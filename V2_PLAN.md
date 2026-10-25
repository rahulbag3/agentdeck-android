# v2 Plan — new agent app (working name: **Kestrel**)

> Status: plan agreed with user 2026-10-25. v1 (AgentDeck) stays installed on
> the phone as the reference/backup; its GitHub backup is tagged
> `v1.0-baseline`. The new app is a clean rebuild — v1 features are ported
> deliberately, not carried over by inertia.

## 1. Identity & repo

- **Working name:** Kestrel (trivially renameable before release).
- **Package:** `dev.kestrel.agent` (new ID — installs side-by-side with v1).
- **Repo layout:** same GitHub repo, new branch `v2`, new project folder
  `/kestrel/` (Gradle project) next to `/android/` (v1, untouched).
- **minSdk 28** (Android/media shared workspace + modern APIs), targetSdk 36,
  single-Activity Compose app. Material 3, dynamic color where available.

## 2. What v1 teaches v2 (feature map — "remember this app")

| Carry over from v1 (module, unchanged or lightly adapted) | Becomes in v2 |
|---|---|
| `CloudClient` (BYOK SSE, retries) + `CloudProfiles` (multi-API, /models fetch) | `data/ModelRepository` — model picker with saved models per API |
| `LocalEngine` JNI + llama.cpp **b11876** natives (standard + Snapdragon) | `data/LocalBackend` — on-device streaming chat |
| `WebTools` (DDG search, page fetch→text, HTTP API calls) | Chat tools: `web_search`, `web_fetch`, `http` |
| `MemoryStore`, `InstructionStore` | Agent context: memory tools + always-injected snapshot; instruction library |
| `Workspace` (files, path confinement, GitHub tarball import) | Coding mode file engine |
| `WorkspaceGit` (JGit status/commit/diff/log) | Coding mode git + diff review |
| `ShellRunner` (toybox allowlist) + `SshRunner` (Termux bridge) | `run` tool — in-app shell first, Termux for real toolchains |
| `ToolParser`/`ToolExecutor` (agentdeck-tool fences) | `domain/ToolRegistry` — same wire protocol, pluggable set |
| `UnifiedDiffFiles` + `HostDiff` | Diff renderer for the Changes view |
| `SecureStore` (Keystore AES-GCM) | Secrets: API keys, SSH password |
| Dropped: PC host bridge (port later if wanted), Room v1 JSON codec, seed/legacy UI, WebView-era remnants | — |

## 3. Architecture

```
kestrel/
├── app/                      # Compose UI (screens, viewmodels, nav)
├── core/agent/               # AgentEngine: loop, backends, approvals, ToolRegistry
├── core/models/              # CloudClient + profiles, LocalBackend (v1 ports)
├── core/tools/               # WebTools, MemoryStore, InstructionStore (v1 ports)
├── core/workspace/           # Workspace, WorkspaceGit, ShellRunner, SshRunner
└── core/data/                # Room (new schema), SecureStore, settings
```

- **MVVM + StateFlow.** One `ChatViewModel` per screen; no god-object.
  Manual DI (`AppContainer`) — no Hilt, keeps annotation processing to Room/KSP only.
- **Navigation-Compose** with typed routes (home, chat/{id}, coding/{id},
  settings) — replaces v1's hand-rolled back stack.
- **AgentEngine** (single actor per session, channel-fed):
  message → backend stream → parse tool calls → execute → feed results →
  repeat (step budget) → finalize. Backends: `CloudBackend`, `LocalBackend`
  behind one interface — switching models mid-session stays supported.
- **ToolRegistry:** each tool = (name, doc, approval policy, executor).
  Adding a tool = one object + one prompt line. Approval policies:
  `auto` / `ask` (bottom sheet: Allow once · Allow all session · Deny) /
  `deny`. Defaults: reads+web+memory auto; write/run/git-commit/http ask.
- **Room, real tables** (no JSON blobs):
  `sessions(id, kind CHAT|CODING, title, model_id, workspace_id, created, updated,
  status)` · `messages(id, session_id, role, text, model, provider, created,
  meta_json)` · `tool_events(id, session_id, tool, args_json, output, ok,
  created)` · `approvals(id, session_id, tool, args_json, decision, created)`.
  Schema v1 exported; migrations required from day one.

## 4. UX blueprint

- **Home:** two oversized cards — **Chat** / **Coding** — plus recent
  sessions and a one-line status (model · device shell · memory count). No
  projects/tasks hierarchy anywhere.
- **Chat:** full-bleed conversation; composer with model chip + instruction
  chip; tool activity renders as compact inline cards (search results,
  fetched page previews, memory writes); current date/time injected into
  every prompt (v1 lesson).
- **Coding:** workspace header (repo · branch · import · file browser),
  conversation, **Changes** (real JGit diff, approve/request changes),
  **Runs** (command history from Termux + in-app shell). Step indicator
  "Step 2/8" always visible during runs.
- **Settings:** Models (profiles, fetch, defaults), Instructions, Memory
  (view/clear), Device shell (Termux setup checklist + Test), About.
- **Honesty invariants carried over:** no simulated results; platform limits
  stated in prompts; every consequential action visible and approved; secrets
  only in Keystore; local models stream on-device or say nothing.

## 5. Phases (each ends with something usable)

- **P0 — Skeleton + Chat talks.** Project scaffold, nav, AppContainer, port
  `CloudClient`+profiles+SecureStore, chat screen streaming from cloud,
  Settings→Models. *Milestone: send a message, get a streamed reply on-device.*
- **P1 — Agent loop.** AgentEngine + ToolRegistry (web/memory/http) +
  Room sessions + approvals sheet + instructions. *Milestone: "what time is
  it" answers from `date`; "search X" returns real results.*
- **P2 — Coding mode.** Workspace + import + browser, git diff in Changes,
  coding tool set, JGit commits. *Milestone: agent edits a file, diff shows,
  user approves.*
- **P3 — Real execution.** ShellRunner (toybox) + Termux bridge + local
  models (llama b11876 natives) as chat/coding backend.
  *Milestone: `node -v` runs through an approved tool call; local model chats.*
- **P4 — Polish + release.** Empty/error states, dynamic color, widget?,
  95-test-equivalent coverage for ports, R8 release, signed APKs, README.

## 6. Risks & mitigations

- **Free-model 502s (OpenRouter)** → multi-model fallback: engine retries
  once on the next saved model of the same profile when a provider 5xxs.
- **Local models are weak tool-users** → cloud is the default loop backend;
  local gets a simplified tool prompt and honest "this model struggles with
  tools" note if two consecutive steps emit no parseable tools.
- **Termux fragility** → Device shell card ships a live checklist (sshd
  running? port? user? mirror reachable?) — lessons from the v1 session
  (username `u0_aXXX`, Cloudflare mirror `packages.termux.dev`).
- **Rewrite scope creep** → phases are shippable; v1 remains the working app.

## 7. Definition of done for v2 MVP

Chat and Coding both: stream from cloud or device, call real tools with
approvals, persist sessions, recover from process death, and run real
toolchains through Termux — with zero "simulated" code paths anywhere.
