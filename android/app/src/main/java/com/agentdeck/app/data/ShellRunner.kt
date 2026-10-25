package com.agentdeck.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Executes commands in Android's real system shell (`/system/bin/sh` + toybox
 * applets). Stock Android has no Node/Python/Gradle toolchains — commands run
 * only after explicit user approval, and the allowlist below rejects anything
 * that is not a known system utility.
 */
class ShellRunner {

    data class Result(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean
    ) {
        val ok: Boolean get() = exitCode == 0 && !timedOut
    }

    suspend fun run(workdir: File, command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result =
        withContext(Dispatchers.IO) {
            val trimmed = command.trim()
            if (trimmed.isEmpty()) return@withContext Result(-1, "ERROR: empty command", timedOut = false)
            val rejection = allowlistRejection(trimmed)
            if (rejection != null) return@withContext Result(-1, "ERROR: $rejection", timedOut = false)

            val process = try {
                ProcessBuilder("sh", "-c", trimmed)
                    .directory(if (workdir.isDirectory) workdir else workdir.parentFile)
                    .redirectErrorStream(true)
                    .start()
            } catch (error: Exception) {
                return@withContext Result(-1, "ERROR: could not start shell: ${error.message}", timedOut = false)
            }

            val output = StringBuilder()
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (output.length < MAX_OUTPUT_CHARS) {
                        output.appendLine(line.take(500))
                    }
                }
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@withContext Result(
                        -1,
                        output.toString() + "\nERROR: timed out after ${timeoutMs / 1000}s (killed)",
                        timedOut = true
                    )
                }
                if (output.length >= MAX_OUTPUT_CHARS) {
                    output.appendLine("… output truncated at $MAX_OUTPUT_CHARS chars")
                }
                Result(process.exitValue(), output.toString().trimEnd(), timedOut = false)
            } finally {
                process.destroyForcibly()
            }
        }

    /** Rejects commands whose segments do not start with an allowed utility. */
    fun allowlistRejection(command: String): String? {
        val segments = command.split(Regex("&&|\\|\\||;|\\|"))
        for (segment in segments) {
            val words = segment.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue
            var first = words[0]
            // Allow env-prefixed commands ("VAR=x cmd") and path-qualified binaries.
            while (first.contains('=') && words.size > 1) {
                first = words[words.indexOf(first) + 1]
            }
            val name = File(first).name
            if (name in BLOCKED) return "\"$name\" is not allowed on this device."
            if (name !in ALLOWED) {
                return "\"$name\" is not in the Android system utility allowlist. " +
                    "Allowed: ${ALLOWED.take(12).joinToString(", ")}…"
            }
        }
        return null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        const val MAX_OUTPUT_CHARS = 20_000

        /** System utilities available through toybox on stock Android. */
        val ALLOWED = setOf(
            "sh", "ls", "cat", "head", "tail", "wc", "grep", "egrep", "fgrep", "sed", "awk",
            "find", "sort", "uniq", "cut", "tr", "nl", "tee", "basename", "dirname", "stat",
            "file", "mkdir", "cp", "mv", "rm", "touch", "chmod", "echo", "printf", "date",
            "du", "df", "which", "env", "pwd", "true", "false", "sleep", "uname", "id",
            "tar", "unzip", "gzip", "gunzip", "zcat", "diff", "cmp", "md5sum", "sha256sum",
            "ln", "readlink", "realpath", "dirname", "seq", "test", "expr", "od", "strings",
            "xxd", "toybox", "busybox"
        )

        private val BLOCKED = setOf("su", "sudo", "am", "pm", "cmd", "settings", "dd", "mount", "reboot")
    }
}
