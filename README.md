# AgentDeck

A native Android agent harness and coding workspace. Two modes: **Chat** — a
general assistant with real web search, webpage retrieval, HTTP APIs, and
persistent memory — and **Coding** — an agent that plans, reads and edits
files in an on-device workspace, runs shell commands and git, and hands you a
real diff to review. Both run on cloud models via your own API key or
genuinely on-device via llama.cpp, stream replies, use reusable instructions,
and ask your approval for consequential actions — straight from the phone, no
host computer required.

<table>
  <tr>
    <td><img src="preview-native/home.png" width="200" alt="Home" /></td>
    <td><img src="preview-native/task-review.png" width="200" alt="Task review" /></td>
    <td><img src="preview-native/diff.png" width="200" alt="Diff viewer" /></td>
    <td><img src="preview-native/checks.png" width="200" alt="Checks" /></td>
    <td><img src="preview-native/settings.png" width="200" alt="Settings" /></td>
  </tr>
</table>

## What it does

- **Two modes** — Home opens on a **Chat / Coding** launcher. Chat: general
  assistant with web search, page retrieval, HTTP APIs, and memory. Coding:
  workspaces, file edits, shell commands, git, and review. Both support cloud
  BYOK and on-device models, streaming, reusable instructions, and approvals.
- **Tasks and projects** — per-project task lists, status filters, search, and
  a task detail view with Chat / Changes / Checks tabs (chat tasks show the
  Chat tab only). The workspace starts empty; nothing is seeded.
- **Cloud BYOK analysis** — talk to any OpenAI-compatible endpoint with
  streaming replies, Markdown rendering, and per-message model attribution.
  Save several APIs (OpenRouter, Groq, a local gateway…) and switch between
  them; fetch each endpoint's model list and save model ids for one-tap reuse.
- **Mid-conversation model switching** — change the cloud model or switch to a
  local model without starting a new task. Selecting an unloaded local model
  loads it automatically; only one local model stays in memory at a time.
- **GitHub context** — attach a read-only snapshot (README, manifests, relevant
  sources) to a cloud analysis task. Public repos work without a token.
- **Apply to GitHub** — cloud replies propose files in structured blocks; one
  tap commits them to a branch and opens a pull request with your write token.
  CI checks on the pull request report into the Checks tab.
- **On-device agent** — with "Run as agent", a cloud model works directly in
  the project's on-device workspace: it lists and reads real files, edits
  them, runs Android system shell commands and git (JGit), and iterates on
  errors. Reads run automatically; every file write, shell command, and git
  commit requires your explicit approval. The workspace imports from GitHub
  (codeload zip) and ends each run as a real diff for review. The shell is
  Android's toybox — there is no Node/Python/Gradle toolchain on the device,
  so test runs belong to CI or your computer. Local models run the same agent
  loop, with Needle as a grammar-constrained fallback for tool selection. Each
  local session declares a small, session-scoped catalogue (five workspace
  tools for coding, four web/memory tools for chat); Needle 3 has no retrieval
  head, so a large catalogue degrades routing. The
  `agentdeck-tool` protocol tolerates missing closing fences, single-quoted or
  unquoted arguments, and JSON / OpenAI-style calls; an unparseable attempt
  gets one repair turn instead of silently ending the run, and a write whose
  block was cut off is refused rather than saved partially.
- **Phone control** — the agent can drive the device through public Android
  intents and system services: read battery, network, storage, volume and
  uptime (`device_status`), list and open apps (`list_apps`, `open_app`), open
  26 settings panels (`open_settings`), open a URL, share text, prefill the
  dialer (`dial`) or Messages (`sms_compose`), set an alarm or timer, insert a
  calendar event, and change stream volume. `dial`, `sms_compose` and
  `share_text` require approval and stop at the system UI — the agent never
  places a call or sends a message by itself. No dangerous runtime permissions
  are requested. The full capability matrix, including what Android does not
  allow an ordinary app to do, is in [PHONE_CONTROL.md](PHONE_CONTROL.md).
- **Real toolchains via Termux** — the on-device shell covers toybox
  utilities natively; enable the Termux bridge in Settings and non-toybox
  commands (`node`, `python`, `gradle`…) execute in Termux's sshd over
  loopback SSH against the same shared workspace, so tests and toolchains
  genuinely run on the phone. llama.cpp is pinned to upstream `b11966`
  (current Hexagon skels for Snapdragon NPU).
- **Real host execution (optional)** — connect the bundled host agent on your
  computer to run your own agent CLI in the project folder, with approvals,
  streamed output, real `git` diffs, and checks. The app is fully functional
  without it; host tasks simply need a connected host.
- **On-device inference** — import a GGUF model and chat offline with a bundled
  llama.cpp runtime. Token streaming runs on the phone; nothing is sent to a
  server.
- **Review flow** — diffs, check results, approve or request changes.
- **Persistence** — tasks, conversations, plans, checks, diffs, and settings are
  stored on-device in Room and survive restarts, rotation, and interrupted runs.
- **Resilience** — structured cloud errors, automatic retry with backoff, an
  offline indicator, stop/retry for in-flight replies, and crash reporting.
- **Privacy controls** — optional biometric/PIN app lock and portable JSON
  workspace backup/restore that never includes API keys.

## Security and privacy

- API keys and GitHub tokens are encrypted with AES-256-GCM using a key held by
  the Android Keystore. Only ciphertext is written to SharedPreferences.
- Requests go directly from the phone to the endpoint you configure. There is
  no AgentDeck relay, gateway, or account.
- Host execution is gated by a per-host bearer token and an approval prompt on
  every run. The host never accepts arbitrary commands from the phone: the
  agent CLI and checks come from the host's own config file.
- GitHub reads are read-only (contents and metadata). Writes happen only when
  you explicitly tap Apply, only with a token that has Contents + Pull requests
  write access, and only as a branch and pull request — never a direct push to
  your default branch. The token is never sent to the model.
- Repository content is treated as untrusted data by the cloud system prompt.
- Cloud analysis never edits files, runs commands, or claims to. It proposes
  files; applying them is an explicit user action (a pull request on GitHub, or
  a run on the optional execution host).
- Android Auto Backup is disabled; the Room database stays on the device.

## Requirements

- Android Studio or a JDK 17+ toolchain
- Android SDK with compileSdk 37 (minSdk 26)
- Git Bash / a POSIX shell for the commands below

## Build and run

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.agentdeck.app/.MainActivity
```

Run the unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

### Signed release

Create `android/keystore.properties` (gitignored) pointing at your keystore:

```properties
storeFile=app/agentdeck-release.jks
storePassword=…
keyAlias=agentdeck
keyPassword=…
```

Then:

```bash
./gradlew :app:assembleRelease   # signed APK
./gradlew :app:bundleRelease     # Play Store AAB
```

Release builds enable R8 minification and resource shrinking. Without a
keystore the release build is unsigned but still minified.

### Host execution (optional)

The app runs tasks on your computer through a zero-dependency Node agent:

```bash
cd tools/agentdeck-host
node server.mjs --project "C:/work/orbit-api"
```

On first run it creates `agentdeck-host.config.json`, generates a token, and
prints both. Add your agent CLI and checks to the config, for example:

```json
"agentCommand": ["aider", "--message", "{prompt}", "--yes"],
"checks": [
  { "kind": "test", "name": "Unit tests", "command": ["pnpm", "test"] }
]
```

Then in AgentDeck: **Settings → Execution host → Set up host**, enter
`http://<computer-IP>:8787` (run the host with `--host 0.0.0.0` for LAN) and the
token. For http:// LAN hosts, enable **Allow unencrypted LAN**; prefer HTTPS via
a tunnel on untrusted networks. See
[tools/agentdeck-host/README.md](tools/agentdeck-host/README.md) for the full
protocol and security notes.

### On-device models

The app bundles llama.cpp. Import a GGUF file from **Settings → On-device
models → Import**, then **Load** it. Start a task with that model, or switch an
existing local conversation to it with **Change**.

Two APKs are available:

```bash
./gradlew :app:assembleDebug                 # standard: CPU, arm64 + x86_64
./gradlew :app:assembleDebug -Psnapdragon    # Snapdragon GPU/NPU, arm64
```

- **Standard** includes llama.cpp's runtime-dispatch CPU variants
  (`armv8.0` → `armv9.2`) and picks the best one for the phone at startup.
- **Snapdragon** adds Adreno OpenCL and Hexagon NPU backends, selectable in
  Settings → Acceleration. It requires OpenCL 3.0 and HTP v73+; on older
  Snapdragon phones the native engine is unavailable (the rest of the app still
  runs). Verified decode on a Snapdragon 888 with the standard APK: ~18 tokens/s
  for Llama 3.2 1B Q4_0. On an iQOO 13 (Snapdragon 8 Elite, HTP v79), 64-token
  decode of the same model: **56–61 tokens/s on the NPU**, 42–45 on CPU, ~16 on
  the Adreno GPU; Auto selects the NPU. The GPU is kept selectable but decode is
  slower than CPU for this small 1B model.
- Imported models are copied into app storage; none ship with the APK.
  Prefer Q4_K_M; 1–2B models need roughly 1–2 GB of RAM.
- Native sets live in `android/app/src/standard/jniLibs` and
  `android/app/src/snapdragon/jniLibs`. Rebuild them with
  `tools/fetch-llama-android.sh` (official release packages) and
  `tools/build-llama-x86_64.sh` (emulator).

### Optional Firebase crash reporting

Create a Firebase Android app for `com.agentdeck.app`, download
`google-services.json` into `android/app/`, and rebuild. The Gradle plugins are
applied only when that file exists, so fresh clones build without Firebase.
`android/google-services.template.json` documents the expected fields.

## Architecture

```
android/app/src/main/java/com/agentdeck/app/
├── App.kt                  Application bootstrap (Firebase)
├── MainActivity.kt         FragmentActivity + Compose host
├── data/
│   ├── AgentDeckState.kt   Central state, navigation, jobs, app lock
│   ├── AgentTools.kt       Tool protocol, prompts, approvals, executor
│   ├── PhoneTools.kt       Public-intent phone control + argument parsing
│   ├── LocalEngine.kt      JNI bridge to llama.cpp
│   ├── LocalModelRegistry.kt  Imported GGUF catalogue
│   ├── Models.kt           Tasks, messages, plans, checks, config
│   ├── CloudClient.kt      OpenAI-compatible SSE client + retries
│   ├── CloudTurns.kt       Provider turn builder (history limits)
│   ├── CloudErrorHandling.kt  Error mapping + NetworkMonitor
│   ├── GitHubClient.kt     GitHub context fetcher + apply (branch, PR, CI)
│   ├── SecureStore.kt      Keystore AES-GCM secret storage
│   ├── Logger.kt           Logcat + Crashlytics
│   └── local/              Room entities, DAO, codec, backup
├── ui/                     Compose screens, sheets, Markdown renderer
├── cpp/                    llama_jni.cpp + llama/ggml headers
├── standard/jniLibs/       CPU runtime-dispatch arm64 libraries
├── snapdragon/jniLibs/     Adreno OpenCL + Hexagon NPU arm64 libraries
└── main/jniLibs/x86_64/    Emulator libraries
```

State flows one way: `AgentDeckState` mutates an immutable `AgentDeckData`
snapshot, a persistence loop writes changed snapshots to Room about once per
second, and interrupted runs are reconciled on restore instead of staying stuck.

```
tools/agentdeck-host/
├── server.mjs                      Zero-dependency host agent + protocol
├── server.test.mjs                 Protocol tests (node --test)
├── agentdeck-host.config.example.json
└── README.md                       Setup, protocol, security notes
```

## Tests

138 JVM unit tests cover:

- Room codec round-trips, damaged payloads, and retry-attempt persistence
- Markdown parsing (blocks, inline styles, link safety, fences)
- Cloud URL validation and endpoint construction
- Provider turn building and retry history trimming
- Cloud error classification, retryability, and backoff
- Workspace backup format, validation, and skipped rows
- Host URL policy, endpoint building, and unified-diff parsing
- Proposal block parsing (file fences, path validation, last-write-wins) and
  line diffs for applied files
- Task provider classification, including tasks whose local model row is
  missing (transcript evidence fallback)
- Cloud profile codec round-trips and OpenAI-style `/models` list parsing
- Agent tool-protocol parsing, workspace path confinement, and unified-diff
  file splitting
- Resilient tool calls (unclosed fences, quoting variants, batched calls,
  JSON/OpenAI shapes, XML invoke-style calls, truncated writes, engine-scored
  Needle confidence and scoped catalogues) and retry history trimming for local
  models
- Imported local model catalogue encoding and tolerant decoding
- Phone argument parsing (number normalisation, 24-hour clocks, durations,
  calendar starts, volume mapping), the 26 settings panels, the phone approval
  policy, and phone calls parsed from fenced and JSON shapes

The host agent has 9 protocol tests (auth, approval, denial, inspection mode,
diff collection, checks, cancellation, diff parsing):

```bash
node --test tools/agentdeck-host/server.test.mjs
```

An instrumentation smoke test (`AppSmokeTest`) launches the app on a device,
waits for hydration, and navigates to Settings. Run it against one device:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

`LocalEngineDeviceTest` exercises the native engine and each accelerator. Push a
GGUF, copy it into app storage, then run the test. It launches `MainActivity` and
keeps the screen on so phone ROMs do not freeze the process mid-load:

```bash
adb push model.gguf /data/local/tmp/model.gguf
adb shell run-as com.agentdeck.app cp /data/local/tmp/model.gguf files/test.gguf
adb shell am instrument -w \
  -e class com.agentdeck.app.LocalEngineDeviceTest#generateOnRequestedBackend \
  -e modelPath /data/user/0/com.agentdeck.app/files/test.gguf \
  -e accelerator npu   # npu, gpu, cpu, or auto
  com.agentdeck.app.test/androidx.test.runner.AndroidJUnitRunner
```

On aggressive ROMs (OriginOS/MIUI) also keep the display awake for the run, e.g.
`adb shell svc power stayon true`, and restore it afterwards.

CI runs the JVM tests, the host tests, and assembles the debug APK on every push
to the default branch.

## Known limitations

- Execution without a host means GitHub applies: a branch, a pull request, and
  whatever CI the repository already runs. Applying proposals needs a GitHub
  fine-grained token with Contents + Pull requests write access; without a
  write token the app stays read-only. Host-agent tasks additionally require a
  connected, approved host.
- On-device quality depends entirely on the GGUF you import. Small or base
  models produce limited text; no model ships with the app.
- Accelerated inference is only in the optional Snapdragon APK and depends on
  the phone's OpenCL/HTP generation; the standard APK is CPU-only. One model
  can be loaded at a time, and large models can be killed on low-RAM devices.
- Cloud sends require connectivity. There is no automatic offline queue; the
  failure path and retry action are explicit.
- In-app updates are not implemented; distribution signing is ready.

More detail — invariants, verification evidence, and continuation notes — is in
[HANDOVER.md](HANDOVER.md). The original product brief is
[AGENTDECK_MVP.md](AGENTDECK_MVP.md).
