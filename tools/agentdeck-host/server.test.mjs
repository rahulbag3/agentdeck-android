import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { buildCommandLine, createHost, parseGitDiff } from './server.mjs'

const TOKEN = 'test-token'

function makeRepo() {
  const dir = mkdtempSync(path.join(tmpdir(), 'agentdeck-host-'))
  execFileSync('git', ['init', '-q'], { cwd: dir })
  execFileSync('git', ['config', 'user.email', 'test@example.com'], { cwd: dir })
  execFileSync('git', ['config', 'user.name', 'Test'], { cwd: dir })
  writeFileSync(path.join(dir, 'app.txt'), 'hello\n')
  execFileSync('git', ['add', '.'], { cwd: dir })
  execFileSync('git', ['commit', '-qm', 'init'], { cwd: dir })
  return dir
}

function makeConfig(repo, overrides = {}) {
  return {
    port: 0,
    bind: '127.0.0.1',
    token: TOKEN,
    name: 'Test Host',
    projects: [{ id: 'orbit-api', name: 'orbit-api', path: repo }],
    agentCommand: ['node', '-e', "require('fs').appendFileSync('app.txt','world')"],
    checks: [{ kind: 'test', name: 'Unit tests', command: ['node', '-e', 'process.exit(0)'] }],
    ...overrides
  }
}

async function startServer(t, config) {
  const server = createHost(config)
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => server.close())
  return `http://127.0.0.1:${server.address().port}`
}

function parseChunk(chunk) {
  let event = 'message'
  let data = ''
  for (const line of chunk.split('\n')) {
    if (line.startsWith('event: ')) event = line.slice(7).trim()
    else if (line.startsWith('data: ')) data += line.slice(6)
  }
  if (!data) return null
  return { event, data: JSON.parse(data) }
}

async function runTask(base, body, onEvent) {
  const response = await fetch(`${base}/v1/run`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  assert.equal(response.status, 200)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const events = []
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let index
    while ((index = buffer.indexOf('\n\n')) >= 0) {
      const parsed = parseChunk(buffer.slice(0, index))
      buffer = buffer.slice(index + 2)
      if (!parsed) continue
      events.push(parsed)
      await onEvent?.(parsed)
    }
  }
  return events
}

test('ping requires the bearer token', async t => {
  const base = await startServer(t, makeConfig(makeRepo()))
  const unauthorized = await fetch(`${base}/v1/ping`)
  assert.equal(unauthorized.status, 401)

  const response = await fetch(`${base}/v1/ping`, { headers: { Authorization: `Bearer ${TOKEN}` } })
  assert.equal(response.status, 200)
  const info = await response.json()
  assert.equal(info.protocol, 1)
  assert.equal(info.name, 'Test Host')
  assert.equal(info.projects[0].id, 'orbit-api')
})

test('approved run streams permission, message, file, check, and done', async t => {
  const repo = makeRepo()
  const base = await startServer(t, makeConfig(repo))

  const events = await runTask(base, {
    taskId: 't-allow',
    projectId: 'orbit-api',
    prompt: 'Add a greeting',
    branch: 'feat/greeting'
  }, async event => {
    if (event.event === 'permission') {
      const response = await fetch(`${base}/v1/permission`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ taskId: 't-allow', requestId: event.data.requestId, decision: 'allow' })
      })
      assert.equal(response.status, 204)
    }
  })

  const names = events.map(e => e.event)
  assert.ok(names.includes('permission'))
  assert.ok(names.includes('file'))
  assert.ok(names.includes('check'))
  const done = events.find(e => e.event === 'done')
  assert.equal(done.data.status, 'review')

  const file = events.find(e => e.event === 'file').data
  assert.equal(file.path, 'app.txt')
  assert.equal(file.changeType, 'MODIFIED')
  assert.match(file.diff, /\+world/)

  const check = events.find(e => e.event === 'check').data
  assert.equal(check.status, 'PASSED')
})

test('denied permission ends the run without changes', async t => {
  const repo = makeRepo()
  const base = await startServer(t, makeConfig(repo))

  const events = await runTask(base, {
    taskId: 't-deny',
    projectId: 'orbit-api',
    prompt: 'Do not run',
    branch: ''
  }, async event => {
    if (event.event === 'permission') {
      await fetch(`${base}/v1/permission`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ taskId: 't-deny', requestId: event.data.requestId, decision: 'deny' })
      })
    }
  })

  assert.ok(events.some(e => e.event === 'error'))
  assert.ok(!events.some(e => e.event === 'file'))
  assert.ok(!events.some(e => e.event === 'check'))
})

test('host without an agent command inspects and runs checks', async t => {
  const repo = makeRepo()
  const base = await startServer(t, makeConfig(repo, { agentCommand: [] }))

  const events = await runTask(base, {
    taskId: 't-inspect',
    projectId: 'orbit-api',
    prompt: 'Check the repo',
    branch: ''
  })

  assert.ok(events.some(e => e.event === 'message'))
  assert.ok(events.some(e => e.event === 'check'))
  assert.equal(events.find(e => e.event === 'done').data.status, 'review')
  assert.ok(!events.some(e => e.event === 'permission'))
})

test('run rejects unknown projects when paths are missing', async t => {
  const base = await startServer(t, makeConfig(path.join(tmpdir(), 'missing-agentdeck-project')))
  const response = await fetch(`${base}/v1/run`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ taskId: 't-missing', projectId: 'orbit-api', prompt: 'x' })
  })
  assert.equal(response.status, 400)
})

test('cancel stops a running agent', async t => {
  const repo = makeRepo()
  const base = await startServer(t, makeConfig(repo, {
    agentCommand: ['node', '-e', 'setTimeout(() => {}, 15000)']
  }))

  let cancelled = false
  const events = await runTask(base, {
    taskId: 't-cancel',
    projectId: 'orbit-api',
    prompt: 'Long run',
    branch: ''
  }, async event => {
    if (event.event === 'permission') {
      await fetch(`${base}/v1/permission`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ taskId: 't-cancel', requestId: event.data.requestId, decision: 'allow' })
      })
    }
    if (!cancelled && event.event === 'status' && /Agent started/.test(event.data.text ?? '')) {
      cancelled = true
      const response = await fetch(`${base}/v1/cancel`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ taskId: 't-cancel' })
      })
      assert.equal(response.status, 204)
    }
  })

  assert.ok(events.some(e => e.event === 'error' && /cancel/i.test(e.data.message ?? '')))
  assert.ok(!events.some(e => e.event === 'done'))
})

test('parseGitDiff extracts path, change type, and language', () => {
  const diff = [
    'diff --git a/src/app.ts b/src/app.ts',
    'index 111..222 100644',
    '--- a/src/app.ts',
    '+++ b/src/app.ts',
    '@@ -1,1 +1,2 @@',
    ' const x = 1',
    '+const y = 2'
  ].join('\n')

  const files = parseGitDiff(diff)
  assert.equal(files.length, 1)
  assert.equal(files[0].path, 'src/app.ts')
  assert.equal(files[0].changeType, 'MODIFIED')
  assert.equal(files[0].language, 'TypeScript')
})

test('parseGitDiff marks new files as added', () => {
  const diff = [
    'diff --git a/docs/guide.md b/docs/guide.md',
    'new file mode 100644',
    '--- /dev/null',
    '+++ b/docs/guide.md',
    '@@ -0,0 +1,1 @@',
    '+# Guide'
  ].join('\n')

  const files = parseGitDiff(diff)
  assert.equal(files[0].changeType, 'ADDED')
  assert.equal(files[0].path, 'docs/guide.md')
})

test('buildCommandLine substitutes placeholders', () => {
  const line = buildCommandLine(
    ['agent', '--message', '{prompt}', '--task', '{taskId}'],
    { prompt: 'Fix the bug', taskId: 't-1' }
  )
  assert.match(line, /Fix the bug/)
  assert.match(line, /t-1/)
  assert.match(line, /agent/)
})
