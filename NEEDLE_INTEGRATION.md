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
4. `runAgentLoop` local path: count consecutive local replies with zero
   parseable tool blocks; after 2, delegate: Needle(userMessage) → tool calls
   → ToolExecutor (same approvals) → feed results back to the local LLM.
5. Tool doc strings must be short and description-rich (Needle uses them as
   the tool descriptions).
6. Ship toggle: Settings → Agent intelligence → "Needle tools (beta)" with
   model size note (35 MB).

## Test criteria
- "search the web for X" on a local model produces a real web_search tool
  event; nonsense prompt produces no tool call (confidence gate ≥ 0.5).
- Approval flow unchanged; cloud sessions untouched.

## Update (2026-10-27): switch to the official Cactus Android engine

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
