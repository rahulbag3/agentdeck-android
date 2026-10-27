# AgentDeck — engineering handover

**Date:** 2026-10-25 (updated: on-device agent harness)  
**Workspace:** `C:\Users\Rahul\Downloads\qoder\AgentDeck2.0`  
**Primary target:** Android-only native Kotlin/Jetpack Compose rebuild  
**Current build:** `android/app/build/outputs/apk/release/app-release.apk` (signed, R8, v1.0.0)  
**Debug build:** `android/app/build/outputs/apk/debug/app-debug.apk`  
**GitHub:** https://github.com/rahulbag3/agentdeck-android

> This file is for an engineering agent continuing the work. It records the current state, product boundaries, verification evidence, environment quirks, and recommended next steps.

---

## 1. Project context

AgentDeck is a mobile control center for coding agents: create tasks, chat with an agent, inspect changes, review checks, and approve or request changes. The product brief is in [AGENTDECK_MVP.md](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/AGENTDECK_MVP.md); visual prototypes are in [agentdeck_mobile_bundle](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/agentdeck_mobile_bundle).

There are two implementations in this workspace:

1. **Web prototype:** [app](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/app)
   - Dependency-free HTML/CSS/JS.
   - Responsive at 320/375/430 dp.
   - Uses localStorage key `agentdeck.demo.v4`.
   - Useful as a UX reference, not the runtime target.
2. **Native Android app:** [android](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android)
   - Kotlin/Jetpack Compose.
   - The active implementation.
   - Single Activity, custom state navigation, no WebView.

The user explicitly rejected:

- A WebView wrapper around the web prototype.
- A native rebuild being interpreted as a hybrid.
- Host-side local models.
- A gateway for personal/shared BYOK use.

The user explicitly deferred:

- Real on-device execution.
- A full local executor.

The user explicitly kept in scope:

- Native cloud BYOK.
- Cloud analysis of GitHub repositories.
- Planning/review/reporting from cloud models.
- File changes and checks remaining simulated and host-gated.

---

## 2. Current native status

### Completed and verified

- Full screen port:
  - Home
  - Tasks
  - Projects
  - Settings
  - New Task
  - Task Chat / Changes / Checks
  - Diff viewer
  - Sheets for projects, models, approvals, requests, confirmations, about info
- Local-model UI states and real on-device replies through llama.cpp.
- Host connection/offline states. Execution runs only when the bundled host
  agent is connected and the run is approved; without a host, host tasks fail
  with an actionable error.
- Cloud BYOK:
  - Settings card and setup sheet.
  - OpenAI-compatible `/chat/completions` client.
  - SSE streaming.
  - Non-streaming fallback.
  - Connection test.
  - Keystore-encrypted API key.
- GitHub read-only context:
  - Settings card and setup sheet.
  - Public repositories without a token.
  - Optional encrypted fine-grained token.
  - Repository info, recursive tree, README/manifest/source selection.
  - Task context card and inspectable context sheet.
  - Refresh context action.
- Cloud analysis tasks:
  - Work while Studio Mac is offline.
  - Read-only semantics.
  - Repository snapshot attached to the task.
  - Changes tab says no files were written.
  - Checks tab says no checks ran.
  - Cloud failures appear as system notices without losing the conversation.
- Mid-conversation model switching:
  - `Change` control in the task composer for cloud and local conversations.
  - Task-specific cloud model override.
  - Switching between ready local models.
  - Host execution models are not selectable mid-conversation.
  - Switching is blocked while a reply or repository-context request is active.
  - Each agent reply retains the model and provider that produced it.
- ViewModel survives rotation.
- Room-backed workspace persistence:
  - Tasks, ordered messages, activity, changed files/diffs, checks, decisions, plans, permission/failure state, repository snapshots, and per-task cloud model overrides.
  - Workspace settings (active project, default project, default model).
  - A hydration gate prevents placeholder data from flashing before saved tasks load.
  - Interrupted live runs are reconciled on restore instead of staying stuck in progress.
- In-flight cloud control:
  - The composer swaps Send for Stop while a cloud reply streams.
  - Stopping keeps the partial reply, returns the task to `Needs input`, and records the action.
  - A stopped run is not treated as an interruption after restart.
  - A failed cloud request shows a `Retry` action in the composer; it reuses the existing conversation without adding a duplicate user message.
  - Retry omits trailing partial assistant messages from the provider request, so the request ends on the last user turn. The partial reply stays visible in the transcript.
- Markdown rendering for cloud agent replies:
  - Paragraphs, headings, bullet/numbered lists, quotes, rules, and fenced code.
  - Inline code, bold, italic, and safe `http(s)` links.
  - Unknown syntax falls back to plain text; no raw HTML or images.
- Host disconnect handling:
  - Disconnecting while a host run is active stops the run and marks the task `Failed` with a retryable `Host disconnected` failure.
  - Reconnecting leaves tasks stopped until the user retries; a retry clears the old failure.
- Cloud and GitHub configuration survives app restart.
- Unit tests for persistence, Markdown parsing, cloud URL validation, provider-turn building, error classification, and backup format (45 tests).
- Debug APK builds successfully.

### Still simulated

Nothing. As of the demo removal (2026-10-24, below) every reply, diff, check,
and approval in the app comes from a real source: the configured cloud
endpoint, the bundled llama.cpp runtime, or the connected host agent. The web
prototype in `app/` remains a non-runtime UX reference.

Known gap: local models still do not read the GitHub snapshot (cloud-only).

### Still missing

- Multiple saved cloud endpoints/models.
- In-app update mechanism and Play Store listing.
- Automatic offline send queue (cloud sends fail fast with a retry path instead).
- Integration/E2E tests on CI; unit coverage now includes error mapping, host
  diff parsing, and codec retry persistence.

### Production hardening (2026-10-24)

**Release distribution**
- Signed release builds. Signing material lives in `android/keystore.properties`
  and `android/app/agentdeck-release.jks`; both are gitignored and must never be
  committed. The keystore password is stored only in `keystore.properties`.
- Release builds enable R8 minification and resource shrinking (APK ≈ 2.1 MB).
  `proguard-rules.pro` keeps the Room entry point and source line numbers.
- `versionName` is now `1.0.0`.
- Play Store AAB: `./gradlew :app:bundleRelease`.
- Signing is opt-in: without `keystore.properties`, release builds are unsigned
  but still minified.

**Crash reporting**
- Firebase Crashlytics + Analytics wired behind an opt-in check for
  `google-services.json`; debug builds and fresh clones work without Firebase.
- `data/Logger.kt` writes to logcat and forwards warnings/errors to Crashlytics
  with custom context keys. Every Crashlytics call is guarded; logging can never
  crash the app.
- Setup: create a Firebase Android app for `com.agentdeck.app`, drop the real
  `google-services.json` into `android/app/`, then rebuild.

**Lifecycle and connectivity**
- `AgentDeckViewModel.onCleared()` calls `AgentDeckState.shutdown()`, which
  cancels host/local/model/cloud jobs, clears caches, and unregisters the
  connectivity callback.
- `NetworkMonitor` registers a default `NetworkCallback`; `AgentDeckState.online`
  drives an offline pill above the bottom bar on root screens. Verified on the
  emulator by disabling/enabling radios.

**App lock (security)**
- Optional biometric / device-credential gate covering the whole app, toggled in
  Settings → Security. Default is off.
- `ProcessLifecycleOwner` re-locks on app background (config changes do not
  trigger a lock). The lock screen auto-opens the system prompt after a short
  focus delay and always offers an Unlock button.
- If the device has no screen lock, the lock screen explains why and offers
  "Turn off app lock" so the user can never be locked out.
- `MainActivity` now extends `FragmentActivity` because `BiometricPrompt`
  requires it; dependencies are `androidx.biometric:biometric:1.1.0`,
  `androidx.fragment:fragment-ktx:1.8.5`, and
  `androidx.lifecycle:lifecycle-process:2.8.6`.
- Verified on the emulator: enabling, PIN unlock, background re-lock, relaunch
  auto-prompt, PIN removal fallback, and disabling.
- `SecureStore.put`/`remove` now commit synchronously so "key saved" is durable
  before the UI reports success.

**Backup & restore**
- Settings → Backup & restore exports a portable JSON workspace (tasks,
  messages, diffs, checks, decisions, settings) through the system file picker.
  API keys and tokens are excluded — they remain in the Keystore.
- Import validates the format/version, replaces the stored workspace in one
  transaction, re-validates project/model IDs, and recovers interrupted runs.
- Import is refused while a request is in flight.
- Verified on the emulator: exported 54 KB / 12 tasks, re-imported, database
  showed 12 tasks and restored settings.

**Error handling**
- `CloudError` sealed hierarchy (network, HTTP, auth, rate limit, timeout,
  validation, invalid response) with `isRetryable`.
- `CloudClient` retries transient failures only while no reply text has been
  delivered, so a retry cannot duplicate a partial stream. Backoff:
  1s → 2s → 4s → 8s → 10s cap.
- `AgentDeckState.streamCloudReply()` persists a per-task `retryAttempts`
  counter and surfaces user-friendly failure text and activity labels.

**Repository layout**
- GitHub default branch: `agentdeck-root`, with the app at the repository root
  (`android/`, `README.md`, `.github/workflows/android.yml`).
- The workspace `C:\Users\Rahul\Downloads\qoder\AgentDeck2.0` is now its own git
  repository tracking `origin/agentdeck-root`. The parent `qoder` repo (`main`)
  is legacy and is no longer the source of truth.
- GitHub Actions runs unit tests and assembles the debug APK; the run on
  `agentdeck-root` is green.

**On-device inference (llama.cpp)**
- Two native sets, both pinned to llama.cpp `b11851`:
  - **Standard** (`src/standard/jniLibs/arm64-v8a`): official Android CPU package
    with runtime-dispatch variants (`armv8.0`, `armv8.2+dotprod`, `armv8.6+i8mm`,
    `armv9.0`, `armv9.2`). `libllama` is DL-based; the JNI calls
    `ggml_backend_load_all_from_path(nativeLibraryDir)` and the loader picks the
    best variant for the CPU. Emulator x86_64 libs are built locally.
  - **Snapdragon** (`src/snapdragon/jniLibs/arm64-v8a`): official Android
    Snapdragon package with CPU, Adreno OpenCL, Hexagon NPU, and HTP skels
    v73/75/79/81/85. Selected with `-Psnapdragon`.
- `llama_jni.cpp` exposes load/free/cancel/generate plus `nativeInit`,
  `nativeDevices`, and `nativeLastBackend`. It enumerates backends, selects
  NPU/GPU/CPU from the user setting (Auto prefers NPU, then GPU), sets
  `n_gpu_layers = 999` for accelerators, and retries on CPU if the accelerator
  rejects the model or context.
- Backend isolation and DSP discovery (added after iQOO 13 bring-up):
  - Every load passes an explicit single-device list, including CPU. A CPU or
    GPU request therefore never initializes HTP during context creation; before
    this, an HTP session failure aborted otherwise-valid CPU runs.
  - `ensureBackend()` sets `ADSP_LIBRARY_PATH`, `CDSP_LIBRARY_PATH`, and
    `CDSP1..3_LIBRARY_PATH` to the native library dir before loading backends.
    FastRPC resolves `libggml-htp-v79.so` through these paths; without them the
    session fails with `AEE_EUNABLETOLOAD (0x80000406)`. This mirrors the
    official `scripts/snapdragon/run.py` environment.
- Settings → Acceleration: Auto / NPU · Hexagon / GPU · Adreno / CPU, with the
  detected backend list. Changing it requires reloading a model.
- Packaging requirements discovered during integration:
  - `<uses-native-library>` for `libOpenCL.so` and `libcdsprpc.so`; without it
    vendor public libs are not in the app namespace.
  - `packaging.jniLibs.useLegacyPackaging = true` so ggml can scan and dlopen
    backend variants and HTP skels from the native library dir.
  - Load `libOpenCL.so` explicitly before the JNI library on Snapdragon builds
    (defensive; harmless elsewhere).
  - Exclude `libggml-rpc.so` from the standard set: ggml auto-loads every
    backend in the directory and the RPC backend deadlocks in-app.
  - R8 keep rules for `LocalEngine`, native methods, and `TokenCallback`; JNI
    clears a failed callback lookup instead of crashing. Verified in release.
- Model import/catalogue, streaming, stop, attribution, and On-device badges
  are unchanged from the earlier engine work.
- Build commands:
  - `./gradlew :app:assembleDebug` → standard (`app-debug-standard.apk`)
  - `./gradlew :app:assembleDebug -Psnapdragon` → Snapdragon APK
  - `tools/fetch-llama-android.sh` restores both arm64 sets from official
    release packages; `tools/build-llama-x86_64.sh` rebuilds the emulator libs.
- Verification:
  - Standard APK on a Snapdragon 888 (Xiaomi): backends `CPU`, runtime selected
    `libggml-cpu-android_armv8.2_2.so`, Llama 3.2 1B Q4_0 loaded in ~1.1 s and
    generated 64 tokens at **18.0 tokens/s**.
  - Snapdragon APK on the same phone: app launches and cloud/host features work,
    but the native engine is unavailable because the 888's OpenCL 2.0 lacks
    `clCreateBufferWithProperties` and its HTP is older than v73. Expected; the
    Snapdragon APK targets newer chips.
  - **Snapdragon APK on iQOO 13 (SM8750, Snapdragon 8 Elite, Android 16, HTP
    v79, Adreno 830):** backends `GPUOpenCL, HTP0, CPU`. Llama 3.2 1B Instruct
    Q4_0, 64-token generation, same debug build:
    - NPU (`HTP0`): **56.3 / 59.0 tokens/s**, load 1.4–1.8 s, context reserve
      193 ms, 17/17 layers offloaded, 267 MiB HTP compute buffer.
    - Auto: `HTP0`, **61.0 tokens/s**, load 0.76 s.
    - CPU: **41.7–45.0 tokens/s**, load 0.6–7.8 s.
    - GPU (`GPUOpenCL`): **12–16 tokens/s**, load 4.2–5.8 s. Decode is slower
      than CPU for this 1B Q4_0 model, so GPU remains an explicit choice; Auto
      prefers the NPU. GPU prefill/larger-model behavior is not yet measured.
- Test hook: `LocalEngineDeviceTest` (instrumentation) lists backends and runs a
  generation with `-e modelPath ... -e accelerator ...`.
  - The test launches `MainActivity` and sets keep-screen-on: on OriginOS the
    cached-app freezer suspends the instrumentation process ~10 s after start,
    even with a resumed activity, if the display is off. `cmd activity unfreeze
    --sticky` is not sufficient. For manual runs use `svc power stayon true`.
  - On MIUI, `/data/local/tmp` is readable by the app even when `run-as` is
    blocked. On the iQOO, `run-as` works and the model was copied into
    `files/test.gguf`.

**Host bridge (real execution)**
- `tools/agentdeck-host/server.mjs` is a zero-dependency Node 18+ host agent.
  It creates `agentdeck-host.config.json` on first run with a generated token,
  project paths, an agent command, and checks.
- Endpoints (Bearer token required): `GET /v1/ping`, `POST /v1/run` (SSE),
  `POST /v1/permission`, `POST /v1/cancel`. Events: `plan`, `status`,
  `message`, `file`, `check`, `permission`, `done`, `error`, plus heartbeats.
- A run streams a plan, asks for approval in the app (120s timeout = decline),
  runs the configured agent CLI in the project folder, streams stdout/stderr,
  collects `git diff HEAD` (up to 8 files / 200k chars), runs checks
  sequentially (180s each), and ends in review. Cancel kills the process tree.
- Security: localhost bind by default, token on every request, project roots
  and commands come only from host config, no arbitrary commands from the phone.
  LAN mode is opt-in (`--host 0.0.0.0` plus "Allow unencrypted LAN" in the app);
  HTTPS via a tunnel is recommended off-LAN.
- Android: `data/HostClient.kt` (SSE + permission/cancel/ping), `data/HostDiff.kt`
  (unified diff → DiffLine), host fields in `AppConfig`/`SecureStore`
  (`host.token`), and `AgentDeckState.startLiveHostRun`/`handleHostEvent`.
  With no host configured, host tasks fail with an actionable error
  ("Connect an execution host in Settings…").
- Settings → Execution host: setup sheet with URL, token, insecure-LAN toggle,
  test, save & connect, reconnect, and remove. The host card shows live/offline
  state; `Live host` appears on tasks that produced real host events.
- Verified end-to-end on the emulator against a local Windows host: connect,
  create task, approve, real file change on disk, streamed diff and check,
  review state, and a clean second run with no stale failures. Earlier stale-job
  and 204-response bugs were fixed.
- Host protocol tests: 9 (`node --test tools/agentdeck-host/server.test.mjs`).

**Product copy**
- Demo framing is gone: the header chip reads `LOCAL` and the info sheet is
  "How this works" ("Where your work runs"). After the demo removal below, no
  copy anywhere claims simulation.
- The placeholder account/sign-out flow was removed; Settings shows a local
  profile with no account.
- Reset is "Reset workspace" (clears to an empty workspace); the footer shows
  the real package version.

**Tests**
- 63 JVM tests pass (`./gradlew :app:testDebugUnitTest`), including
  `CloudErrorHandlingTest` (11), `WorkspaceBackupTest` (9), `HostClientTest` (8),
  `HostDiffTest` (5), `LocalModelRegistryTest` (5), and codec retry-attempt
  persistence.
- Host agent: 9 protocol tests pass (`node --test tools/agentdeck-host/server.test.mjs`).
- `androidTest/AppSmokeTest` launches the app, waits for hydration, and opens
  Settings. Verified green on the `agentdeck` AVD:
  `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`.
  Running without `ANDROID_SERIAL` also targets the physical phone, which fails
  while its lock screen is up.
- GitHub Actions (`.github/workflows/android.yml`) runs tests and assembles the
  debug APK on pushes and PRs that touch `android/`.

**Repository docs**
- `README.md` covers features, privacy properties, build/release steps,
  architecture, test inventory, and known limitations.

**Two-mode restructure of v1 UI (2026-10-25, final)**
- Home is now a pure launcher: two large cards — **Chat** (AI assistant) and
  **Code** (coding agent) — plus a Recent list. Removed from Home: host
  offline banner, project switcher, stats grid, search field, LOCAL pill.
- Bottom bar: Home / Chats / Coding / Settings. The host banner now only
  appears on host-model tasks; New Task subtitles are per-mode; list titles
  are Chats/Coding.
- **Install note for this phone:** the installed build is RELEASE-signed
  (user installed a dist APK). `adb install -r` of a debug APK fails with
  INSTALL_FAILED_UPDATE_INCOMPATIBLE. Update in place with
  `adb install -r dist/agentdeck-snapdragon-1.0.0.apk` (or standard) — data
  survives; debug installs require uninstalling (data loss).

**Two-mode launcher + Termux bridge + llama b11876 (2026-10-25, late)**
- **Simplified interface**: Home now opens with two launcher cards — Chat and
  Coding — each jumping straight into New Task with that mode preselected.
  The bottom bar is Home / **Chats** / **Coding** / Settings: the old Tasks
  tab shows only chat conversations, the old Projects tab shows coding tasks
  for the active project (project management moved to the existing project
  sheet from Home). No projects exist by default, so Coding needs a project
  first (creation form lives in the project sheet).
- **llama.cpp updated b11851 → b11876**: both native sets re-fetched from
  official release packages (now `.tar.gz` assets) via
  `tools/fetch-llama-android.sh` (default tag bumped). Standard runtime-
  dispatch CPU variants and Snapdragon CPU/OpenCL/HTP skels v73–v85 all
  replaced; the Hexagon updates are inside the skels (skel list unchanged).
  Not yet regenerated: emulator x86_64 libs (tools/build-llama-x86_64.sh).
- **Termux bridge (real toolchains on-device)**: `data/SshRunner.kt` executes
  commands in Termux's sshd over 127.0.0.1:8022 (JSch). Setup lives in
  Settings → Device shell: enable, port, Termux `whoami` user, password
  (Keystore). The `run` tool routes automatically: toybox-allowed commands
  stay in the in-app shell; anything else (node, python, gradle…) goes to
  Termux **when the bridge is enabled and ready**, otherwise the allowlist
  error explains the limit. The workspace moved to
  `/storage/emulated/0/Android/media/com.agentdeck.app/workspace` so Termux
  (with storage permission) and AgentDeck share the same real files;
  falls back to app-private storage if the media dir is not writable. The
  coding system prompt tells the model which shell applies. One-time Termux
  setup: `pkg install openssh`, `passwd`, `sshd`, `termux-setup-storage`.
  Not yet device-verified: the full SSH round-trip (phone locked during the
  session); the Test button in Settings checks it (`echo … && uname -o`).
  Device-verified 2026-10-25: Test passed ("works · Android"), and the bridge
  was then used for real toolchains. Two live issues found and fixed on the
  device: (1) the Termux username must be exactly `whoami` output
  (`u0_a705` — a missing underscore fails auth); (2) the default Termux
  mirror host (185.224.83.107) was unreachable from the user's ISP — every
  pkg mirror "bad" while ping/DNS/google.com worked. Fix: point
  `$PREFIX/etc/apt/sources.list` at the Cloudflare-backed
  `packages.termux.dev` mirror, then `pkg install -y nodejs python`
  (node v26.12.1, python 3.14.8, npm, clang, make verified).

**Dual-mode harness: Chat + Coding (2026-10-25)**
- New Task presents two mode cards. **Chat** = general assistant with real
  tools; **Coding** = the workspace agent (unchanged). Kind persists on the
  task (`Task.kind`, codec-aware) and chat tasks are visible in every project
  list (global).
- **Chat tools** (auto-run): `web_search` (DuckDuckGo HTML endpoint — a scrape,
  not a contract; errors honestly if markup changes), `web_fetch` (any URL →
  extracted text; HTML/JSON/plain aware, size-capped), `memory_save` /
  `memory_recall`; **`http`** (GET/POST/PUT/DELETE, approval-gated with the
  exact URL + body shown). Coding mode keeps workspace/git/shell tools and
  gains web_fetch/web_search/memory for docs lookups.
- **Backends**: the tool loop now runs on cloud (streamed) OR on-device
  (llama.cpp, blocking per step, full reply appended). Chat + local models get
  the same tool loop; coding + local keeps the plain chat path (tool quality
  on 4B models is poor — revisit deliberately).
- **Memory** (`data/AgentStores.kt` MemoryStore): JSON on app storage, 60
  entries cap, term recall with recent-fallback, top-10 snapshot injected into
  both system prompts. **Instructions** (InstructionStore): named reusable
  prompt sets, curated in Settings → Agent intelligence → "Manage instructions
  & memory" (add/delete, memory viewer + clear), selectable per task in the
  New Task sheet; injected as `[User instructions for this task]`.
- Chat tasks show a single Chat tab (no Changes/Checks); their runs end at
  needs-input (no git finalize). Errors still route into the loop for
  self-recovery; failure cards unchanged.
- Tests: 95 JVM unit tests (added: MemoryStore, HTML text extraction, DDG
  parse, prompt builder per mode).
- Honest limits: web_search scrapes DDG (may break — it reports instead of
  faking); http calls send data off-device and are always approved first;
  memory is plaintext JSON (user-inspectable); no file attachments in chat
  yet (next step); local models are not streamed in the loop (blocking per
  step).

**On-device agent harness (2026-10-25)** — the core host-independent execution path:
- **Workspace** (`data/Workspace.kt`): real files under
  `files/workspace/<projectId>/`. Path confinement (`Workspace.confine`, pure
  + unit-tested) rejects `..`, backslashes, colons, and anything canonicalizing
  outside the root. `importFromGitHub` downloads the codeload zip and extracts
  it (entry/size/total caps); `list/read/write/grep/clear/stats` with size
  caps and binary detection.
- **Shell** (`data/ShellRunner.kt`): `/system/bin/sh -c` with an allowlist of
  toybox utilities (su/am/pm/cmd/settings/dd/mount/reboot blocked), 20 s
  timeout, 20 kB output cap. Commands require user approval.
- **Git** (`data/WorkspaceGit.kt`): JGit 5.13.5 (pure Java; R8 needs
  `-dontwarn javax.management.**` / `java.lang.management.**`). init, status,
  commitAll, diffHead (cached diff incl. untracked, `diff --git` format),
  log. All local; publishing stays on the GitHub PR flow.
- **Tool protocol** (`data/AgentTools.kt`): the model emits
  ` ```agentdeck-tool <name> key="value" ` fenced blocks (list / read / write /
  run / grep / git). `ToolParser` reuses FenceScanner; `ToolExecutor` runs
  them against the real workspace/shell/git; `needsApproval` = write/run/commit;
  `UnifiedDiffFiles` splits JGit diffs into ChangedFiles for the Changes tab.
- **Agent loop** (`AgentDeckState.runAgentLoop`): cloud task with
  `agentMode = true` routes to the loop instead of plain streaming — reply →
  parse tools → execute (reads automatic; writes/commands raise the existing
  permission card via `CompletableDeferred`, 120 s timeout = decline) →
  results fed back as the next user turn — up to 8 steps, then finalize:
  git diff → Changes tab → REVIEW, or NEEDS_INPUT with no changes. Errors feed
  back to the model for self-recovery; loop failure → retryable Failure.
- **UI**: New Task has a "Run as agent" toggle (cloud models only); Settings
  has an "Agent workspace" card (import from GitHub / clear / stats);
  Projects gained a **New project** creation form (creation UI never existed —
  seed data used to provide projects); agent tasks show a dedicated banner and
  tool notes as system messages (⚙ label → output first line).
- Tests: 91 JVM unit tests (added: ToolParser parse/approval/results,
  Workspace path confinement, UnifiedDiffFiles splitting).
- Honest limits: stock Android has no Node/Python/Gradle toolchains — the
  system prompt says so and steers verification to CI/inspection; shell is
  toybox-only. Local models are not wired to the agent loop yet.
- Untested on device end-to-end (phone auto-locked during the session):
  the full import → agent task → approval → diff flow. All pieces are unit
  tested and compile; first live run is the next action.

**Cloud profiles + local model lifecycle (2026-10-25)**- Multiple cloud APIs: `CloudProfile` (id, label, baseUrl, saved model ids,
  last-used model) persisted in `AppConfig.cloudProfilesJson`; each API key
  lives in SecureStore under `cloud.key.<id>`. The pre-profiles single config
  migrates into a profile automatically on first launch (key moved, legacy
  `cloud.api-key` cleared). `cloudConfig` is now a derived view of the active
  profile.
- Settings → Cloud APIs: list with Use/Models/Edit/Remove, an add/edit editor,
  and a per-profile model manager — "Fetch from API" (`GET /models`,
  `CloudClient.listModels`) lists endpoint models for tap-to-save; saved ids
  can be removed. Using a model id in a conversation saves it automatically.
- Conversation model sheet is compact: saved cloud models as one-tap rows, a
  small manual id field, and local models that load on selection. Only one
  local model stays in memory — loading a model unloads every other loaded or
  loading model (models with active generation are skipped); switching to an
  unloaded model inside a conversation loads it automatically.
- Fixes after device testing (2026-10-25, all verified on the iQOO 13 against
  the user's live OpenRouter profile):
  - **Local-generation crash**: `nativeGenerate` passed each token piece to
    Kotlin with `NewStringUTF`; a multi-byte UTF-8 character split across
    tokens produced an invalid fragment and the JNI checker aborted the
    process (`SIGABRT`, "input is not valid Modified UTF-8"). The JNI layer
    now decodes pieces to UTF-16 itself (`NewString` with surrogate pairs),
    carries incomplete trailing bytes to the next token, and maps invalid
    sequences to U+FFFD. Verified: two full generations on the bilingual
    Spark model with zero crash signals.
  - The "No reply / returned no text" notice now includes the engine result
    code (-1 load/encode, -2 decode, -3 stopped) to separate model silence
    from engine failure.
  - **Mid-reply stops / "no reply" in long conversations**: the KV cache was
    never cleared between generations, so the second message in a
    conversation appended onto a full 2048-token cache and decode failed
    ("failed to find a memory slot", engine code -2). `nativeGenerate` now
    calls `llama_memory_clear` at the start of every generation (each call
    re-prefills the whole prompt anyway). Context raised 2048 → 4096,
    max tokens 384 → 512, and local conversations use a dedicated turn
    budget (`buildLocalTurns`: last 8 turns, 1.2k chars each, 8k total)
    instead of the cloud-sized one. Oversized prompts now keep the
    template/system head (256 tokens) plus the recent tail — the old
    tail-only truncation cut the chat template apart and produced silence.
    Verified: two consecutive generations with zero memory-slot warnings.
    Note: OriginOS freezes the app when the screen dims mid-generation and
    the reply then never completes — that is the device freezer, not the
    engine; test with the screen on.
  - `listModels` built `/chat/completions/models` because it appended to the
    chat endpoint; it now builds `{base}/models`, and reads the body with an
    8 MB cap (OpenRouter's list is ~2 MB; the 200 KB chat cap truncated it to
    an unparseable "0 models").
  - The send path (`startCloudAnalysis`, `startCloudReply`, task creation,
    connection test) still read the legacy `cloud.api-key`, which profile
    migration clears — every send failed with "Cloud API key missing" and
    flipped `keySaved` off. All reads now go through
    `activeCloudApiKey()` (profile key with legacy fallback).
  - Tapping the task's *current* unloaded local model was a no-op; it now
    loads it (same-model check runs before the ready check).
  - `CloudConfig.endpointReady` (endpoint + key, no model required) now gates
    the conversation cloud section and `switchTaskToCloud`; previously
    removing your only saved model blanked the default model and hid the
    manual field behind a "Connect a cloud API" dead end.

**Host-independent execution via GitHub (2026-10-24)**
- The host agent is now strictly optional. The standalone execution path is
  GitHub-native and runs entirely from the phone:
  - The cloud system prompt instructs the model to emit each proposed file as
    an `agentdeck-file path="…"` fenced block containing the complete new file
    content (`data/Proposal.kt` parses these; last-write-wins per path).
  - **Changes tab → Apply** commits every proposed file to a new
    `agentdeck/<slug>-<taskId>` branch (Contents API), opens a pull request,
    and moves the task to review with real diffs (old content comes from the
    task's repo snapshot when present; otherwise the file is shown as added).
  - The Checks tab fetches the repository's CI check runs for the applied
    branch (`/commits/{ref}/check-runs`) and keeps polling (~20 s, up to ~7
    minutes) while any run is queued or in progress. A manual Refresh action
    exists on applied tasks.
  - Writes require the user's fine-grained token (Contents + Pull requests:
    Read and write) and an explicit Apply tap. The model can never trigger a
    write, and nothing pushes to the default branch.
  - Re-applying after further chat updates the same branch and reuses the open
    pull request.
- `GitHubClient` grew a write path: `branchHeadSha`, `ensureBranch` (422 =
    reuse existing), `putFile` (create/update via sha), `createPullRequest`
    (422 = return the open PR), `checkRuns`. Reads still work without a token.
- The built-in host model row now appears only while a host is configured
    (added on connect, removed with the host config); `defaultModelId()`
    prefers cloud. All host references in copy are labelled optional.
- Host bridge code and protocol are unchanged and still work end to end.

**Demo removal (2026-10-24)**
- The seeded demo workspace is gone. `SeedData.kt` was deleted; the app starts
  from `AgentDeckState.emptyWorkspace()` (no projects, no tasks, cloud + host
  model rows, imported GGUF models re-attached from the registry).
- Simulated host runs are gone (`startSimulatedHostRun`, `demoFiles`,
  `demoChecks`, `fileFromLines`, `buildDiff` deleted). A host task without a
  reachable host fails fast with an actionable error.
- Simulated local replies are gone (`localReply()` and the fake 1.6 s typing
  path deleted, `localTyping` state removed). Sending to a local model without
  a loaded engine posts a "Local model not loaded" system notice instead.
- The fake 1.3 s model-load path is gone; only imported GGUF models can load,
  through the real llama.cpp engine.
- A built-in `HOST_MODEL_ID` ("Host agent") model row keeps host execution
  reachable from the New Task sheet now that seed host models no longer exist.
- The home demo-notice card was removed; the top-bar info pill opens the
  "How this works" sheet (`Sheet.About`). `Sheet.DemoInfo`, `openDemoInfo`,
  `dismissDemoNotice`, `DemoTag`, `PreviewTag` were removed/renamed
  (`HostTag`, `PlaceholderTag` with explicit text).
- `AppSettings.demoNoticeDismissed` and the matching Room column/backup key
  stay for schema v1 and older-backup compatibility; nothing reads it anymore.
- "Clear workspace" (`clearWorkspace()`, renamed from `resetDemo()`) keeps
  imported local models in the picker along with the cloud and host rows.
- The web prototype in `app/` and the visual bundles remain untouched as
  non-runtime references.

---

## 3. Architecture map

### Entry and navigation

- [MainActivity.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/MainActivity.kt)
  - Edge-to-edge setup and single Compose host.
- [App.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/App.kt)
  - `AgentDeckViewModel : AndroidViewModel`.
  - Owns `AgentDeckState`.
  - Renders screens, bottom navigation, sheets, and snackbar.
- Navigation is custom:
  - `Screen` sealed interface.
  - A back stack inside `AgentDeckState`.
  - No Android Navigation component.

### State and data

- [Models.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/Models.kt)
  - Tasks, messages, models, providers, plans, changes, checks, sheets, repo context.
- [AgentDeckState.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/AgentDeckState.kt)
  - Central Compose state, navigation, task creation, messaging, model switching, cloud analysis, GitHub context, live host runs, and the llama.cpp engine bridge.

Key state concepts:

- `ModelProvider`: `HOST`, `LOCAL`, `CLOUD`.
- `CLOUD_MODEL_ID`: a single dynamic cloud row; `HOST_MODEL_ID` is the built-in
  host-agent row. Imported GGUF models are appended from the registry.
- `Task.cloudModel`: per-conversation cloud model override.
- `Message.modelName` and `Message.provider`: per-reply attribution.
- `Task.repoContext`: read-only GitHub snapshot attached to a task.
- `Task.analysisOnly`: true when the current mode is cloud analysis.
- `isTaskBusy(taskId)`: guards sending and model switching during cloud/local/context work.
- `repoCache`: in-memory 10-minute GitHub context cache.
- `Task.activeRun`: true only while a request started by this app is live; restore reconciles exactly those runs.

### Persistence

- [local/TaskEntity.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/local/TaskEntity.kt)
  - `tasks`: queryable scalar columns plus `payloadJson`.
  - `app_settings`: one row for non-secret workspace choices.
- [local/TaskCodec.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/local/TaskCodec.kt)
  - Encodes messages, activity, files/diffs, checks, decisions, permission, failure, plan, and repo context as ordered JSON arrays.
  - Unknown keys are ignored and malformed rows are skipped, so additive payload changes do not crash restore.
- [local/TaskStore.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/local/TaskStore.kt)
  - Loads the whole workspace once; saves a consistent snapshot through a transaction.
- [local/AgentDeckDatabase.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/local/AgentDeckDatabase.kt)
  - Room 2.8.5 on KSP 2.3.12, which supports AGP 9 built-in Kotlin.
  - Schema version 1 is exported to `android/app/schemas/`.
- `AgentDeckState`:
  - `restoreWorkspace()` loads persisted tasks/settings asynchronously, validates project/model IDs, and sets `hydrated`.
  - The UI shows a small restore screen until `hydrated` is true.
  - `startPersistenceLoop()` saves when the snapshot differs from the last saved state (about once per second).
  - `recoverInterruptedTasks()` finds `IN_PROGRESS` tasks with `activeRun = true`, marks in-flight messages failed, and converts host runs to retryable `FAILED` or cloud runs to `NEEDS_INPUT` with an explicit system notice.
  - `clearWorkspace()` clears the database to an empty workspace (cloud/GitHub settings and imported models are kept).
- Room stores task content and repository snapshots, not API keys/tokens. Secrets remain in Android Keystore. App backup is disabled so the Room DB is not copied off-device by Auto Backup.
- Any future entity change needs a Room migration; version 1 has no migration history yet.

### Network and configuration

- [CloudClient.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/CloudClient.kt)
  - Direct OpenAI-compatible chat requests.
  - SSE parsing with non-SSE JSON fallback.
  - Curated HTTP error messages.
  - Coroutine cancellation propagates to OkHttp.
  - Reply-size limit.
  - HTTPS required for remote endpoints; cleartext only for loopback test servers.
- [CloudTurns.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/CloudTurns.kt)
  - Pure provider-turn builder shared by normal sends and retries.
  - Excludes system/blank messages, caps history at 14 turns and 30,000 characters, and truncates each turn to 6,000 characters.
  - On retry, drops trailing partial assistant messages so the request ends on the last user turn.
- [GitHubClient.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/GitHubClient.kt)
  - Read-only GitHub REST access.
  - Parses `owner/repo` or a GitHub URL.
  - Fetches repository metadata, recursive tree, and selected raw file contents.
  - Selects README, manifests, and up to 8 relevant source files based on task terms.
  - Applies directory, lock-file, extension, size, and total-character limits.
  - Marks snapshots truncated when GitHub or the local budget truncates them.
- [SecureStore.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/SecureStore.kt)
  - Android Keystore AES-256-GCM.
  - Stores only ciphertext in `agentdeck.secrets` SharedPreferences.
  - Removes undecryptable entries safely.
- [AppConfig.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/AppConfig.kt)
  - Non-secret cloud URL/model and GitHub repo/branch in `agentdeck.config`.

### UI

- [Components.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/Components.kt)
  - Shared cards, buttons, tags, chips, banners, fields, task cards, and bottom navigation.
- [HomeScreen.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/HomeScreen.kt)
- [TasksScreen.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/TasksScreen.kt)
- [ProjectsScreen.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/ProjectsScreen.kt)
- [SettingsScreen.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/SettingsScreen.kt)
- [NewTaskScreen.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/NewTaskScreen.kt)
- [TaskScreen.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/TaskScreen.kt)
- [Markdown.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/Markdown.kt)
  - Pure Markdown block/inline parser plus the Compose renderer used for cloud replies.
- [Sheets.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/Sheets.kt)
- [Theme.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/theme/Theme.kt)

Important UI behavior:

- Cloud tasks show `Cloud` and `ANALYSIS ONLY` tags.
- Cloud replies show the model and a `Cloud` chip and render Markdown; user, system, host, and local messages stay plain.
- Markdown fenced code scrolls horizontally inside the bubble; it is never wrapped or executed.
- Local replies show the model and `On this phone`.
- Host replies show the model without a cloud/local chip.
- The activity card shows `Cloud`, `On device`, `Live host`, `Mixed`, or `History` based on actual history.
- The task composer's model row contains the `Change` affordance.
- The task model sheet is in [Sheets.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/Sheets.kt) as `TaskModelsSheet`.
- The app column is capped at 430 dp on wide screens.

---

## 4. Product and security invariants

Preserve these unless the user explicitly changes direction.

1. **Cloud inference is not execution.**
   - Cloud tasks can analyze, plan, and propose files (`agentdeck-file` blocks).
   - They must never claim to write files, run commands, run tests, merge, or deploy.
2. **Writes are user-triggered and pull-request-shaped.**
   - Applying a proposal requires the user's explicit Apply tap plus a write
     token, and only ever creates a branch and pull request — never a direct
     push to the default branch.
3. **Host execution is optional and host-gated.**
   - The optional host agent performs real work on the user's computer only
     after per-run approval; without one, nothing about the app is blocked.
4. **Local inference is real and on-device.**
   - Local replies come only from the bundled llama.cpp runtime on an imported
     GGUF model. If no engine is loaded, say so — never fabricate a reply.
5. **No gateway for personal BYOK.**
   - Requests go directly from the phone to the endpoint entered by the user.
6. **Never store a key in source or the APK.**
   - Runtime BYOK only.
   - The current debug APK contains no API key or GitHub token.
7. **Secrets are Keystore-encrypted.**
   - SharedPreferences contain ciphertext only.
   - API-key and token fields use password IME behavior.
8. **GitHub token is never sent to the cloud model.**
   - Only repository content selected for the task is sent to the configured cloud endpoint.
9. **Repository content is untrusted data.**
   - The cloud system prompt tells the model not to treat repository text as instructions.
10. **Do not log prompts, responses, keys, or tokens.**
11. **Remote endpoints require HTTPS.**
    - Cleartext is allowed only for `10.0.2.2`, `localhost`, and `127.0.0.1` for local testing.
12. **Do not introduce a WebView wrapper.**
13. **Do not infer that "keep it as is" means a hybrid architecture.** Confirm native versus wrapper when ambiguous.

---

## 5. Build and run

### Requirements

- Windows host with Git Bash.
- Android SDK at:
  - `C:\Users\Rahul\AppData\Local\Android\Sdk`
- JDK compatible with AGP 9.
- Existing AVD:
  - Name: `agentdeck`
  - Android 16 / API 36 Play Store x86_64 image.
- `adb` available on PATH.

### Build

```bash
cd /c/Users/Rahul/Downloads/qoder/AgentDeck2.0/android
./gradlew :app:assembleDebug
```

Artifact:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release build

```bash
./gradlew :app:assembleRelease   # signed APK when keystore.properties exists
./gradlew :app:bundleRelease     # Play Store AAB
```

Signing reads `android/keystore.properties`:

```properties
storeFile=app/agentdeck-release.jks
storePassword=…
keyAlias=agentdeck
keyPassword=…
```

Both the `.jks` and `keystore.properties` are gitignored. Back them up
somewhere safe — losing the key prevents updates to the installed app.


### Start the emulator

```bash
/c/Users/Rahul/AppData/Local/Android/Sdk/emulator/emulator.exe \
  -avd agentdeck \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -gpu swiftshader_indirect
```

If the emulator reports `offline` after a normal start, cold-boot it:

```bash
/c/Users/Rahul/AppData/Local/Android/Sdk/emulator/emulator.exe \
  -avd agentdeck \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot-load \
  -gpu swiftshader_indirect
```

### Install and launch

```bash
cd /c/Users/Rahul/Downloads/qoder/AgentDeck2.0/android
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.agentdeck.app/.MainActivity
```

Clear app data only when intentionally starting from a clean state:

```bash
adb shell pm clear com.agentdeck.app
```

### Useful inspection commands

```bash
adb exec-out screencap -p > screenshot.png
MSYS_NO_PATHCONV=1 adb shell uiautomator dump /sdcard/ui.xml
MSYS_NO_PATHCONV=1 adb pull /sdcard/ui.xml _ui.xml
adb shell run-as com.agentdeck.app cat shared_prefs/agentdeck.config.xml
adb shell run-as com.agentdeck.app cat shared_prefs/agentdeck.secrets.xml
```

`MSYS_NO_PATHCONV=1` is required for `/sdcard/...` paths in Git Bash.

---

## 6. Manual test recipe

### Cloud connection

1. Start any OpenAI-compatible local mock at `127.0.0.1:8787`.
2. In the emulator, use base URL:
   - `http://10.0.2.2:8787/v1`
3. Use a fake key such as `test-key-123`.
4. Settings → **Set up cloud API**.
5. Enter URL, model, and key.
6. Test:
   - Wrong key should produce a 401 message.
   - Correct key should show "Connection works".
7. Save.
8. Verify:
   - `agentdeck.secrets.xml` contains ciphertext only.
   - `agentdeck.config.xml` contains URL/model but no key.
   - A recursive search for the test key under app data returns nothing.

### GitHub context

1. Settings → **Connect GitHub**.
2. Repository:
   - `octocat/Hello-World`
3. Leave branch and token empty for the public test.
4. Test should report public access and the default branch.
5. Save.
6. Create a cloud task with "Include repository snapshot" enabled.
7. Confirm:
   - Context card appears.
   - Context sheet lists files.
   - The mock request contains the system guard and repository context.
   - The task does not create changes or checks.

### Mid-conversation model switching

1. Open a cloud or local task.
2. Tap `Change` in the composer footer.
3. Change the cloud model name and switch.
4. Send a message.
5. Confirm:
   - The request uses the task-specific model.
   - The new reply is labeled with that model.
   - Earlier replies retain their original labels.
6. Switch to a ready local model.
7. Send a message.
8. Confirm:
   - A local reply streams from the loaded GGUF model (load one first; an unloaded model shows a "not loaded" notice instead).
   - No cloud request is made.
   - The header switches from `Cloud / ANALYSIS ONLY` to `Local`.
   - Activity shows `Mixed` after the conversation has both cloud and local history.
9. Open a host task.
10. Confirm there is no `Change` affordance.

---

## 7. Verification already completed

The current APK was verified on the `agentdeck` AVD using a local mock OpenAI-compatible SSE server and the public `octocat/Hello-World` repository.

Verified:

- Cloud setup, save, test, and 401 handling.
- GitHub public repository connection and context fetch.
- Cloud task creation with repository context.
- Cloud analysis while Studio Mac was disconnected.
- Streaming replies and follow-up messages.
- Cloud request failure and recovery after the endpoint came back.
- Keystore encryption and absence of plaintext secrets.
- Mid-conversation local → cloud → local switching.
- Task-specific cloud model override in the actual request.
- Per-message model/provider attribution.
- Host tasks not exposing the model switcher.
- Rotation preserving task state.
- App restart preserving cloud/GitHub settings.
- Clean rebuild after the latest changes.

### Persistence verification

Commands:

```powershell
cd C:\Users\Rahul\Downloads\qoder\AgentDeck2.0\android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```

JVM tests currently total 63 and all pass:

- `TaskCodecTest` (5): task/message round-trip, defaults, ordering, damaged payloads, settings, retry attempts.
- `MarkdownTest` (8): block/inline parsing, fences, escapes, link safety, empty input.
- `CloudClientTest` (7): URL scheme/loopback rules, credentials/query rejection, endpoint construction, provider labels.
- `CloudTurnsTest` (5): system exclusion, turn/history/character caps, and retry trailing-assistant removal.
- `CloudErrorHandlingTest` (11): retryability, retry-button policy, backoff growth/cap, and failure labels.
- `WorkspaceBackupTest` (9): round trip, format/version validation, skipped rows, defaults, empty backups.
- `HostClientTest` (8): host URL policy, insecure-LAN opt-in, endpoint building, loopback rules.
- `HostDiffTest` (5): hunk parsing, line numbering, header skipping, no-newline markers, line caps.
- `LocalModelRegistryTest` (5): catalogue round trip, malformed JSON, skipped rows, defaults.

Host protocol tests total 9 (`node --test tools/agentdeck-host/server.test.mjs`):
token auth, approval, denial, no-agent inspection mode, missing project,
diff parsing, check results, cancellation, and command templating.

The debug APK also assembles successfully with the same command.

On the `agentdeck` AVD:

- Created a task and let it finish; `adb shell am force-stop com.agentdeck.app` followed by relaunch kept the task, messages, changed files, and checks.
- Started a host retry and force-stopped at about 1.3 s. Relaunch restored it as `Failed` with a `Run interrupted` failure, an activity event, and a system notice.

---

## 2026-10-27 — tool-calling reliability pass

Root causes fixed (unit-tested; release build verified):

1. **Unclosed fences were dropped** — `ToolParser` required `fence.isClosed`, but
   models routinely omit the final ```. It now accepts unclosed blocks, single
   or unquoted arguments, `agentdeck-tool:` and bare `agentdeck-tool` with the
   name on the next line, batched calls in one fence, and JSON
   (`function_calls`) / OpenAI (`tool_calls`) shapes.
2. **XML tool syntax is now parsed** — providers such as dots emit
   `<dots_function_call><agentdeck-tool"><parameter name="query">…</parameter>`
   or `<invoke name="read"><parameter name="path">…</parameter></invoke>`.
   `parseXmlCalls` decodes entities, honours an explicit name, and otherwise
   infers the tool from its parameters (`query`→web_search, `url`→web_fetch,
   `command`→run, `pattern`→grep, `action`→git, `content`→write, `path`→read).
3. **The prompt taught the broken shape** — both system prompts showed examples
   without closing fences. Every example now closes its block and the rules say
   so explicitly.
4. **Malformed attempts ended runs silently** — a tool-looking reply that parses
   to zero calls now triggers a visible notice and one repair turn (max 2).
5. **Local first turns bypassed the agent loop** — new local tasks used the
   legacy `startLocalReply` path whose prompt said tools did not exist. That
   path and prompt were removed; every local session runs `runAgentLoop` with
   `buildLocalTurns` and the real agent prompt.
6. **Needle fallback was unreachable** — the old counter could never reach 2.
   The loop nudges once, then delegates to Needle. Since 2026-10-28 the
   catalogue is session-scoped (five workspace tools for coding, four
   web/memory tools for chat), the confidence floor is 0.1 (the engine's own
   withholding threshold), and `write`/`commit` are filtered from both the
   catalogue and the output.
7. **Approvals were host-gated** — Allow/Decline were disabled unless a host was
   connected, so cloud/local tool approvals timed out. Buttons now enable when
   `pendingAgentApproval(taskId)`; the timeout is 5 minutes with an explicit
   "Approval timed out" notice.
8. **Main-thread blocking** — workspace/shell/git/SSH work runs on
   `Dispatchers.IO`; Needle init and inference run on `Dispatchers.Default`.
9. **Retry problems** — the Needle pre-check is skipped on retry (no duplicate
   commands), failure cards are mode-aware (`retryAgentRun` for cloud/local),
   and retries drop the trailing partial assistant turn.
10. **Truncated writes** — `ToolCall.truncated` marks blocks with no closing
    fence; `ToolExecutor` refuses them and returns an actionable error instead of
    saving a partial file.
11. **JNI UTF-8 and thread safety** — `needle_jni.cpp` decodes standard UTF-8
    to UTF-16 and uses `NewString`, avoiding modified-UTF-8 aborts on
    emoji/4-byte characters. A native mutex plus a Kotlin lock serialize the
    process-global model, `needle_reset()` runs before each one-shot
    completion, and `needle_init` is re-run when the active catalogue changes.

### 2026-10-28 — Needle official verification

- `needle3.cact` (SHA256 `C9D915EC…170C38`) and `libneedle.a`
  (`7AA6DE61…2A2FA0`) match the Hugging Face LFS oids; `needle.h` matches.
  Cactus `android/` builds a different chat engine, so the static
  `libneedle.a` link stays.
- Needle 3 has no retrieval head, reads the whole toolset, is process-global
  and non-thread-safe, and is documented for five tools or fewer.
- On-device CLI tests: a shared 9-tool catalogue routed "find TODO" to
  `git log`; correct calls scored 0.11–0.35, so the old 0.5 gate dropped them.
  See `NEEDLE_INTEGRATION.md` for the full table.
- `NeedleEngine.toolsJson(coding)` now returns the 5-tool coding catalogue or
  the 4-tool chat catalogue; coding sessions try the local model first and use
  Needle only as the fallback. Non-coding sessions may pre-check.

Tests: **126 JVM unit tests pass**, including the scoped-catalogue and
confidence tests in `ToolParserResilienceTest` and the local-retry case in
`CloudTurnsTest`. Release APK: `-Psnapdragon`, signed, installs in place over
`1.0.0-snapdragon`.
- Streamed a deliberately slow cloud reply through the local mock, then force-stopped mid-stream. Relaunch kept the partial reply, set `Needs input`, and appended a `Cloud analysis interrupted` notice.
- Seeded `IN_PROGRESS` demo tasks stayed untouched because they have `activeRun = false`; only runs started by the app are recovered.
- Reinstalling with `adb install -r` preserved the Room database.

The test mock server and this round's test app data were removed after verification; the `agentdeck` AVD remains available.

### Live-session verification

- Started a deliberately slow cloud reply, pressed the Stop control, and confirmed:
  - The partial reply stayed in the conversation.
  - Status changed to `Needs input`.
  - A `Generation stopped` system notice was appended.
- Force-stopped and relaunched after the stop. The task stayed at `Needs input` with no new interruption notice, proving `activeRun` was cleared.
- Started a host run via Request changes, opened Settings, confirmed Disconnect, and verified:
  - The task moved to `Failed`.
  - A `Host disconnected` failure card and system notice appeared.
  - Retry remained available.
- Reconnected, retried the host task, and confirmed it returned to `Review`; the failure card was cleared while the historical system notice remained in the transcript.

### On-device engine verification

- Built llama.cpp for arm64-v8a and x86_64, copied stripped libraries into
  `jniLibs`, and confirmed the JNI library loads (`LLAMA.CPP` tag in Settings).
- Imported `stories15M-q4_0.gguf` (19 MB) through the system file picker; the app
  copied it into `filesDir/models`, showed the real size/context, and loaded it
  with llama.cpp.
- Switched an existing local conversation to the model and sent a prompt.
  Verified streamed tokens, attribution (`stories15M-q4_0 · On this phone`), and
  `Generating on this phone` / `Answered on this phone` activity.
- The 15M base model's output is low quality by nature; this verifies the engine
  path, not model quality. Larger Q4 instruct models are the intended use.

### Host bridge verification

- Ran the host agent on Windows against a temporary git repo whose agent command
  appends to `app.txt`, with one Node check.
- Connected the emulator to `http://10.0.2.2:8787` with the host token from
  Settings → Execution host.
- Created a host task and approved the prompt. Confirmed:
  - host plan, output, and `Live host` activity streamed into the task;
  - the file on disk actually changed (`git diff HEAD` shows the addition);
  - the app showed 1 changed file with the real diff and 1 passed check;
  - the task moved to Review with no failure card.
- Repeated with a second task after fixing two bugs found in the first round:
  a cancelled stale job could overwrite a newer run, and Node 204 responses with
  a body broke the approval POST.
- The composer shows Stop during a host run; cancellation is covered by the
  host protocol test and POSTs `/v1/cancel` to kill the process tree.

### Error handling improvements (current session)

**New architecture** - [`CloudErrorHandling.kt`](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/CloudErrorHandling.kt):
- Sealed class `CloudError` with 7 subtypes: NetworkFailure, HttpError, AuthenticationError, RateLimitError, TimeoutError, InvalidResponse, ValidationFailed
- Each error carries `isRetryable` flag and user-friendly message
- Automatic retry with exponential backoff (1s → 2s → 4s → 10s max)
- Result-based API replacing throw-based errors
- Network connectivity monitoring via `NetworkMonitor` class
- Classification logic maps provider errors to action types

**Modified CloudClient**:
- [`streamChat()`](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/CloudClient.kt#L95-L165) now uses automatic retry loop (up to 3 attempts) and Result type
- [`testConnection()`](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/CloudClient.kt#L385-L454) improved with socket timeout detection
- Proper handling of cancellations vs errors (no exception on user-initiated stop)
- Classification of provider-specific errors from JSON responses
- Backed-off retry with jitter based on error type

**Modified AgentDeckState**:
- [`streamCloudReply()`](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/AgentDeckState.kt#L1428-L1581) updated to classify errors and provide actionable feedback
- Better toast messages based on error classification:
  - Network failure → "Check your internet connection"
  - Auth error → "Check API key in Settings"
  - Rate limit → "Wait X seconds before trying again"
  - Timeout → "You can try again"
- New `Task.retryAttempts` field added for persistent retry tracking

### Crash reporting and Firebase integration (latest session)

**Firebase infrastructure added**:
- [build.gradle.kts](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/build.gradle.kts) with Firebase BOM v33.7.0, Crashlytics, and Analytics dependencies
- [AgentDeckApplication](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/App.kt) class initializing Firebase on startup
- [AndroidManifest.xml](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/AndroidManifest.xml) with Crashlytics collection metadata
- [google-services.template.json](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/google-services.template.json) template for project setup

**Logger utility** - [`Logger.kt`](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/Logger.kt):
- Centralized logging across the app with 5 levels (DEBUG, INFO, WARN, ERROR, FATAL)
- Silent fail-safe design that never causes crashes itself
- Custom key/value tracking for structured context (task IDs, operation types, parameters)
- User identifier support for per-user issue analysis in Crashlytics
- Integration with Firebase Crashlytics for automatic exception reporting

**Enhanced CloudClient logging**:
- Comprehensive error logging throughout HTTP requests ([streamChat()](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/CloudClient.kt), [testConnection()](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/CloudClient.kt))
- Timeout event tracking with duration measurements
- JSON parsing failure diagnostics with sample payloads
- Provider error classification with detailed context mapping
- Connection test result recording with success/failure metrics

**Next steps for Firebase setup**:
1. Create a Firebase project at https://console.firebase.google.com
2. Add Android app with package name `com.agentdeck.app`
3. Download `google-services.json` and place it at `android/app/google-services.json`
4. Replace the template file content with actual values
5. Rebuild and install APK to activate Crashlytics

**Benefits**:
- Real-time crash reporting with stack traces and device info
- Structured error tracking with contextual keys for debugging
- Production-ready monitoring infrastructure
- Foundation for future analytics and usage tracking
EOF

**Benefits:**
- Users receive clear, actionable error messages instead of generic failures
- Transient errors automatically recover without user intervention
- App gracefully degrades when offline or rate-limited
- Debugging easier with structured error types instead of raw exceptions
- Recovery paths documented via `retryAttempts` field

### Markdown verification

- Streamed a Markdown-heavy mock reply through the existing slow SSE mock.
- Confirmed on the `agentdeck` AVD:
  - Heading, paragraph, bullets, and numbered list rendered as blocks.
  - Inline code, bold, italic, and an `https://` link rendered correctly.
  - The fenced Kotlin block showed its language header and clipped long lines.
  - A horizontal swipe inside the code block revealed the end of the long line.
  - The quote bar, rule, and final paragraph rendered without layout clashes.
- Checked logcat after rendering and horizontal scrolling: no app crash or exception.
- Tapped the rendered `docs` link. Chrome opened and Back returned to AgentDeck; no crash. The renderer only annotates `http(s)` targets and wraps the platform URI handler in `runCatching`.
- `MarkdownTest` covers headings/paragraphs, lists, fences (including unclosed), quotes/rules, inline styles/links/escapes, word-internal underscores, safe link schemes, and empty input.

### Cloud retry verification

- Stopped the loopback mock, sent a cloud message, and confirmed the task returned to `Needs input` with a failure system notice and a composer `Retry` strip.
- Restarted the mock, tapped `Retry`, and confirmed:
  - A new cloud reply arrived.
  - No duplicate user message was added.
  - The retry strip disappeared.
  - Force-stop/relaunch kept the new reply and left the failure notice in history.
- Added a temporary fail-once mode to the mock that destroys the SSE connection after three chunks. Verified:
  - The partial Markdown reply stayed in the transcript.
  - The failure notice and retry strip appeared.
  - The retry request ended on the last user turn (the trailing partial assistant message was omitted from the provider payload).
  - The retry completed with a full Markdown reply.

---

## 8. Current runtime behavior details

### Cloud request shape

The app sends a combined system message containing:

- AgentDeck's read-only analysis instructions.
- The GitHub repository snapshot, when present.

Conversation turns include only user and agent messages; system notices are excluded. Recent history is capped to avoid unbounded requests.

### Cloud task lifecycle

1. Create task with `IN_PROGRESS`.
2. Fetch repository context if requested.
3. Stream a cloud reply.
4. Finish with `NEEDS_INPUT` and an activity entry.
5. No changed files or checks are generated.

If context fetching fails, the task continues without context and records a system notice.

If the cloud request fails:

- Any partial reply text is kept and marked finished.
- The task returns to `NEEDS_INPUT` with `activeRun = false`.
- A system notice, a `cloud-error` activity event, and a failed active plan step are recorded.
- The composer shows a `Retry` strip.

Retry reuses the existing conversation and repository snapshot without adding a new user message. It drops trailing partial assistant messages from the provider request so the request ends on the user turn; the partial text remains in the transcript. A successful retry adds a new cloud reply and leaves the earlier failure notice in history.

### Model switching lifecycle

- The current task model changes for subsequent messages.
- Existing messages retain their own model/provider.
- A cloud task's repository context remains attached when temporarily switching to local.
- The local UI explains that the snapshot is not used by the local preview.
- Switching back to cloud restores the cloud analysis UI and uses the retained snapshot.
- A task's stored cloud model override is reused when returning from local.

### GitHub selection behavior

The connector prefers:

- Root README, including extensionless `README`.
- Common manifests.
- Up to eight source files ranked by task terms and path relevance.

It skips:

- Dependencies and build output directories.
- Lock files.
- Minified and map files.
- Binary or unsupported extensions.
- Files above the configured size limit.

The total request digest is bounded. Truncation is surfaced in the context card/sheet.

---

## 9. Important implementation notes

### Cloud model identity

There is one global cloud connection, but each task can have its own cloud model name:

- `cloudConfig.model`: default for new tasks.
- `Task.cloudModel`: override used for that conversation.
- `AgentDeckState.taskModelName(task)` resolves the display name.
- Cloud requests use the task override when present.

Do not make a task switch mutate the global Settings model.

### Message attribution

`Message` contains:

- `modelName`
- `provider`
- `cloud`
- `localPreview`

Unattributed messages are backfilled during state initialization. New local, cloud, and host agent messages must set these fields. Do not label an old message using the task's current model.

### Busy guard

`AgentDeckState.isTaskBusy(taskId)` covers:

- Message delivery.
- Local typing.
- Cloud streaming.
- GitHub context loading.
- Active cloud jobs.

Use it before sending or switching models so a new action cannot cancel an in-flight request and create contradictory attribution.

### Stopping cloud generation

- `isCloudStreaming(taskId)` drives the composer: while true, the Send button is replaced by a red Stop control and the hint explains that the partial reply is kept.
- `stopCloudReply(taskId)` removes the job from `cloudJobs`, clears `cloudStreaming`, cancels the request, sets `activeRun = false`, returns the task to `NEEDS_INPUT`, fails the active plan step, and appends a system notice plus activity event.
- The partial agent message is never deleted.
- `streamCloudReply` captures its own `Job` and only clears `cloudStreaming`/`cloudJobs` when the map still points at that job. This prevents a cancelled run from clobbering a newer run started immediately afterward.
- The streaming ticker also checks job identity, so it cannot append text from an old request to a new one.
- Cloud stop is the only manual stop path. Host runs are stopped by disconnecting the host.

### Retrying cloud generation

- `canRetryCloudReply(taskId)` requires a cloud task, `NEEDS_INPUT`, a last activity of type `cloud-error`, and no live job.
- The composer shows a red `Cloud request failed` strip with a `Retry` action only while that condition holds.
- `retryCloudReply(taskId)` reuses the existing conversation and repository snapshot; it does **not** append a duplicate user message.
- `startCloudReply(..., dropTrailingAssistant = true)` makes `buildCloudTurns` drop trailing agent messages before building the provider request. This avoids sending a truncated assistant turn as the last message. The partial reply remains visible in the transcript for honesty.
- A retry regenerates the answer from the last user turn; it does not resume the partial reply.
- If the cloud endpoint/key is no longer configured, retry opens the cloud setup sheet instead of sending.
- Once the retry records a new `cloud` activity, the retry strip disappears. A successful retry leaves the failure notice in history.

### Host disconnect

`disconnectHost()` marks every `IN_PROGRESS` host task with `activeRun = true` as `FAILED`, clears `activeRun`, fails the active plan step, and adds:

- A `Host disconnected` failure card.
- A system notice in the conversation.
- An activity event.

The task is retryable after reconnecting. `retryTask()` clears the old failure before starting a new host run. `reconnectHost()` does not auto-resume anything; execution requires an explicit user retry.

The host's `connected` flag is runtime state and is not persisted; a cold start begins disconnected and a background ping reconnects automatically when a host is configured. Tasks and failures still persist.

### Markdown replies

`ui/Markdown.kt` contains a pure parser and a Compose renderer. It is intentionally a subset, not a full CommonMark implementation:

- Blocks: paragraphs, ATX headings, flat bullet/numbered lists, quotes, rules, and fenced code.
- Inline: code, bold, italic, and `[label](url)` links.
- Unknown syntax is shown literally.
- Escaped punctuation is supported.
- Underscores inside words are not treated as italics, so identifiers like `snake_case` survive.

Integration is cloud-only in `MessageBlock`: user, system, host, and local messages remain plain `Text`. This keeps host/local copy predictable and limits untrusted model formatting to the provider that produces Markdown in practice.

Safety choices:

- No WebView, HTML, images, or remote content.
- Code fences are displayed as text and can scroll horizontally; nothing is executed.
- Only `http://` and `https://` targets receive a `LinkAnnotation`; other schemes remain non-clickable text.
- Links use the platform URI handler, which opens an external browser.

Streaming:

- The renderer parses the current partial text on each update with `remember(text)`.
- An unclosed fence consumes the remaining text, so a partially streamed code block still renders instead of flashing raw backticks.
- No animations are used, keeping streaming updates cheap and compatible with reduced motion.

Current parser limitations: flat lists only, no nested lists, tables, images, setext headings, task lists, or reference-style links.

### Compose threading

Cloud networking runs on `Dispatchers.IO`. Streaming deltas are buffered off the main thread; a Main-thread ticker updates Compose state. Keep state mutations on the ViewModel/main coroutine context.

### OkHttp cancellation

Both network clients register coroutine completion handlers that cancel the HTTP call. Preserve this so reset or ViewModel cleanup does not leave long blocking reads running.

### AGP 9

The project uses AGP 9 built-in Kotlin:

- Plugins:
  - `com.android.application`
  - `org.jetbrains.kotlin.plugin.compose`
  - `com.google.devtools.ksp` (`2.3.12`)
- Do **not** add `org.jetbrains.kotlin.android`; it conflicts with built-in Kotlin.
- KSP 2.3.1+ is required for AGP 9 built-in Kotlin. Do not downgrade KSP without also changing the Kotlin setup.
- Room schemas are exported by the `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` block into `app/schemas/`.
- `compileSdk = 37`.
- Gradle wrapper is checked into `android/gradle`.

### Git workspace caveat

The Git repository root is:

```text
C:\Users\Rahul\Downloads\qoder
```

`AgentDeck2.0` is currently untracked there, and the repository also contains unrelated project history. Do not run broad `git add -A` commands without inspecting what will be staged. No AgentDeck commit was requested.

---

## 10. Recommended next milestones

### A. Room persistence — completed

Implemented and verified. Persisted data:

- Tasks and ordered messages.
- Activity events.
- Changed files/diffs, checks, decisions, plans, permission/failure state, and repository snapshots.
- Workspace settings (active/default project, default model).
- Per-task cloud model override.

Design decisions:

- One `tasks` row per task: scalar columns are queryable; nested lists live in an ordered JSON payload. This keeps schema version 1 small and lets additive fields decode safely.
- The whole workspace is saved as one transaction when its snapshot changes (about once per second). This is simple and crash-consistent at the app's scale.
- API keys and GitHub tokens stay in Keystore; no secrets enter Room.
- `Task.activeRun` distinguishes a live request from a persisted snapshot. On restore, only active runs are reconciled.
- Auto Backup is disabled. Room schema v1 is exported under `app/schemas/`.

Remaining follow-ups:

- Add explicit Room migrations before changing entities; do not rely on destructive fallback.
- Improve snapshot writes if task volume grows (dirty-task or message-row writes).
- Local model load/import state is intentionally ephemeral and resets on restart.
- Navigation position is not restored; a cold start returns to Home.

### B. Host bridge and apply-with-host

Define a real protocol between the phone and an execution host:

- Pairing/authentication.
- Task submission.
- Streaming status/messages.
- Diff and check results.
- Approval and request-changes commands.
- Reconnect and queued-action semantics.

A useful feature is an explicit action on a cloud analysis:

- Send the conversation/proposed patch to the host.
- Create or attach a host execution task.
- Keep the cloud analysis history intact.

Do not let the cloud model claim this happened until the host protocol exists.

### C. On-device inference

The agreed future path is llama.cpp JNI plus GGUF import.

After the engine exists:

- Replace simulated local replies. — completed; llama.cpp generation streams on-device
- Add real token/latency/memory state.
- Wire the same GitHub context into local inference.
- Keep execution separate from inference.

### D. Reply rendering — completed

Implemented in [Markdown.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/Markdown.kt) and used for cloud agent messages:

- Paragraphs and headings.
- Flat bullet/numbered lists.
- Quotes and rules.
- Fenced code with language header and horizontal scrolling.
- Inline code, bold, italic, and safe `http(s)` links.
- Pure parser unit tests; renderer verified on the AVD with a streaming mock.

Follow-ups: nested lists, tables, images, syntax highlighting, a copy-code action, and a benchmark for very long streamed replies.

### E. Multi-endpoint cloud management

Current design has one cloud endpoint/key. Later:

- Saved provider profiles.
- Multiple models per provider.
- Per-task endpoint selection.
- Clear indication of which endpoint receives data.

### F. Control and error UX

Cloud stop and one-tap retry are implemented. Remaining:

- Retry GitHub context (currently available from the context card).
- Clear provider/model attribution in task cards.
- Better long-model-name handling.

---

## 11. Known rough edges

- Host tasks do not reply to follow-up messages during or after a run.
- Applies carry full new file contents (no partial-diff application); deleting a
  file is not supported by the block format yet.
- Apply diffs compare against the task's context snapshot; files the model
  changes outside the snapshot always display as added.
- CI polling stops after roughly 7 minutes; use Refresh on the Checks tab after
  that.
- Host tasks cannot switch to cloud/local mid-conversation by design in the current slice.
- Local inference does not read GitHub context.
- Only one global cloud endpoint/key.
- GitHub context selection is heuristic and may miss the ideal file set.
- Public GitHub requests are rate-limited to roughly 60/hour without a token.
- Markdown support is a small subset: flat lists only; no tables, images, nested lists, setext headings, or reference-style links.
- Markdown links open the external browser; there is no in-app browser, link preview, or confirmation prompt.
- Markdown is parsed on every streaming update; very long replies have not been benchmarked yet.
- Only the most recent cloud error is retryable. Any later activity (for example a model switch) hides the retry strip, though the failure notice remains in the transcript.
- Cloud retry regenerates from the last user turn; it does not resume a partial reply. The old partial stays visible and can duplicate content in the new answer.
- Stop is available for cloud streaming, on-device generation, and live host runs (cancel).
- Host connection state is runtime-only; a cold start begins disconnected and reconnects in the background when a host is configured.
- Workspace snapshots are written about once per second; an abrupt process kill can lose up to roughly the last second of streamed text. Completed state transitions are normally captured within that window.
- Room schema v1 has no migration history yet; future entity changes require explicit migrations.
- Task content and repository snapshots live in app-private Room storage, not Keystore-encrypted storage. Auto Backup is disabled and secrets remain in Keystore.
- Local model load/import state is intentionally ephemeral and resets on restart.
- Navigation position is not restored; a cold start returns to Home.
- Test coverage is currently limited to the persistence codec; there is no CI.
- The UI is light-theme only.
- The debug APK is unsigned.
- The app has no real notifications, background sync, or account system.
- Accessibility has not been systematically audited.
- The repository contains unrelated project files and history.

---

## 12. Suggested first actions for the next agent

1. Read this file and [AGENTDECK_MVP.md](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/AGENTDECK_MVP.md).
2. Build the current APK to confirm the environment:
   ```bash
   cd /c/Users/Rahul/Downloads/qoder/AgentDeck2.0/android
   ./gradlew :app:assembleDebug
   ```
3. Inspect:
   - [AgentDeckState.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/AgentDeckState.kt)
   - [Models.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/Models.kt)
   - [TaskScreen.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/TaskScreen.kt)
   - [Sheets.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/Sheets.kt)
4. Choose the next milestone deliberately:
   - **Host bridge** is the key step toward real execution.
   - **llama.cpp** is the core on-device inference step.
   - Room persistence is complete; extend it only with explicit migrations.
5. Preserve the product boundaries in section 4.
6. Do not commit or push unless the user explicitly asks.

---

## 13. Useful source entry points

Search these names when continuing:

- `AgentDeckViewModel`
- `AgentDeckState`
- `createTask()`
- `sendMessage()`
- `startCloudAnalysis()`
- `startCloudReply()`
- `streamCloudReply()`
- `fetchRepoContext()`
- `switchTaskToCloud()`
- `switchTaskToLocal()`
- `taskModelName()`
- `isTaskBusy()`
- `restoreWorkspace()`
- `startPersistenceLoop()`
- `recoverInterruptedTasks()`
- `TaskStore`
- `TaskCodec`
- `Task.activeRun`
- `TaskModelsSheet()`
- `CloudSetupSheet()`
- `GitHubSetupSheet()`
- `RepoContextSheet()`
- `ComposerBar()`
- `MessageBlock()`
- `ActivityCard()`
- `SecureStore`
- `AppConfig`
- `CloudClient`
- `GitHubClient`

---

## 14. Minimal local mock for cloud testing

Save this outside the repository, for example in the system temp directory, and run it with Node:

```js
const http = require('http');

const KEY = 'test-key-123'; // intentionally fake, local testing only

http.createServer((req, res) => {
  let raw = '';
  req.on('data', (chunk) => { raw += chunk; });
  req.on('end', () => {
    let body = {};
    try { body = JSON.parse(raw); } catch (_) {}

    if ((req.headers.authorization || '') !== `Bearer ${KEY}`) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'Invalid API key' } }));
      return;
    }

    if (req.url !== '/v1/chat/completions') {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end('{}');
      return;
    }

    if (!body.stream) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        model: body.model || 'mock-gpt',
        choices: [{ message: { role: 'assistant', content: 'OK' } }]
      }));
      return;
    }

    const answer = `Mock reply from ${body.model}. This endpoint verifies transport only; no files were changed or run.`;
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache'
    });

    const chunks = answer.match(/[\s\S]{1,20}/g) || [answer];
    let index = 0;
    const timer = setInterval(() => {
      if (index >= chunks.length) {
        res.write('data: [DONE]\n\n');
        clearInterval(timer);
        res.end();
        return;
      }
      res.write('data: ' + JSON.stringify({
        choices: [{ delta: { content: chunks[index] } }]
      }) + '\n\n');
      index += 1;
    }, 25);
  });
}).listen(8787, '127.0.0.1', () => console.log('AgentDeck mock listening on 8787'));
```

In the emulator configure:

```text
Base URL: http://10.0.2.2:8787/v1
Model:    mock-gpt
API key:  test-key-123
```

The app sends the requested model in each request, so changing a task's model to `mock-gpt-2` should make the mock reply name that model. This is the quickest way to verify per-conversation model switching without a real provider key.
