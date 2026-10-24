package com.agentdeck.app.data

/**
 * JNI bridge to the bundled llama.cpp runtime.
 *
 * Generation is blocking and must run on a background dispatcher. [TokenCallback]
 * returns false to stop; cancellation can also come from another thread through
 * [nativeCancel].
 */
object LocalEngine {

    interface TokenCallback {
        fun onToken(text: String): Boolean
    }

    val available: Boolean by lazy {
        runCatching { System.loadLibrary("agentdeck_llama") }.isSuccess
    }

    external fun nativeLoad(path: String, threads: Int, contextSize: Int): Long
    external fun nativeFree(handle: Long)
    external fun nativeCancel(handle: Long)
    external fun nativeGenerate(
        handle: Long,
        system: String,
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        callback: TokenCallback
    ): Int
}
