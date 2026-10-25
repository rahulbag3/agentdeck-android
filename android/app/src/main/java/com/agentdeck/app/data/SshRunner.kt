package com.agentdeck.app.data

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Executes commands in Termux over loopback SSH. This is the honest way to
 * reach real Node.js/Python/Gradle toolchains on Android: a standard app
 * cannot exec downloaded binaries (W^X), but Termux ships them and runs an
 * sshd the app can drive on 127.0.0.1. The workspace lives in
 * Android/media so both processes see the same real files.
 */
class SshRunner {

    data class Config(
        val enabled: Boolean = false,
        val host: String = "127.0.0.1",
        val port: Int = 8022,
        val user: String = "",
        val password: String = ""
    ) {
        val ready: Boolean get() = enabled && user.isNotBlank() && password.isNotBlank()
    }

    data class Result(val exitCode: Int, val output: String, val timedOut: Boolean) {
        val ok: Boolean get() = exitCode == 0 && !timedOut
    }

    suspend fun run(
        config: Config,
        workdir: java.io.File,
        command: String,
        timeoutMs: Long = 60_000L
    ): Result = withContext(Dispatchers.IO) {
        if (!config.ready) return@withContext Result(-1, "ERROR: device shell is not configured.", timedOut = false)
        var session: Session? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(config.user, config.host, config.port)
            session.setPassword(config.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(8_000)

            val channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
            // Termux sees the shared media workspace; cd there before the command.
            val escaped = command.replace("'", "'\\''")
            channel.setCommand("cd '$workdir.absolutePath' 2>/dev/null; $escaped; exit \$?")
            channel.setInputStream(null)
            val out = ByteArrayOutputStream()
            channel.setOutputStream(out)
            val err = ByteArrayOutputStream()
            channel.setErrStream(err)
            channel.connect(timeoutMs.toInt().coerceAtMost(120_000))

            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            while (channel.isConnected && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(120)
            }
            val timedOut = channel.isConnected
            if (timedOut) channel.sendSignal("KILL")
            val exit = runCatching { channel.exitStatus }.getOrDefault(if (timedOut) -1 else 0)
            channel.disconnect()
            val text = (out.toString("UTF-8") + err.toString("UTF-8")).trim()
            Result(
                exitCode = exit,
                output = (if (text.length > ShellRunner.MAX_OUTPUT_CHARS) {
                    text.take(ShellRunner.MAX_OUTPUT_CHARS) + "\n… output truncated"
                } else text).ifBlank { "(no output)" },
                timedOut = timedOut
            )
        } catch (error: Exception) {
            Result(-1, "ERROR: device shell failed — ${error.message?.take(140)}. " +
                "Check that Termux is running sshd (sshd) and the port, user and password are right.", timedOut = false)
        } finally {
            session?.disconnect()
        }
    }
}
