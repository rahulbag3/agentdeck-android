package com.agentdeck.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentdeck.app.data.LocalEngine
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Real-device check for the native engine and its accelerators.
 *
 * Run with a model already readable by the app:
 * adb shell run-as com.agentdeck.app cp /data/local/tmp/model.gguf files/test.gguf
 * adb shell am instrument -w \
 *   -e class com.agentdeck.app.LocalEngineDeviceTest \
 *   -e modelPath /data/user/0/com.agentdeck.app/files/test.gguf \
 *   -e accelerator auto com.agentdeck.app.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class LocalEngineDeviceTest {

    private fun log(message: String) = Log.i(TAG, message)

    @Test
    fun backendListIsReported() {
        assumeTrue("native engine unavailable", LocalEngine.available)
        LocalEngine.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.nativeLibraryDir
        )
        val devices = LocalEngine.devices()
        log("devices=${devices.joinToString()}")
        assertTrue("At least the CPU backend must be present", devices.isNotEmpty())
    }

    @Test
    fun generateOnRequestedBackend() {
        assumeTrue("native engine unavailable", LocalEngine.available)
        LocalEngine.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.nativeLibraryDir
        )
        val arguments = InstrumentationRegistry.getArguments()
        val modelPath = arguments.getString("modelPath")
        assumeTrue("Pass -e modelPath <path> to run generation", !modelPath.isNullOrBlank())
        val path = requireNotNull(modelPath)
        val accelerator = arguments.getString("accelerator") ?: "auto"

        val modelFile = java.io.File(path)
        log("modelExists=${modelFile.exists()} canRead=${modelFile.canRead()} bytes=${modelFile.length()}")
        val loadStart = System.nanoTime()
        val handle = LocalEngine.nativeLoad(path, 4, 1024, accelerator)
        val loadSeconds = (System.nanoTime() - loadStart) / 1_000_000_000.0
        assertTrue("nativeLoad returned 0 for $path", handle != 0L)
        val backend = LocalEngine.lastBackend()
        log(
            String.format(
                Locale.US,
                "accelerator=%s backend=%s load=%.2fs devices=%s",
                accelerator, backend, loadSeconds, LocalEngine.devices().joinToString()
            )
        )

        val buffer = StringBuilder()
        var tokens = 0
        val generateStart = System.nanoTime()
        val result = LocalEngine.nativeGenerate(
            handle,
            "You are a concise assistant.",
            arrayOf("user"),
            arrayOf("Write a short paragraph about the ocean."),
            64,
            0.7f,
            0.9f,
            object : LocalEngine.TokenCallback {
                override fun onToken(text: String): Boolean {
                    synchronized(buffer) { buffer.append(text) }
                    tokens++
                    return true
                }
            }
        )
        val generateSeconds = (System.nanoTime() - generateStart) / 1_000_000_000.0
        val text = synchronized(buffer) { buffer.toString() }
        val tps = if (generateSeconds > 0) tokens / generateSeconds else 0.0
        log(
            String.format(
                Locale.US,
                "result=%d tokens=%d seconds=%.2f tps=%.2f text=%s",
                result, tokens, generateSeconds, tps, text.take(200).replace('\n', ' ')
            )
        )
        LocalEngine.nativeFree(handle)

        assertTrue("nativeGenerate failed with $result", result >= 0)
        assertTrue("No tokens were produced", tokens > 0)
    }

    private companion object {
        const val TAG = "AgentDeckEngineTest"
    }
}
