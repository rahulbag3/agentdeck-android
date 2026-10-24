# AgentDeck host agent

The host agent connects the AgentDeck Android app to a real project checkout on
your computer. The phone sends a task; the host runs **your configured agent
CLI** in the project folder, streams its output back, collects the resulting
`git diff`, runs your configured checks, and marks the task ready for review.

The host never accepts arbitrary commands from the phone. The agent command and
checks come from the host's own config file, and every run asks for approval in
the app before it starts.

## Requirements

- Node.js 18 or newer (no npm packages)
- `git` on `PATH`
- An agent CLI you already use, for example Aider, Codex CLI, or Claude Code

## Quick start

```bash
cd tools/agentdeck-host
node server.mjs --project "C:/work/orbit-api"
```

On first run the agent creates `agentdeck-host.config.json`, generates a token,
and prints it. It also creates a project entry for `--project` (or the current
directory). Edit the config to add your agent command and checks:

```json
{
  "port": 8787,
  "bind": "127.0.0.1",
  "name": "Studio Mac",
  "projects": [
    { "id": "orbit-api", "name": "orbit-api", "path": "C:/work/orbit-api" }
  ],
  "agentCommand": ["aider", "--message", "{prompt}", "--yes"],
  "checks": [
    { "kind": "test", "name": "Unit tests", "command": ["pnpm", "test"] },
    { "kind": "lint", "name": "Lint", "command": ["pnpm", "lint"] }
  ]
}
```

Restart the host after editing the config.

`agentCommand` and `check.command` are **argument arrays**, not shell strings.
Placeholders inside any argument: `{prompt}`, `{taskId}`, `{branch}`, `{project}`.
Use `{prompt}` where your CLI expects the message. If your CLI is more reliable
with a file, point it at a path and write the prompt there yourself, or use a
small wrapper script and reference it in `agentCommand`.

With no `agentCommand`, the host still collects the current diff and runs
configured checks — useful for read-only inspection tasks.

## Connect from the phone

The default bind is `127.0.0.1`, which only accepts connections from the same
machine. For a phone on the same Wi-Fi:

```bash
node server.mjs --host 0.0.0.0
```

Then in AgentDeck: **Settings → Execution host → Set up host**, enter:

- URL: `http://<your-computer-LAN-IP>:8787`
- Token: the value printed by the host

LAN traffic is unencrypted unless you put the host behind an HTTPS tunnel
(Tailscale, Caddy, Cloudflare Tunnel). AgentDeck enables "Allow unencrypted LAN"
for this; leave it off when using an HTTPS tunnel. The token is still required
for every request, but HTTP on an untrusted network can be observed.

If the phone cannot reach the computer, check the OS firewall for port 8787 and
confirm both devices are on the same network.

## What a run does

1. Streams a four-step plan and waits for approval in the app.
2. Runs `agentCommand` in the project folder with the task prompt.
3. Streams stdout/stderr back as agent messages.
4. Collects `git diff HEAD` (up to 8 files, 200k characters) and sends each file
   diff to the app.
5. Runs checks sequentially (`PASSED` on exit 0, `FAILED` otherwise, 180s each).
6. Finishes with `review`.

Cancel from the app kills the agent process tree and stops the run.

## Protocol

All endpoints require `Authorization: Bearer <token>`.

| Method | Path            | Purpose |
|--------|-----------------|---------|
| GET    | `/v1/ping`      | Host name, platform, protocol version, projects |
| POST   | `/v1/run`       | Task body; responds with an SSE event stream |
| POST   | `/v1/permission`| `{taskId, requestId, decision: allow\|deny}` |
| POST   | `/v1/cancel`    | `{taskId}` — kill the run |

SSE events: `plan`, `status`, `message`, `file`, `check`, `permission`, `done`,
`error`, plus `: ping` heartbeat comments.

`POST /v1/run` body:

```json
{
  "taskId": "t-abc123",
  "projectId": "orbit-api",
  "prompt": "Add cursor pagination",
  "branch": "feat/cursor-pagination",
  "turns": [{ "role": "user", "content": "Add cursor pagination" }]
}
```

## Security

- Every request needs the token; rotate it by editing `token` and restarting.
- Project paths come from the host config and are never supplied by the app.
- The agent command and checks are fixed by the host operator.
- Runs require explicit approval in the app (120s timeout = decline).
- Prefer `bind: 127.0.0.1` plus an HTTPS tunnel for anything beyond a trusted LAN.
- Do not commit `agentdeck-host.config.json`: it contains the token.
