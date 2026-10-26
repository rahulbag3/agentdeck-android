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

    fun ensureReady(context: Context, toolsJson: String): Boolean {
        if (initialized && ready) return true
        if (!supported) return false
        return synchronized(this) {
            if (initialized && ready) return@synchronized true
            runCatching {
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
                if (!nativeLoad(cact)) return@synchronized false
                val indexPath = File(context.cacheDir, "needle-tool-index").absolutePath
                if (!nativeInit(NEEDLE_SYSTEM_PROMPT, toolsJson, indexPath)) return@synchronized false
                ready = true
                initialized = true
                true
            }.getOrDefault(false)
        }
    }

    /** Runs a prompt through Needle and returns parsed tool calls (may be empty). */
    fun toolCallsFor(prompt: String): List<ToolCall> {
        if (!ready) return emptyList()
        val json = runCatching { nativeComplete(prompt, 256) }.getOrNull() ?: return emptyList()
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

    /** Compact tool catalogue for Needle's static prefix. */
    fun toolsJson(tools: List<Pair<String, String>>): String {
        val array = org.json.JSONArray()
        tools.forEach { (name, description) ->
            array.put(
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put(
                        "parameters",
                        JSONObject().put("type", "object").put("properties", JSONObject())
                    )
            )
        }
        return array.toString()
    }

    private const val NEEDLE_SYSTEM_PROMPT =
        "You choose tools for the user's request on an Android device. " +
            "Return the tool calls that satisfy the request."

    external fun nativeLoad(cact: ByteArray): Boolean
    external fun nativeModels(): Int
    external fun nativeInit(systemPrompt: String, toolsJson: String, toolIndexPath: String): Boolean
    external fun nativeComplete(input: String, maxNewTokens: Int): String
}
