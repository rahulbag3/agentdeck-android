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
        runCatching {
            // Vendor OpenCL is exposed as a public library. Load it first so the
            // OpenCL backend's DT_NEEDED resolves inside the app namespace.
            runCatching { System.loadLibrary("OpenCL") }
            System.loadLibrary("agentdeck_llama")
        }.isSuccess
    }

    @Volatile
    private var initialized = false

    /** Loads backend libraries from the APK's native library directory. */
    fun initialize(backendDir: String) {
        if (!available || initialized || backendDir.isBlank()) return
        synchronized(this) {
            if (initialized) return
            runCatching { nativeInit(backendDir) }
            initialized = true
        }
    }

    external fun nativeInit(backendDir: String)

    external fun nativeLoad(
        path: String,
        threads: Int,
        contextSize: Int,
        accelerator: String
    ): Long
    external fun nativeFree(handle: Long)
    external fun nativeCancel(handle: Long)
    /** Backend devices reported by ggml, e.g. CPU, GPUOpenCL, HTP0:0. */
    fun devices(): List<String> {
        if (!available) return emptyList()
        return runCatching {
            nativeDevices().split('|').filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    /** The device that actually loaded the last model (may be a CPU fallback). */
    fun lastBackend(): String {
        if (!available) return "CPU"
        return runCatching { nativeLastBackend() }.getOrDefault("CPU")
    }

    external fun nativeDevices(): String
    external fun nativeLastBackend(): String

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
