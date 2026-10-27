package com.agentdeck.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device tool selection (Cactus Needle 3, 35 MB grammar-constrained model).
 *
 * Small local LLMs often fail to emit our agentdeck-tool protocol. Needle reads
 * the request and the declared tool schemas, then returns grammar-guaranteed
 * JSON with the tool name and arguments. Tool execution and approvals are
 * unchanged: Needle only chooses the tool and fills arguments.
 *
 * Needle 3 has no retrieval head, so every declared tool is rendered into the
 * prompt. Keeping each session's catalogue small is part of the contract:
 * coding sessions get five workspace tools, chat sessions get four web/memory
 * tools. The native model is process-global and not thread-safe; every native
 * call is serialized by [nativeLock], and [ensureReadyLocked] re-initializes
 * the static prefix when the active catalogue changes.
 */
object NeedleEngine {

    interface StateCallback {
        fun onState(state: String)
    }

    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    private var initialized = false

    @Volatile
    private var loaded = false

    @Volatile
    private var activeToolsJson: String? = null

    @Volatile
    private var activeNames: Set<String> = emptySet()

    /** The model is process-global and non-thread-safe; serialize every native call. */
    private val nativeLock = Any()

    /** True when the native symbols exist (arm64 builds only). */
    val supported: Boolean by lazy {
        synchronized(nativeLock) {
            runCatching {
                System.loadLibrary("agentdeck_llama")
                nativeModels()
            }.isSuccess
        }
    }

    @Volatile
    var lastError: String? = null
        private set

    fun ensureReady(context: Context, toolsJson: String): Boolean =
        synchronized(nativeLock) { ensureReadyLocked(context, toolsJson) }

    private fun ensureReadyLocked(context: Context, toolsJson: String): Boolean {
        if (initialized && ready && activeToolsJson == toolsJson) return true
        if (!supported) {
            lastError = "engine not supported on this ABI"
            return false
        }
        try {
            if (!loaded) {
                val cact = context.assets.open("needle3.cact").use { input -> input.readBytes() }
                if (!nativeLoad(cact)) {
                    lastError = nativeLastError()
                    return false
                }
                loaded = true
            }
            // Needle 3 has no retrieval head; tool_index_path is a Needle 2
            // feature. Passing null keeps the model from writing a useless
            // index cache and matches the official Python wrapper.
            if (!nativeInit(NEEDLE_SYSTEM_PROMPT, toolsJson, null)) {
                ready = false
                initialized = false
                lastError = nativeLastError()
                return false
            }
            activeToolsJson = toolsJson
            activeNames = toolNames(toolsJson)
            ready = true
            initialized = true
            lastError = null
            return true
        } catch (e: Throwable) {
            ready = false
            initialized = false
            lastError = "exception: ${e.javaClass.simpleName}: ${e.message?.take(140)}"
            return false
        }
    }

    /**
     * Runs a request through Needle and returns parsed tool calls (may be
     * empty). The conversation is reset before each one-shot completion, so a
     * previous request or tool result can never bleed into this one.
     */
    fun toolCallsFor(context: Context, prompt: String, toolsJson: String): List<ToolCall> =
        synchronized(nativeLock) {
            if (!ensureReadyLocked(context, toolsJson)) return@synchronized emptyList()
            if (prompt.isBlank()) return@synchronized emptyList()
            val json = runCatching { nativeComplete(prompt, 256) }.getOrNull()
                ?: return@synchronized emptyList()
            // Defence in depth: the catalogue never offers write or commit, but
            // a malformed model or a stale catalogue must not blank a file or
            // commit on its own. The active catalogue also bounds which tools
            // this session may use.
            filterCalls(parseToolCalls(json), activeNames)
        }

    /**
     * Parses {"function_calls":[{"name","arguments"}],"confidence":0.9} into
     * ToolCalls. Calls below [MIN_CONFIDENCE] are ignored. The engine already
     * withholds calls below 0.1 into `suppressed_calls`; this floor keeps a
     * malformed or hand-written JSON response from smuggling one through.
     */
    fun parseToolCalls(json: String): List<ToolCall> =
        ToolParser.parseJsonCalls(json, MIN_CONFIDENCE)

    private fun filterCalls(calls: List<ToolCall>, names: Set<String>): List<ToolCall> =
        calls.filter { call ->
            call.name in names &&
                call.name != "write" &&
                !(call.name == "git" && call.args["action"] == "commit")
        }

    /**
     * Catalogue for the session type. Coding sessions get the five workspace
     * tools Needle can actually fill; chat sessions get the four web/memory
     * tools. Needle 3 reads the whole catalogue, so both stay small.
     */
    fun toolsJson(coding: Boolean): String = if (coding) codingToolsJson else chatToolsJson

    private val codingToolsJson: String by lazy { buildCodingTools() }
    private val chatToolsJson: String by lazy { buildChatTools() }

    private fun buildCodingTools(): String = JSONArray()
        .put(
            tool(
                "read",
                "Read one text file from the workspace and return its contents.",
                listOf("path"),
                JSONObject().put(
                    "path",
                    prop("string", "workspace-relative file path, for example src/Main.kt")
                ),
                listOf(READ_TRIGGER)
            )
        ).put(
            tool(
                "list",
                "List the files and folders inside one workspace directory.",
                listOf("path"),
                JSONObject().put(
                    "path",
                    prop(
                        "string",
                        "workspace-relative folder path; use . for the workspace root, or a folder name such as src"
                    )
                ),
                listOf(LIST_TRIGGER)
            )
        ).put(
            tool(
                "grep",
                "Find exact text such as TODO, FIXME, a function name, or an error message inside workspace files. Returns matching lines with file names.",
                listOf("pattern"),
                JSONObject()
                    .put("pattern", prop("string", "the exact text to find, for example TODO"))
                    .put(
                        "path",
                        prop(
                            "string",
                            "workspace-relative folder or file to search; use . for the whole workspace"
                        )
                    ),
                listOf(GREP_TODO_TRIGGER, GREP_ACTION_TRIGGER)
            )
        ).put(
            tool(
                "run",
                "Run a shell command in the workspace and return its exit code and output.",
                listOf("command"),
                JSONObject().put(
                    "command",
                    prop(
                        "string",
                        "the exact shell command to run, copied as written, for example ./gradlew test"
                    )
                ),
                listOf(RUN_TRIGGER)
            )
        ).put(
            tool(
                "git",
                "Show git status, changes, or recent commits in the workspace repository.",
                listOf("action"),
                JSONObject().put(
                    "action",
                    prop(
                        "string",
                        "status lists changed files; diff shows uncommitted changes; log lists recent commits",
                        listOf("status", "diff", "log")
                    )
                ),
                listOf(GIT_TRIGGER)
            )
        ).toString()

    private fun buildChatTools(): String = JSONArray()
        .put(
            tool(
                "web_search",
                "Search the web for pages about a query. Covers news, prices, versions, and facts that are not in the workspace.",
                listOf("query"),
                JSONObject().put("query", prop("string", "the words to search for")),
                listOf(SEARCH_TRIGGER)
            )
        ).put(
            tool(
                "web_fetch",
                "Fetch one web page by URL and return its readable text. The URL must start with http:// or https://.",
                listOf("url"),
                JSONObject().put(
                    "url",
                    prop("string", "full http(s) URL, for example https://example.com/page")
                ),
                listOf(FETCH_TRIGGER)
            )
        ).put(
            tool(
                "memory_save",
                "Save one durable fact about the user, such as a name, preference, or project detail, for later sessions.",
                listOf("text"),
                JSONObject().put("text", prop("string", "the fact to remember, as one sentence")),
                listOf(SAVE_TRIGGER)
            )
        ).put(
            tool(
                "memory_recall",
                "Look up facts saved about the user in earlier sessions.",
                listOf("query"),
                JSONObject().put("query", prop("string", "words describing the fact to look up")),
                listOf(RECALL_TRIGGER)
            )
        ).toString()

    private fun tool(
        name: String,
        description: String,
        required: List<String>,
        params: JSONObject,
        triggers: List<String> = emptyList()
    ): JSONObject = JSONObject()
        .put("name", name)
        .put("description", description)
        .put(
            "parameters",
            JSONObject()
                .put("type", "object")
                .put("properties", params)
                .put("required", JSONArray(required))
        )
        .apply {
            if (triggers.isNotEmpty()) put("triggers", JSONArray(triggers))
        }

    private fun prop(type: String, description: String, values: List<String>? = null): JSONObject =
        JSONObject().put("type", type).put("description", description).apply {
            if (values != null) put("enum", JSONArray(values))
        }

    private fun toolNames(toolsJson: String): Set<String> = runCatching {
        val array = JSONArray(toolsJson)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
        }.toSet()
    }.getOrDefault(emptySet())

    /**
     * Facts for the static prefix. Needle reads these literally; instructions
     * do not steer decoding, so keep the environment factual and let the tool
     * schemas describe the actions.
     */
    private const val NEEDLE_SYSTEM_PROMPT =
        "Environment: Android phone; workspace: a project directory on the device; " +
            "paths: workspace-relative; shell: POSIX sh. The declared tools are the " +
            "complete set available for this request."

    /** Matches the engine's own withholding floor (calls below 0.1 are suppressed). */
    private const val MIN_CONFIDENCE = 0.1

    private const val READ_TRIGGER =
        """\b(read|open|cat|show)\b[\s\S]*\b(file|\.kt|\.kts|\.md|\.json|\.txt|\.gradle|\.xml|\.java|\.py|\.js|\.ts)\b"""
    private const val LIST_TRIGGER =
        """\b(list|show|what(?:'s| is) in)\b[\s\S]*\b(files?|folder|directory)\b"""
    private const val GREP_TODO_TRIGGER = """\b(TODO|FIXME|HACK)\b"""
    private const val GREP_ACTION_TRIGGER =
        """\b(grep|search|find|look for)\b[\s\S]*\b(files?|code|source|workspace|comments?)\b"""
    private const val RUN_TRIGGER =
        """\b(run|execute)\b[\s\S]*\b(command|shell|script|gradle|test|build|ls|echo)\b"""
    private const val GIT_TRIGGER = """\bgit\b"""
    private const val SEARCH_TRIGGER = """\b(search|google)\b[\s\S]*\b(web|internet|online)\b"""
    private const val FETCH_TRIGGER = """https?://"""
    private const val SAVE_TRIGGER = """\b(remember|save|note)\b[\s\S]*\b(that|this|my|i)\b"""
    private const val RECALL_TRIGGER = """\b(what do you remember|recall|memory)\b"""

    external fun nativeLoad(cact: ByteArray): Boolean
    external fun nativeModels(): Int
    external fun nativeInit(systemPrompt: String, toolsJson: String, toolIndexPath: String?): Boolean
    external fun nativeComplete(input: String, maxNewTokens: Int): String
    external fun nativeLastError(): String
}
