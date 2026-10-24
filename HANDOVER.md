# AgentDeck — engineering handover

**Date:** 2026-10-24  
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
  - Sheets for projects, models, approvals, requests, confirmations, demo info
- Local-model UI states and simulated on-device replies.
- Host connection/offline states and simulated host execution.
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
  - Workspace settings (active project, default project, default model, demo-notice state).
  - A hydration gate prevents demo data from flashing before saved tasks load.
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
- Unit tests for persistence, Markdown parsing, cloud URL validation, provider-turn building, error classification, and backup codec (36 tests).
- Debug APK builds successfully.

### Still simulated

- Host execution, diffs, checks, approvals, and host agent replies.
- Local/on-device inference and local model loading/importing.
- The local model does **not** use the GitHub connector yet.
- The web prototype's runtime behavior remains a prototype.

### Still missing

- A real host bridge or execution protocol.
- A real llama.cpp/GGUF inference engine.
- Multiple saved cloud endpoints/models.
- A "apply this analysis with the host" workflow.
- In-app update mechanism and Play Store listing.
- Automatic offline send queue (cloud sends fail fast with a retry path instead).
- Integration/E2E tests on CI; unit coverage now includes error mapping and codec retry persistence.

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

**Tests**
- 36 JVM tests pass (`./gradlew :app:testDebugUnitTest`), including new
  `CloudErrorHandlingTest` (11 cases) and codec retry-attempt persistence.

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
- [SeedData.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/SeedData.kt)
  - Demo workspace, host, projects, models, and tasks.
- [AgentDeckState.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/data/AgentDeckState.kt)
  - Central Compose state, navigation, task creation, messaging, model switching, cloud analysis, GitHub context, and simulated host behavior.

Key state concepts:

- `ModelProvider`: `HOST`, `LOCAL`, `CLOUD`.
- `CLOUD_MODEL_ID`: a single dynamic cloud row appended to the seeded models.
- `Task.cloudModel`: per-conversation cloud model override.
- `Message.modelName` and `Message.provider`: per-reply attribution.
- `Task.repoContext`: read-only GitHub snapshot attached to a task.
- `Task.analysisOnly`: true when the current mode is cloud analysis.
- `isTaskBusy(taskId)`: guards sending and model switching during cloud/local/context work.
- `repoCache`: in-memory 10-minute GitHub context cache.
- `Task.activeRun`: true only while a request started by this app is live. Seeded demo snapshots can be `IN_PROGRESS` without an active run and are intentionally left alone on restore.

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
  - `resetDemo()` clears and reseeds the database.
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
- Local replies show the model, `On this phone`, and `Simulated`.
- Host replies show the model without a cloud/local chip.
- The activity card shows `Cloud`, `Mixed`, or `Simulated` based on actual history.
- The task composer's model row contains the `Change` affordance.
- The task model sheet is in [Sheets.kt](file:///C:/Users/Rahul/Downloads/qoder/AgentDeck2.0/android/app/src/main/java/com/agentdeck/app/ui/Sheets.kt) as `TaskModelsSheet`.
- The app column is capped at 430 dp on wide screens.

---

## 4. Product and security invariants

Preserve these unless the user explicitly changes direction.

1. **Cloud inference is not execution.**
   - Cloud tasks can analyze, plan, and propose patches.
   - They must never claim to write files, run commands, run tests, merge, or deploy.
2. **Execution remains host-gated.**
   - File changes, checks, and approvals are associated with the execution host.
   - The current host is simulated.
3. **Local inference is still simulated.**
   - Do not present local replies as real model output.
4. **No gateway for personal BYOK.**
   - Requests go directly from the phone to the endpoint entered by the user.
5. **Never store a key in source or the APK.**
   - Runtime BYOK only.
   - The current debug APK contains no API key or GitHub token.
6. **Secrets are Keystore-encrypted.**
   - SharedPreferences contain ciphertext only.
   - API-key and token fields use password IME behavior.
7. **GitHub token is never sent to the cloud model.**
   - Only repository content selected for the task is sent to the configured cloud endpoint.
8. **Repository content is untrusted data.**
   - The cloud system prompt tells the model not to treat repository text as instructions.
9. **Do not log prompts, responses, keys, or tokens.**
10. **Remote endpoints require HTTPS.**
    - Cleartext is allowed only for `10.0.2.2`, `localhost`, and `127.0.0.1` for local testing.
11. **Do not introduce a WebView wrapper.**
12. **Do not infer that "keep it as is" means a hybrid architecture.** Confirm native versus wrapper when ambiguous.

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
   - A local simulated reply appears.
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

JVM tests currently total 36 and all pass:

- `TaskCodecTest` (6): task/message round-trip, defaults, ordering, damaged payloads, settings, retry attempts.
- `MarkdownTest` (8): block/inline parsing, fences, escapes, link safety, empty input.
- `CloudClientTest` (7): URL scheme/loopback rules, credentials/query rejection, endpoint construction, provider labels.
- `CloudTurnsTest` (5): system exclusion, turn/history/character caps, and retry trailing-assistant removal.
- `CloudErrorHandlingTest` (11): retryability, retry-button policy, backoff growth/cap, and failure labels.

The debug APK also assembles successfully with the same command.

On the `agentdeck` AVD:

- Created a task and let it finish; `adb shell am force-stop com.agentdeck.app` followed by relaunch kept the task, messages, changed files, and checks.
- Started a host retry and force-stopped at about 1.3 s. Relaunch restored it as `Failed` with a `Run interrupted` failure, an activity event, and a system notice.
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

Seed messages are attributed during state initialization. New local, cloud, and host agent messages must set these fields. Do not label an old message using the task's current model.

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

The simulated host's `connected` flag is runtime state and is not persisted; a cold start assumes the demo host is connected again. Tasks and failures still persist.

### Markdown replies

`ui/Markdown.kt` contains a pure parser and a Compose renderer. It is intentionally a subset, not a full CommonMark implementation:

- Blocks: paragraphs, ATX headings, flat bullet/numbered lists, quotes, rules, and fenced code.
- Inline: code, bold, italic, and `[label](url)` links.
- Unknown syntax is shown literally.
- Escaped punctuation is supported.
- Underscores inside words are not treated as italics, so identifiers like `snake_case` survive.

Integration is cloud-only in `MessageBlock`: user, system, host, and local messages remain plain `Text`. This keeps simulated-host/local copy predictable and limits untrusted model formatting to the provider that produces Markdown in practice.

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
- Workspace settings (active/default project, default model, demo notice).
- Per-task cloud model override.

Design decisions:

- One `tasks` row per task: scalar columns are queryable; nested lists live in an ordered JSON payload. This keeps schema version 1 small and lets additive fields decode safely.
- The whole workspace is saved as one transaction when its snapshot changes (about once per second). This is simple and crash-consistent at the app's scale.
- API keys and GitHub tokens stay in Keystore; no secrets enter Room.
- `Task.activeRun` distinguishes a live request from a seeded `IN_PROGRESS` snapshot. On restore, only active runs are reconciled.
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

- Replace simulated local replies.
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

- Host tasks do not generate simulated replies for follow-up messages.
- Host tasks cannot switch to cloud/local mid-conversation by design in the current slice.
- Local inference is still simulated.
- Local inference does not read GitHub context.
- Only one global cloud endpoint/key.
- GitHub context selection is heuristic and may miss the ideal file set.
- Public GitHub requests are rate-limited to roughly 60/hour without a token.
- Markdown support is a small subset: flat lists only; no tables, images, nested lists, setext headings, or reference-style links.
- Markdown links open the external browser; there is no in-app browser, link preview, or confirmation prompt.
- Markdown is parsed on every streaming update; very long replies have not been benchmarked yet.
- Only the most recent cloud error is retryable. Any later activity (for example a model switch) hides the retry strip, though the failure notice remains in the transcript.
- Cloud retry regenerates from the last user turn; it does not resume a partial reply. The old partial stays visible and can duplicate content in the new answer.
- Stop is available only for cloud streaming. Simulated host runs cannot be stopped manually except by disconnecting the host.
- Host connection state is runtime-only in the demo; a cold start assumes the host is connected again even if it was disconnected before the restart.
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
