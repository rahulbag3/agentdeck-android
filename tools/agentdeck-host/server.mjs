#!/usr/bin/env node
/**
 * AgentDeck host agent.
 *
 * A zero-dependency bridge between the AgentDeck Android app and a real
 * project checkout. It never accepts arbitrary commands from the phone: the
 * agent CLI and checks come from this host's config file.
 *
 *   node server.mjs                     # uses ./agentdeck-host.config.json
 *   node server.mjs --config my.json
 *   node server.mjs --port 8787 --host 127.0.0.1
 *   node server.mjs --print-token
 *   node server.mjs --project C:\\code\\orbit-api
 *
 * Endpoints (all require `Authorization: Bearer <token>`):
 *   GET  /v1/ping        host + project info
 *   POST /v1/run         {taskId, projectId, prompt, branch, turns} -> SSE
 *   POST /v1/permission  {taskId, requestId, decision}
 *   POST /v1/cancel      {taskId}
 */
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const PROTOCOL = 1
const VERSION = '1.0.0'
const HERE = path.dirname(fileURLToPath(import.meta.url))
const MAX_DIFF_CHARS = 200_000
const MAX_MESSAGE_CHARS = 200_000
const MAX_CHECK_OUTPUT = 4_000
const PERMISSION_TIMEOUT_MS = 120_000
const CHECK_TIMEOUT_MS = 180_000

const LANGUAGE_BY_EXT = {
  '.ts': 'TypeScript', '.tsx': 'TypeScript', '.js': 'JavaScript', '.jsx': 'JavaScript',
  '.kt': 'Kotlin', '.java': 'Java', '.py': 'Python', '.go': 'Go', '.rs': 'Rust',
  '.rb': 'Ruby', '.php': 'PHP', '.cs': 'C#', '.swift': 'Swift', '.md': 'Markdown',
  '.json': 'JSON', '.yml': 'YAML', '.yaml': 'YAML', '.css': 'CSS', '.html': 'HTML',
  '.sql': 'SQL', '.sh': 'Shell', '.toml': 'TOML'
}

function parseArgs(argv) {
  const options = { config: null, port: null, host: null, project: null, printToken: false }
  for (let i = 2; i < argv.length; i++) {
    const arg = argv[i]
    if (arg === '--config') options.config = argv[++i]
    else if (arg === '--port') options.port = Number(argv[++i])
    else if (arg === '--host') options.host = argv[++i]
    else if (arg === '--project') options.project = argv[++i]
    else if (arg === '--print-token') options.printToken = true
    else if (arg === '--help' || arg === '-h') {
      console.log(readFileSync(fileURLToPath(import.meta.url), 'utf8').split('*/')[0])
      process.exit(0)
    }
  }
  return options
}

function loadConfig(options) {
  const configPath = path.resolve(options.config ?? path.join(HERE, 'agentdeck-host.config.json'))
  let config = {}
  if (existsSync(configPath)) {
    config = JSON.parse(readFileSync(configPath, 'utf8'))
  } else {
    config = {
      port: 8787,
      bind: '127.0.0.1',
      token: randomBytes(24).toString('hex'),
      name: os.hostname(),
      projects: [],
      agentCommand: [],
      checks: []
    }
    writeFileSync(configPath, JSON.stringify(config, null, 2))
    console.log(`Created ${configPath}`)
  }
  if (options.port) config.port = options.port
  if (options.host) config.bind = options.host
  if (!config.token) {
    config.token = randomBytes(24).toString('hex')
    writeFileSync(configPath, JSON.stringify(config, null, 2))
  }
  if (!Array.isArray(config.projects)) config.projects = []
  if (options.project) {
    const projectPath = path.resolve(options.project)
    config.projects = [{
      id: path.basename(projectPath).toLowerCase().replace(/[^a-z0-9]+/g, '-'),
      name: path.basename(projectPath),
      path: projectPath
    }]
  }
  if (config.projects.length === 0) {
    config.projects = [{
      id: path.basename(process.cwd()).toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'workspace',
      name: path.basename(process.cwd()) || 'Workspace',
      path: process.cwd()
    }]
  }
  if (!config.name) config.name = os.hostname()
  if (!Number.isFinite(config.port)) config.port = 8787
  if (!config.bind) config.bind = '127.0.0.1'
  return { config, configPath }
}

function quoteArg(value) {
  if (process.platform === 'win32') {
    return '"' + String(value).replace(/"/g, '\\"') + '"'
  }
  return "'" + String(value).replace(/'/g, "'\\''") + "'"
}

function buildCommandLine(template, values) {
  return template
    .map(token => token.replace(/\{(prompt|taskId|branch|project)\}/g, (_, key) => values[key] ?? ''))
    .map(quoteArg)
    .join(' ')
}

function spawnCommand(template, values, cwd) {
  const line = buildCommandLine(template, values)
  const child = spawn(line, { cwd, shell: true, windowsHide: true })
  return { child, line }
}

function killTree(child) {
  if (!child || child.killed) return
  if (process.platform === 'win32') {
    spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], { windowsHide: true })
  } else {
    try { process.kill(-child.pid, 'SIGTERM') } catch { child.kill('SIGTERM') }
  }
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = ''
    req.on('data', chunk => {
      body += chunk
      if (body.length > 1_000_000) {
        reject(new Error('Request body too large'))
        req.destroy()
      }
    })
    req.on('end', () => {
      if (!body.trim()) return resolve({})
      try { resolve(JSON.parse(body)) } catch { reject(new Error('Invalid JSON body')) }
    })
    req.on('error', reject)
  })
}

function sendJson(res, status, value) {
  const body = JSON.stringify(value)
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) })
  res.end(body)
}

function git(cwd, args) {
  return new Promise(resolve => {
    const child = spawn('git', args, { cwd, windowsHide: true })
    let out = ''
    let err = ''
    child.stdout.on('data', d => { out += d })
    child.stderr.on('data', d => { err += d })
    child.on('close', code => resolve({ code, out, err }))
    child.on('error', () => resolve({ code: -1, out: '', err: 'git unavailable' }))
  })
}

function parseGitDiff(text) {
  const files = []
  if (!text || !text.trim()) return files
  const blocks = text.split(/^(?=diff --git )/m).filter(Boolean)
  for (const block of blocks) {
    if (files.length >= 8) break
    const plus = block.match(/^\+\+\+ b\/(.+)$/m)
    const minus = block.match(/^--- a\/(.+)$/m)
    const newFile = /^new file mode /m.test(block)
    const deleted = /^deleted file mode /m.test(block)
    const filePath = (plus?.[1] ?? minus?.[1] ?? '').trim()
    if (!filePath) continue
    const changeType = newFile ? 'ADDED' : deleted ? 'DELETED' : 'MODIFIED'
    const ext = path.extname(filePath).toLowerCase()
    files.push({
      path: filePath,
      changeType,
      language: LANGUAGE_BY_EXT[ext] ?? 'Text',
      diff: block.slice(0, MAX_DIFF_CHARS)
    })
  }
  return files
}

async function detectChanges(cwd) {
  const head = await git(cwd, ['diff', 'HEAD', '--no-color', '--no-ext-diff'])
  if (head.code === 0 && head.out.trim()) return parseGitDiff(head.out)
  // No commits yet: show untracked files as additions.
  const status = await git(cwd, ['status', '--porcelain=v1'])
  if (status.code !== 0) return []
  const files = []
  for (const line of status.out.split('\n')) {
    if (!line.trim()) continue
    const code = line.slice(0, 2)
    const filePath = line.slice(3).trim()
    if (code.includes('?')) {
      files.push({ path: filePath, changeType: 'ADDED', language: LANGUAGE_BY_EXT[path.extname(filePath).toLowerCase()] ?? 'Text', diff: '' })
    }
    if (files.length >= 8) break
  }
  return files
}

function createHost(config) {
  const active = new Map()
  const token = config.token

  function authorized(req) {
    const header = req.headers.authorization ?? ''
    return header === `Bearer ${token}`
  }

  function emit(res, event, data) {
    if (res.writableEnded || res.destroyed) return
    res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`)
  }

  function projectFor(projectId) {
    return config.projects.find(p => p.id === projectId) ?? config.projects[0] ?? null
  }

  async function requestPermission(res, taskId, command) {
    const requestId = 'perm-' + randomBytes(6).toString('hex')
    const run = active.get(taskId)
    if (!run) return { requestId, decision: 'deny' }
    const decision = new Promise(resolve => {
      const timer = setTimeout(() => resolve('timeout'), PERMISSION_TIMEOUT_MS)
      run.permission = {
        requestId,
        resolve: value => {
          clearTimeout(timer)
          resolve(value)
        }
      }
    })
    emit(res, 'permission', {
      requestId,
      title: 'Run the agent on this project?',
      body: 'The host will run the configured agent command in the project folder. Output and file changes stream back to this task.',
      command: command.slice(0, 500)
    })
    const result = await decision
    run.permission = null
    return { requestId, decision: result }
  }

  function resolvePermission(taskId, requestId, decision) {
    const run = active.get(taskId)
    if (!run || !run.permission || run.permission.requestId !== requestId) return false
    run.permission.resolve(decision)
    return true
  }

  async function runChecks(res, cwd, taskId, cancelled) {
    const results = []
    for (const check of config.checks ?? []) {
      if (cancelled(taskId)) break
      const template = Array.isArray(check.command) ? check.command : null
      if (!template || template.length === 0) continue
      emit(res, 'status', { text: `Running ${check.name ?? template[0]}` })
      const started = Date.now()
      const line = buildCommandLine(template, { prompt: '', taskId, branch: '', project: '' })
      const child = spawn(line, { cwd, shell: true, windowsHide: true })
      const run = active.get(taskId)
      if (run) run.child = child
      let output = ''
      child.stdout.on('data', d => { output = (output + d).slice(-MAX_CHECK_OUTPUT * 2) })
      child.stderr.on('data', d => { output = (output + d).slice(-MAX_CHECK_OUTPUT * 2) })
      const timeout = setTimeout(() => { killTree(child) }, CHECK_TIMEOUT_MS)
      const exitCode = await new Promise(resolve => {
        child.on('close', resolve)
        child.on('error', () => resolve(-1))
      })
      clearTimeout(timeout)
      const seconds = ((Date.now() - started) / 1000).toFixed(1)
      const status = exitCode === 0 ? 'PASSED' : 'FAILED'
      const summary = exitCode === 0 ? `${seconds}s` : `Exit ${exitCode}`
      emit(res, 'check', {
        kind: check.kind ?? 'check',
        name: check.name ?? template[0],
        status,
        summary,
        command: line,
        duration: `${seconds}s`,
        output: output.slice(-MAX_CHECK_OUTPUT)
      })
      results.push({ status })
    }
    return results
  }

  async function handleRun(req, res) {
    const body = await readJson(req)
    const taskId = String(body.taskId ?? '').trim()
    if (!taskId) return sendJson(res, 400, { error: 'taskId is required' })
    if (active.has(taskId)) return sendJson(res, 409, { error: 'That task is already running' })
    const project = projectFor(body.projectId)
    if (!project) return sendJson(res, 400, { error: 'No project is configured on this host' })
    if (!existsSync(project.path)) {
      return sendJson(res, 400, { error: `Project folder not found: ${project.path}` })
    }

    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no'
    })
    res.write(': connected\n\n')

    const state = { child: null, cancelled: false, permission: null }
    active.set(taskId, state)
    const cancelled = id => active.get(id)?.cancelled === true
    const heartbeat = setInterval(() => {
      if (!res.writableEnded) res.write(': ping\n\n')
    }, 15_000)
    const finish = () => {
      clearInterval(heartbeat)
      active.delete(taskId)
      if (!res.writableEnded) res.end()
    }

    res.on('close', () => {
      state.cancelled = true
      killTree(state.child)
    })

    const prompt = String(body.prompt ?? '')
    const agentTemplate = Array.isArray(config.agentCommand) ? config.agentCommand : []
    let pendingMessages = ''
    let recentOutput = ''
    const flushMessages = () => {
      if (!pendingMessages.trim()) return
      emit(res, 'message', { text: pendingMessages.slice(0, 4000) })
      pendingMessages = ''
    }

    try {
      emit(res, 'plan', {
        steps: [
          { text: 'Prepare the project', state: 'ACTIVE' },
          { text: 'Run the agent', state: 'TODO' },
          { text: 'Collect changes', state: 'TODO' },
          { text: 'Run checks', state: 'TODO' }
        ]
      })
      emit(res, 'status', { text: `Working in ${project.path}` })

      if (agentTemplate.length === 0) {
        emit(res, 'message', {
          text: 'No agentCommand is configured on this host, so no files will be modified. Add an agent CLI to agentdeck-host.config.json to let tasks make changes. Collecting the current repository state and running configured checks instead.'
        })
        emit(res, 'plan', {
          steps: [
            { text: 'Prepare the project', state: 'DONE' },
            { text: 'Run the agent', state: 'FAILED' },
            { text: 'Collect changes', state: 'ACTIVE' },
            { text: 'Run checks', state: 'TODO' }
          ]
        })
      } else {
        const permission = await requestPermission(
          res,
          taskId,
          buildCommandLine(agentTemplate, { prompt: '', taskId, branch: body.branch ?? '', project: project.path })
        )
        if (permission.decision !== 'allow') {
          const reason = permission.decision === 'timeout' ? 'Approval timed out.' : 'You declined the agent run.'
          emit(res, 'error', { message: `${reason} Nothing was changed.` })
          finish()
          return
        }
        if (cancelled(taskId)) { emit(res, 'error', { message: 'Run cancelled.' }); finish(); return }

        emit(res, 'plan', {
          steps: [
            { text: 'Prepare the project', state: 'DONE' },
            { text: 'Run the agent', state: 'ACTIVE' },
            { text: 'Collect changes', state: 'TODO' },
            { text: 'Run checks', state: 'TODO' }
          ]
        })
        const { child, line } = spawnCommand(agentTemplate, {
          prompt,
          taskId,
          branch: body.branch ?? '',
          project: project.path
        }, project.path)
        state.child = child
        emit(res, 'status', { text: `Agent started · ${line.slice(0, 160)}` })

        const onData = data => {
          const text = data.toString()
          pendingMessages += text
          recentOutput = (recentOutput + text).slice(-1200)
          if (pendingMessages.length > 4000) flushMessages()
        }
        child.stdout.on('data', onData)
        child.stderr.on('data', onData)
        const exitCode = await new Promise(resolve => {
          child.on('close', resolve)
          child.on('error', () => resolve(-1))
        })
        flushMessages()
        state.child = null
        if (cancelled(taskId)) { emit(res, 'error', { message: 'Run cancelled.' }); finish(); return }
        if (exitCode !== 0) {
          emit(res, 'error', {
            message: `The agent exited with code ${exitCode}.` +
              (recentOutput.trim() ? `\n${recentOutput.trim().slice(-800)}` : '')
          })
          finish()
          return
        }
      }

      emit(res, 'plan', {
        steps: [
          { text: 'Prepare the project', state: 'DONE' },
          { text: 'Run the agent', state: 'DONE' },
          { text: 'Collect changes', state: 'ACTIVE' },
          { text: 'Run checks', state: 'TODO' }
        ]
      })
      const files = await detectChanges(project.path)
      for (const file of files) emit(res, 'file', file)
      emit(res, 'status', {
        text: files.length === 0
          ? 'No file changes detected.'
          : `Collected ${files.length} changed file${files.length === 1 ? '' : 's'}.`
      })

      emit(res, 'plan', {
        steps: [
          { text: 'Prepare the project', state: 'DONE' },
          { text: 'Run the agent', state: 'DONE' },
          { text: 'Collect changes', state: 'DONE' },
          { text: 'Run checks', state: 'ACTIVE' }
        ]
      })
      const checkResults = await runChecks(res, project.path, taskId, cancelled)
      if (cancelled(taskId)) { emit(res, 'error', { message: 'Run cancelled.' }); finish(); return }

      const passed = checkResults.filter(c => c.status === 'PASSED').length
      emit(res, 'done', {
        status: 'review',
        summary: `${files.length} file${files.length === 1 ? '' : 's'} changed · ${passed}/${checkResults.length} checks passed`
      })
    } catch (error) {
      emit(res, 'error', { message: error?.message ?? 'The host run failed.' })
    } finally {
      finish()
    }
  }

  const server = http.createServer(async (req, res) => {
    try {
      if (!authorized(req)) return sendJson(res, 401, { error: 'Invalid or missing bearer token' })
      const url = new URL(req.url, 'http://localhost')
      if (req.method === 'GET' && url.pathname === '/v1/ping') {
        return sendJson(res, 200, {
          protocol: PROTOCOL,
          name: config.name,
          platform: `${os.platform()} ${os.release()}`,
          version: VERSION,
          projects: config.projects.map(p => ({ id: p.id, name: p.name, path: p.path }))
        })
      }
      if (req.method === 'POST' && url.pathname === '/v1/run') return handleRun(req, res)
      if (req.method === 'POST' && url.pathname === '/v1/permission') {
        const body = await readJson(req)
        const ok = resolvePermission(String(body.taskId ?? ''), String(body.requestId ?? ''), body.decision === 'allow' ? 'allow' : 'deny')
        if (!ok) return sendJson(res, 404, { error: 'No matching permission request' })
        res.writeHead(204)
        return res.end()
      }
      if (req.method === 'POST' && url.pathname === '/v1/cancel') {
        const body = await readJson(req)
        const run = active.get(String(body.taskId ?? ''))
        if (run) {
          run.cancelled = true
          run.permission?.resolve('deny')
          killTree(run.child)
        }
        res.writeHead(204)
        return res.end()
      }
      sendJson(res, 404, { error: 'Not found' })
    } catch (error) {
      if (!res.headersSent) sendJson(res, 500, { error: error?.message ?? 'Host error' })
      else res.end()
    }
  })
  server.on('clientError', (err, socket) => socket.destroy())
  return server
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
if (isMain) {
  const options = parseArgs(process.argv)
  const { config, configPath } = loadConfig(options)
  if (options.printToken) {
    console.log(config.token)
    process.exit(0)
  }
  const server = createHost(config)
  server.listen(config.port, config.bind, () => {
    console.log(`AgentDeck host ${VERSION} listening on http://${config.bind}:${config.port}`)
    console.log(`Config: ${configPath}`)
    console.log(`Projects: ${config.projects.map(p => `${p.name} (${p.path})`).join(', ')}`)
    console.log(`Agent command: ${config.agentCommand?.length ? config.agentCommand.join(' ') : '(none — inspection + checks only)'}`)
    console.log(`Token: ${config.token}`)
    console.log('Paste the URL and token into AgentDeck → Settings → Execution host.')
  })
}

export { createHost, buildCommandLine, parseGitDiff }

