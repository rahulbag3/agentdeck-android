# AgentDeck

A native Android control center for coding agents. Create tasks, chat with an
agent, inspect proposed changes, review checks, and approve or request changes —
with cloud analysis over your own API key, straight from the phone.

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

- **Tasks and projects** — seeded workspace, per-project task lists, status
  filters, search, and a task detail view with Chat / Changes / Checks tabs.
- **Cloud BYOK analysis** — talk to any OpenAI-compatible endpoint with
  streaming replies, Markdown rendering, and per-message model attribution.
- **Mid-conversation model switching** — change the cloud model or switch to a
  ready local model without starting a new task.
- **GitHub context** — attach a read-only snapshot (README, manifests, relevant
  sources) to a cloud analysis task. Public repos work without a token.
- **Review flow** — diffs, check results, approve or request changes; host
  execution is represented and clearly labelled as simulated.
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
- GitHub access is read-only (contents and metadata). The token is never sent to
  the model.
- Repository content is treated as untrusted data by the cloud system prompt.
- Cloud analysis never edits files, runs commands, or claims to. File changes,
  checks, and approvals belong to the (simulated) execution host.
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
│   ├── Models.kt           Tasks, messages, plans, checks, config
│   ├── SeedData.kt         Demo workspace
│   ├── CloudClient.kt      OpenAI-compatible SSE client + retries
│   ├── CloudTurns.kt       Provider turn builder (history limits)
│   ├── CloudErrorHandling.kt  Error mapping + NetworkMonitor
│   ├── GitHubClient.kt     Read-only GitHub context fetcher
│   ├── SecureStore.kt      Keystore AES-GCM secret storage
│   ├── Logger.kt           Logcat + Crashlytics
│   └── local/              Room entities, DAO, codec, backup
└── ui/                     Compose screens, sheets, Markdown renderer
```

State flows one way: `AgentDeckState` mutates an immutable `AgentDeckData`
snapshot, a persistence loop writes changed snapshots to Room about once per
second, and interrupted runs are reconciled on restore instead of staying stuck.

## Tests

45 JVM unit tests cover:

- Room codec round-trips, damaged payloads, and retry-attempt persistence
- Markdown parsing (blocks, inline styles, link safety, fences)
- Cloud URL validation and endpoint construction
- Provider turn building and retry history trimming
- Cloud error classification, retryability, and backoff
- Workspace backup format, validation, and skipped rows

An instrumentation smoke test (`AppSmokeTest`) launches the app on a device,
waits for hydration, and navigates to Settings. Run it against one device:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

CI runs unit tests and assembles the debug APK on every push to the default
branch.

## Known limitations

- Host execution, file diffs, checks, approvals, and local inference are
  simulated by design. Cloud analysis is real when an endpoint is configured.
- The local model catalogue is a UI preview; there is no on-device inference
  engine yet.
- Cloud sends require connectivity. There is no automatic offline queue; the
  failure path and retry action are explicit.
- In-app updates are not implemented; distribution signing is ready.

More detail — invariants, verification evidence, and continuation notes — is in
[HANDOVER.md](HANDOVER.md). The original product brief is
[AGENTDECK_MVP.md](AGENTDECK_MVP.md).
