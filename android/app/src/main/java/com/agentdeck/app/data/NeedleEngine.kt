package com.agentdeck.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * On-device tool-calling engine (Cactus Needle, 35 MB grammar-constrained
 * model). Small local LLMs often fail to emit our agentdeck-tool protocol;
 * when that happens twice, tool selection is delegated to Needle, whose
 * output is grammar-guaranteed JSON. Tool execution and approvals are
 * unchanged — Needle only chooses the tool and fills arguments.
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

    /** True when the native symbols exist (arm64 builds only). */
    val supported: Boolean by lazy {
        runCatching { System.loadLibrary("agentdeck_llama") }.isSuccess &&
            runCatching { nativeModels() }.isSuccess
    }

    var lastError: String? = null
        private set

    fun ensureReady(context: Context, toolsJson: String): Boolean {
        if (initialized && ready) return true
        if (!supported) {
            lastError = "engine not supported on this ABI"
            return false
        }
        return synchronized(this) {
            if (initialized && ready) return@synchronized true
            try {
                val cact = context.assets.open("needle3.cact").use { input ->
                    val buffer = ByteArray(input.available())
                    var read = 0
                    while (read < buffer.size) {
                        val n = input.read(buffer, read, buffer.size - read)
                        if (n < 0) break
                        read += n
                    }
                    buffer
                }
                if (!nativeLoad(cact)) {
                    lastError = nativeLastError()
                    return@synchronized false
                }
                val indexPath = File(context.cacheDir, "needle-tool-index").absolutePath
                if (!nativeInit(NEEDLE_SYSTEM_PROMPT, toolsJson, indexPath)) {
                    lastError = nativeLastError()
                    return@synchronized false
                }
                ready = true
                initialized = true
                true
            } catch (e: Throwable) {
                lastError = "exception: ${e.javaClass.simpleName}: ${e.message?.take(140)}"
                false
            }
        }
    }

    /** Runs a prompt through Needle and returns parsed tool calls (may be empty). */
    fun toolCallsFor(prompt: String): List<ToolCall> {
        if (!ready) return emptyList()
        val json = runCatching { nativeComplete(prompt, 256) }.getOrNull() ?: return emptyList()
        com.agentdeck.app.data.Logger.warn(
            "Needle raw output",
            null,
            mapOf("length" to json.length.toString(), "preview" to json.take(300))
        )
        return parseToolCalls(json)
    }

    /** Parses {"function_calls":[{"name","arguments":{...}}]} into ToolCalls. */
    fun parseToolCalls(json: String): List<ToolCall> = runCatching {
        val obj = JSONObject(json)
        val calls = obj.optJSONArray("function_calls") ?: return emptyList()
        (0 until calls.length()).mapNotNull { index ->
            val call = calls.optJSONObject(index) ?: return@mapNotNull null
            val name = call.optString("name").lowercase()
            if (name.isBlank()) return@mapNotNull null
            val argsJson = call.optJSONObject("arguments") ?: JSONObject()
            val args = mutableMapOf<String, String>()
            val keys = argsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                args[key] = argsJson.optString(key)
            }
            ToolCall(name = name, args = args, body = "")
        }
    }.getOrDefault(emptyList())

    /** Compact tool catalogue with typed parameters for Needle's grammar. */
    fun toolsJson(): String {
        fun prop(type: String, description: String) =
            JSONObject().put("type", type).put("description", description)

        fun arg(name: String, schema: JSONObject) =
            JSONObject().put(name, schema)

        fun tool(name: String, description: String, required: String, params: JSONObject) =
            JSONObject()
                .put("name", name)
                .put("description", description)
                .put(
                    "parameters",
                    JSONObject()
                        .put("type", "object")
                        .put("properties", params)
                        .put("required", org.json.JSONArray().put(required))
                )

        return org.json.JSONArray().put(
            tool(
                "web_search", "Search the web and return results for a query.", "query",
                arg("query", prop("string", "The search query text"))
            )
        ).put(
            tool(
                "web_fetch", "Fetch a web page by URL and return its text.", "url",
                arg("url", prop("string", "The full http(s) URL of the page"))
            )
        ).put(
            tool(
                "memory_save", "Save a fact about the user for later.", "text",
                arg("text", prop("string", "The fact to remember"))
            )
        ).put(
            tool(
                "memory_recall", "Recall previously saved facts.", "query",
                arg("query", prop("string", "What to look up in memory"))
            )
        ).put(
            tool(
                "read", "Read a workspace file. Arguments: path.", "path",
                arg("path", prop("string", "Workspace-relative file path"))
            )
        ).put(
            tool(
                "list", "List workspace files in a folder.", "path",
                arg("path", prop("string", "Workspace-relative folder path"))
            )
        ).put(
            tool(
                "write", "Write a workspace file (needs user approval).", "path",
                arg("path", prop("string", "Workspace-relative file path"))
            )
        ).put(
            tool(
                "run", "Run a shell command (needs user approval).", "command",
                arg("command", prop("string", "The shell command to run"))
            )
        ).put(
            tool(
                "git", "Git status, diff or log in the workspace.", "action",
                arg("action", prop("string", "One of: status, diff, log"))
            )
        ).toString()
    }

    private const val NEEDLE_SYSTEM_PROMPT =
        "You choose tools for the user's request on an Android device. " +
            "Return the tool calls that satisfy the request."

    external fun nativeLoad(cact: ByteArray): Boolean
    external fun nativeModels(): Int
    external fun nativeInit(systemPrompt: String, toolsJson: String, toolIndexPath: String): Boolean
    external fun nativeComplete(input: String, maxNewTokens: Int): String
    external fun nativeLastError(): String
}
