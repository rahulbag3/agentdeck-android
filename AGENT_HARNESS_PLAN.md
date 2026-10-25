# AgentDeck → self-contained agent harness: planning input

**Status: research done, no implementation started.** This file captures the
reference-project findings and the current-architecture map so a planning
session can design the rework without re-doing the research. Written
2026-10-25. The working tree contains only completed, tested, uncommitted
work from earlier sessions (see HANDOVER.md "Cloud profiles + local model
lifecycle" and later fix notes; build green, 84 unit tests pass).

## The assignment (from the user)

Rework AgentDeck into a fully independent AI agent harness with no host
computer / host-side service required for core functionality: plan tasks,
understand whole codebases, read and modify files, execute commands where the
platform permits, run tests, use tools, manage Git changes, inspect diffs,
recover from errors, and a complete approval/review workflow. Cloud models via
BYOK and genuine on-device inference stay. Preserve all existing features,
project data, and security protections. No mandatory gateway, no fake
execution, no simulated results, stay native Android (no WebView/hybrid).

## Reference findings (researched 2026-10-25)

- **PandaCode** (desktop orchestrator, Tauri/Rust): runs existing coding-agent
  CLIs side by side behind one normalized event interface. Task board with
  Pending→In progress→Review→Completed; per-task git worktrees; approval bars
  with approve-all; steering mid-run; local keys in OS secrets. Takeaway for
  us: task-board + worktree + approval UX patterns; it is NOT self-contained
  (delegates to CLIs we cannot run on Android).
- **ZCode** (GLM harness): goals broken into plan steps with progress; writes
  files directly, per-file diffs (+/−), commit and undo; runs verification
  commands after writes; "ask before changes" approval mode. Takeaway:
  plan-step UX, write-then-verify loop, undo = git.
- **OpenCode** (terminal/desktop agent, 75+ providers incl. local): landing
  page documents little; known model: client/server agent with tool calls
  (edit/write/read/bash/grep/glob/plan), permissions modes (ask/allow
  per-tool), sessions. Takeaway: tool set + permission modes are the
  reference shape.
- **Hermes Agent** (NousResearch): learning loop, skills, 40+ tools, MCP,
  six terminal backends (local/Docker/SSH/Modal/Daytona/Vercel), command
  allowlists, DM pairing. Takeaway: backend-abstracted terminal + allowlist
  concept; most backends are desktop/cloud-only.
- **OpenClaw** (local gateway + channels): "trusted gateway, untrusted
  execution, policy enforced by code"; tools run on host unless sandboxed;
  pairing approvals. Takeaway: explicit trust model wording.

Common denominator worth copying: **a plan, a tool loop (read→edit→run→verify),
per-action approvals, git as the undo/record layer, and honest sandboxing.**

## Android platform constraints (the physics the plan must respect)

- Apps CAN read/write their own private storage as real `java.io.File` paths.
- Apps CANNOT exec binaries stored in writable app data on targetSdk ≥ 29
  (W^X). They CAN `Runtime.exec("/system/bin/sh")` — toybox applets (ls, cat,
  grep, sed, awk, find, cp, mv, rm, mkdir, tar, unzip, wc, sort, head, tail…)
  are real. No node/python/mvn/gradle toolchains exist on stock Android — say
  so honestly; "run tests" = shell-runnable scripts + GitHub Actions CI via
  the existing PR flow + optional host.
- No git binary. Real git on-device needs JGit (pure-Java; 5.13.x line is
  Android-friendly, used by MGit). JGit needs File paths → the workspace must
  live in app-private storage, not SAF trees. Import/export to user folders
  or GitHub via SAF + tarball/zip.
- GitHub integration already real: read snapshots, apply branch+PR+CI checks.
  Repo import can be extended: download codeload tarball → unzip into the
  workspace (real full-codebase understanding without git clone).
- On-device inference is real (llama.cpp, NPU-verified). 4B-class models are
  weak tool-users; the agent loop should target cloud BYOK first, local later.
- Termux pattern legitimizes app-internal workspace as "the" home for projects.

## Current architecture map (what exists, all working)

- `AgentDeckState.kt` (~3.1k lines): central Compose state; task lifecycle
  (PENDING→IN_PROGRESS→REVIEW…), cloud SSE streaming, GitHub apply flow,
  optional host bridge, local llama.cpp generation, profiles, persistence.
- Cloud: `CloudClient` (SSE), profiles (multi-API, /models fetch, saved ids).
- GitHub: `GitHubClient` (read context + write: branch/commit/PR/check-runs).
- Local: `LocalEngine` JNI → llama.cpp (UTF-16-safe streaming, KV cleared per
  generation, 4K ctx, NPU/GPU/CPU).
- Host bridge (optional): `tools/agentdeck-host` node agent + `HostClient`.
- UI: Home/Tasks/Projects/Settings + Task Chat/Changes/Checks tabs; sheets;
  `PermissionRequest` approval UI exists for host runs (reusable for tools).
- Persistence: Room (tasks payload JSON, additive-tolerant), Keystore secrets.
- Proposal pipeline: `agentdeck-file` blocks parsed from cloud replies →
  apply → PR → CI. This is effectively a 1-tool agent loop already; the
  generalization path is clear.

## Candidate architecture (for the planner to refine)

1. **Workspace store**: `files/workspace/<project>/` (File-based). Import via
   GitHub tarball or SAF (folder/zip copy-in); export back via SAF or the
   existing GitHub apply. Path-confinement guard on every file op.
2. **Tool system** (`AgentTools`): text protocol the model emits in fenced
   blocks (same pattern as agentdeck-file; works with any chat model, no
   native tool-calling needed): `list`, `read`, `write`, `run` (sh, approved),
   `grep/search`, `git_diff`, `git_commit`, `git_log`, `plan`. Reads
   auto-execute; writes/commands raise the existing approval sheet. Bounded
   sizes/step budget; errors fed back to the model for retry (recovery).
3. **Git via JGit**: init/status/add/commit/diff/log inside the workspace;
   real .git; diffs feed the existing Changes tab + approve/request-changes.
   Push deferred (GitHub apply already covers publishing).
4. **Agent loop**: cloud-model-driven first (task with `agentMode`), local
   models later. Reuses streaming; tool-call cards in the chat transcript;
   activity trail; final diff → REVIEW status → existing review workflow.
5. **Honesty layer**: command allowlist (toybox set), explicit "no toolchain
   on device — CI runs your tests" messaging, no fake results.

## Invariants to preserve (from HANDOVER §4)

Writes are user-triggered/pull-request-shaped on GitHub; no gateway for BYOK;
secrets in Keystore only; repo content is untrusted data in prompts; no
WebView; host bridge stays optional; nothing simulated.

## Suggested phasing

A. Workspace + import + file browser (SAF/GitHub tarball) — no model changes.
B. Tool protocol + cloud agent loop + approvals + diff-in-Changes-tab.
C. JGit integration (status/diff/commit/log/undo).
D. Shell executor with allowlist + timeouts + output capture.
E. Local-model agent mode; memory/plan persistence; polish.
