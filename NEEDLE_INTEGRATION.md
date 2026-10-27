# Needle integration — local-model tool calling (next task)

User-approved direction: fix "local models can't call tools" by delegating
tool selection to Needle (cactus-compute/needle) when the local LLM fails to
emit our `agentdeck-tool` protocol.

## Verified facts (2026-10-25)
- Prebuilt **android-arm64** engine exists in
  https://huggingface.co/Cactus-Compute/needle3 (folders: android-arm64,
  android-armv7, android-riscv64, …) alongside `needle3.cact` (35.3 MB).
- C API: `needle_init` loads the model + compiled static tool prefix (returns
  tokenized prefix length; fails if it doesn't fit context). Execute + result
  functions are documented in https://cactuscompute.com/blog/needle-supported-devices
- Output is grammar-constrained JSON: `{function_calls: [{name, arguments}],
  reasoning, confidence}`; unknown requests → empty list (no hallucinated calls).
- Telemetry on by default → set `NEEDLE_TELEMETRY=0` / `DO_NOT_TRACK=1`.
- Apache-2.0. Tools are declared with typed signatures + descriptions
  (docstring-style) compiled into the static prefix.

## Integration steps (v1, mirrors the llama.cpp pattern)
1. Download `android-arm64/` engine (`.so` + header) into
   `android/app/src/main/cpp/needle/` and `needle3.cact` into app assets
   (35 MB — consider on-demand download later).
2. `needle_jni.cpp`: JNI wrapper — `needleInit(modelPath, toolsJson)`,
   `needleRun(prompt) -> String(json)`, `needleFree()`; set
   `NEEDLE_TELEMETRY=0` env before init. R8 keep rules like LocalEngine.
3. Kotlin `NeedleEngine` (object, mirrors `LocalEngine`): init once with the
   tool registry JSON built from `ToolRegistry`; `run(prompt)` parses the JSON
   into `List<ToolCall>` (reuse ToolCall/ToolExecutor + approval flow).
4. `runAgentLoop` local path: nudge once with a format reminder; if the next
   reply still has no parseable tool block, delegate to Needle from the user's
   latest request → tool calls → ToolExecutor (same approvals) → feed results
   back to the local LLM. The pre-check is skipped on retry so a retry never
   re-runs a command.
5. Tool doc strings must be short and description-rich (Needle uses them as
   the tool descriptions).
6. Ship toggle: Settings → Agent intelligence → "Needle tools (beta)" with
   model size note (35 MB).

## Test criteria
- "search the web for X" on a local model produces a real web_search tool
  event; nonsense prompt produces no tool call (confidence gate ≥ 0.5).
- Approval flow unchanged; cloud sessions untouched.

## Update (2026-10-27): Cactus Android engine — superseded, do not apply

> Superseded by the 2026-10-28 verification below. Cactus `android/` builds
> `libcactus_engine.so`, a different chat engine; it is not Needle. The static
> `libneedle.a` we already link **is** the official Needle Android artifact.
> Keep the current integration.

- The user's tools example confirms our JSON schema matches the official
  format (name/description/parameters/required; optional "triggers" regexes
  for fast-path matching — worth adding per tool later).
- **Use the official Android engine instead of the hand-rolled wrapper:**
  `cactus-compute/cactus` `android/` builds `libcactus_engine.so` via
  `cactus build --android` (NDK, min API 21, `android/build.sh`) with a ready
  `cactus_jni.cpp` JNI bridge. Our `needle_jni.cpp` wrapper works for
  load/init/complete but the official bridge + engine is the maintained path
  (it also fixes silent init-failure behavior we hit).
- Next steps: `bash android/build.sh` (needs NDK), swap
  `libcactus_engine.so` + `cactus_jni.cpp` JNI class names into
  `NeedleEngine.kt`, keep the same `ToolRegistry` tools JSON, keep telemetry
  disabled, then re-test "search the web for X" on a local model session.

## Update (2026-10-27, later): reliability guardrails

- `ToolParser.parseJsonCalls(json, 0.5)` is the single JSON entry point; calls
  below the 0.5 confidence gate are ignored.
- `ToolRegistry.toolsJson()` no longer offers `write` (Needle cannot supply a
  file body) and `toolCallsFor` also drops any `write` or `git commit` it
  emits, so a local fallback can never blank a file.
- The raw-output diagnostic log was removed — it leaked prompts and tool
  arguments into Crashlytics.
- `needle_jni.cpp` now converts standard UTF-8 to UTF-16 before `NewString`
  (emoji / 4-byte characters previously aborted via modified-UTF-8).

## Update (2026-10-28): official verification and session-scoped use

Verified against the published artifacts and docs, then exercised on-device
with the official `needle` CLI before changing app code.

### Artifact verification

| Artifact | SHA256 | Result |
| --- | --- | --- |
| `app/src/main/assets/needle3.cact` | `C9D915ECA282ED42D1A09B143B592ADB4CC6744FFE2D294ADF5CFC5548170C38` | matches HF `Cactus-Compute/needle3` LFS oid |
| `app/src/main/cpp/needle/libneedle.a` | `7AA6DE6132A22BF45F5E903425FEB66F159AD46F47FA558921BC4FD02C3A2FA0` | matches `android-arm64/` |
| `app/src/main/cpp/needle/needle.h` | — | matches the published header |

- **Do not swap to Cactus `android/`.** That tree builds `libcactus_engine.so`,
  an LLM chat engine with its own JNI bridge. Needle is a separate 35 MB
  model/runtime, and `libneedle.a` from the same HF folder is the official
  Android build of it.
- Keep the code Apache-2.0 attribution and telemetry disabled
  (`NEEDLE_TELEMETRY=0`, `DO_NOT_TRACK=1`).

### What Needle 3 does and does not do

- Tool retrieval is the one point where the official pages disagree: the
  repo's `llms.txt` says Needle 3 has no tool-retrieval head and reads the
  whole toolset, while the Python docs and the tool-design guide say retrieval
  engages above five tools. We keep every session at five or fewer, so the
  direct-render path is what runs either way. `needle_init` returns the
  tokenized prefix length and fails if it does not fit the context window.
- Process-global and **not thread-safe** (`needle.h` says so explicitly). One
  text model per process, one conversation at a time.
- Output is grammar-constrained JSON:
  `{"function_calls":[{"name","arguments"}],"reasoning","confidence"}`, plus
  `suppressed_calls` when the engine withholds a low-confidence call.
- Official guidance: keep the catalogue at five tools or fewer. A large
  catalogue measurably degrades routing.

### Catalogue policy (`NeedleEngine.toolsJson`)

Needle only ever selects tools; `ToolExecutor` and the approval flow are
unchanged. Catalogues are session-scoped and re-initialized when they change:

| Session | Catalogue | Tools |
| --- | --- | --- |
| Coding / workspace | 5 | `read`, `list`, `grep`, `run`, `git` |
| Chat / web | 4 | `web_search`, `web_fetch`, `memory_save`, `memory_recall` |

- `write` and `git commit` are never offered. `toolCallsFor` also filters them
  (and anything outside the active catalogue) as defence in depth, so a stale
  or malformed response cannot blank a file or commit on its own.
- `git.action` is a closed enum: `status`, `diff`, `log`.
- `grep.pattern` is required; `grep.path` is optional.
- `list.path` is required but carries a schema default of `.`, so a request
  that does not name a folder still produces a call instead of being withheld.
- `web_fetch.url` carries the grammar pattern `^https?://`; an invalid URL is
  unrepresentable rather than merely discouraged.
- Descriptions stay factual: Needle reads them literally and does not follow
  instructions in the system prompt.
- `triggers` regexes are limited to unambiguous read-only actions: `read`,
  `list`, `git`, `web_search`, `web_fetch`. `grep`, `run`, `memory_save` and
  `memory_recall` declare none, because a trigger-forced call with a bad
  argument is worse than a refusal. On-device, a forced `grep` copied the wrong
  span (`"source code"` for "find TODO").
- The system turn is environment facts in the documented form
  (`device: phone; locale: en-US; ...`), not prose instructions. No date
  argument exists, so omitting `date` is safe.
- Coding sessions **try the local model first**. The five-tool catalogue is a
  fallback after the model fails to emit a parseable tool. Non-coding local
  sessions may run the four-tool pre-check up front. The retry path skips the
  pre-check so a retry cannot re-run a command.
- If `needle_init` fails, the app posts a "Needle unavailable" notice with the
  native error instead of silently doing nothing.

### Confidence policy

The engine applies the 0.1 floor before the app sees the response:

- `function_calls` non-empty — trust the engine. `>= 0.7` acts; the 0.1–0.7
  band confirms. Safe tools may run, dangerous tools still prompt.
- A `function_calls` entry below 0.1 only survives when a `triggers` regex
  matched. That is the documented opt-in, so the parser does not re-filter it.
  Memory tools deliberately declare no triggers, which keeps the bypass on
  read-only workspace/web tools and `run` (which still needs approval).
- `function_calls` empty and `suppressed_calls` present — treat the turn as a
  refusal. Suppressed calls are never parsed or executed.
- Both empty — refusal.
- A 0.5 gate was too high: on-device calls that were correct scored 0.11–0.35
  and were being discarded.

### Native/JNI rules (`needle_jni.cpp`)

- `needle_reset()` before every one-shot `needle_complete`, so one request
  cannot bias the next.
- A native `std::mutex` plus the Kotlin `nativeLock` serialize all calls; the
  model is process-global.
- Both directions use standard UTF-8: JNI strings are decoded from UTF-16 and
  encoded manually (`GetStringUTFChars` is modified UTF-8), and output is
  decoded to UTF-16 and returned with `NewString`.
- `nativeLoad` uses `GetByteArrayElements` / `ReleaseByteArrayElements`
  instead of copying the 35 MB asset into another vector.
- `tool_index_path` is `null`: the repo's `llms.txt` lists it as a Needle 2
  feature, and our catalogues never exceed five tools.
- Telemetry is disabled before the engine initializes:
  `NEEDLE_TELEMETRY=0` and `DO_NOT_TRACK=1` are set in `AgentDeckApplication`
  and again in `nativeLoad`.
- `needle_init` is re-run when the active catalogue changes. The static prefix
  is rebuilt, so a previous session's tools cannot leak into the next.

### On-device CLI evidence

Official `android-arm64/needle` binary, `needle3.cact`, and the catalogues in
`/data/local/tmp/needle-test` on `10BEC50UTS000UN` (arm64, API 36):

| Prompt | Catalogue | Result | Confidence |
| --- | --- | --- | --- |
| find TODO | shared 9-tool | `git action=log` (wrong) | 0.1652 |
| find TODO | 5-tool rev2 | `grep pattern="source code"` (wrong arg) | 0.0818 |
| search web latest Kotlin | chat 4 | `web_search` | 0.3519 |
| weather | chat 4 | `web_search` | 0.2883 |
| list files | coding 5 | `list` | 0.1434 |
| read Main.kt | coding 5 | `read` | 0.7705 |
| git status | coding 5 | `git action=status` | 0.7042 |
| run gradle test | coding 5 | `run` | 0.7810 |
| fetch https://… | chat 4 | `web_fetch` | 0.6464 |
| remember my name | chat 4 | `memory_save` suppressed | 0.0802 |

Takeaways: shared catalogues misroute coding prompts (a `TODO` search picked
`git log`), and a 0.5 gate discards correct calls. Scoped catalogues fixed the
tool choice in most cases, but argument quality still varies — "find TODO"
filled `grep.pattern` with `"source code"` at 0.0818, below the engine floor,
so the call is correctly withheld and the local model remains the primary
path for coding sessions.

### Test and build

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleRelease -Psnapdragon --console=plain
adb -s 10BEC50UTS000UN install -r app/build/outputs/apk/release/app-release.apk
```

`ToolParserResilienceTest` now asserts the exact five- and four-tool
catalogues, the `git` enum, required `grep` arguments, typed required fields,
`list.path`'s `.` default, the `web_fetch.url` grammar pattern, engine-supplied
low-confidence calls being trusted, acceptance in the 0.1–0.7 band, and that
`suppressed_calls` are never executed. Full suite: **129 tests**.

### Device verification (2026-10-28)

Installed over `1.0.0-snapdragon` on `10BEC50UTS000UN` with app data preserved.

- **Chat, 4-tool catalogue:** `needle_load ok: 35335380 bytes, models=1`;
  `needle_init ok: static prefix 326 tokens`.
  "search the web for the latest Kotlin release" produced a real
  `web_search`, and the local Qwen3.5-4B answered from the returned GitHub
  release pages ("Kotlin 2.4.20 … October 2026").
- **Coding, 5-tool catalogue:** a coding session made no Needle call up front
  (by design) and the local model ran `git status` and `list .` itself. When a
  later coding run fell through to Needle in the same process, the catalogue
  re-initialized successfully: `needle_init ok: static prefix 456 tokens`
  (versus 326 for the 4-tool chat catalogue). A fallback case that produces a
  confident tool call is still worth running end-to-end.
- The release APK for this build is
  `dist/agentdeck-snapdragon-1.0.0-needle-scoped.apk` (MD5
  `741623ecd3a2eaeb59158b1d77988c42`). The previous working build remains at
  `dist/agentdeck-snapdragon-1.0.0.apk`.

### Audit-build verification (2026-10-28, later)

Rebuilt after the official-docs audit and installed over the same data:

- `AgentDeckNeedle: telemetry disabled for Needle` appears before
  `needle_load ok: 35335380 bytes, models=1`, confirming the opt-out lands
  before engine init.
- The 4-tool chat catalogue now initializes at **314 tokens** (down from 326)
  after the system turn was reduced to environment facts.
- "search the web for the Android 16 release date" produced
  `web_search "Android 16 release date official"`, the tool completed, and
  Qwen3.5-4B summarized the returned pages. Full suite: **129 tests, 0
  failures**.
- This build is `dist/agentdeck-snapdragon-1.0.0-needle-audit.apk` (MD5
  `48d8fdce74ad0f360c4965a45c2708cd`).
