package com.agentdeck.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Real file workspace for agent tasks. Each project gets a directory under
 * `files/workspace/<projectId>/` — plain java.io paths so git (JGit) and the
 * system shell can operate on them. Every path that originates from a model
 * goes through [safeFile], which confines it to the project's workspace.
 */
class Workspace(
    context: Context,
    private val client: OkHttpClient = defaultClient()
) {
    /**
     * Root of all agent workspaces. Prefer Android/media/<pkg>/workspace:
     * Termux (with storage permission) can read it over the SSH bridge, while
     * app-private storage cannot be shared. Falls back to private storage
     * when the shared path is not writable.
     */
    private val root: File = run {
        val media = File("/storage/emulated/0/Android/media/com.agentdeck.app/workspace")
        if (runCatching { media.mkdirs(); media.isDirectory && media.canWrite() }.getOrDefault(false)) {
            media
        } else {
            File(context.applicationContext.filesDir, "workspace")
        }
    }

    fun dirFor(projectId: String): File {
        val id = projectId.ifBlank { "default" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(root, id)
    }

    fun hasWorkspace(projectId: String): Boolean =
        dirFor(projectId).isDirectory && dirFor(projectId).listFiles()?.isNotEmpty() == true

    /** Confines a relative path inside the project workspace; null when unsafe. */
    fun safeFile(projectId: String, path: String): File {
        val dir = dirFor(projectId)
        return confine(dir, path) ?: throw IllegalArgumentException("path escapes workspace: $path")
    }

    private fun safePathOrNull(projectId: String, path: String): File? =
        runCatching { safeFile(projectId, path) }.getOrNull()

    suspend fun list(projectId: String, path: String, maxEntries: Int = 300): String =
        withContext(Dispatchers.IO) {
            val dir = safePathOrNull(projectId, path.ifBlank { "." }) ?: return@withContext "ERROR: invalid path"
            if (!dir.exists()) return@withContext "ERROR: path does not exist"
            val base = if (dir.isDirectory) dir else dir.parentFile ?: return@withContext "ERROR: invalid path"
            val out = StringBuilder()
            var count = 0
            base.walkTopDown().forEach { file ->
                if (file == base) return@forEach
                if (count >= maxEntries) {
                    out.appendLine("… truncated at $maxEntries entries")
                    return@withContext out.toString()
                }
                val rel = file.toRelativeString(base)
                out.appendLine(if (file.isDirectory) "$rel/" else "$rel (${file.length()} B)")
                count++
            }
            if (out.isEmpty()) "(empty)" else out.toString().trimEnd()
        }

    suspend fun read(projectId: String, path: String, maxChars: Int = MAX_READ_CHARS): String =
        withContext(Dispatchers.IO) {
            val file = safePathOrNull(projectId, path) ?: return@withContext "ERROR: invalid path"
            if (!file.exists() || file.isDirectory) return@withContext "ERROR: file not found: $path"
            if (file.length() > MAX_FILE_BYTES) {
                return@withContext "ERROR: file too large to read (${file.length()} bytes, cap $MAX_FILE_BYTES)"
            }
            val text = runCatching { file.readText() }.getOrElse { return@withContext "ERROR: not a text file" }
            if (text.indexOf('\u0000') >= 0) return@withContext "ERROR: binary file"
            if (text.length > maxChars) text.take(maxChars) + "\n… truncated at $maxChars chars"
            else text
        }

    suspend fun write(projectId: String, path: String, content: String): String =
        withContext(Dispatchers.IO) {
            val file = safePathOrNull(projectId, path) ?: return@withContext "ERROR: invalid path"
            if (content.length > MAX_WRITE_CHARS) {
                return@withContext "ERROR: content too large (${content.length} chars, cap $MAX_WRITE_CHARS)"
            }
            file.parentFile?.mkdirs()
            runCatching { file.writeText(content) }
                .onFailure { return@withContext "ERROR: could not write: ${it.message}" }
            dirFor(projectId).mkdirs()
            "wrote ${content.length} chars to $path"
        }

    /** Line search across the workspace; runs in-process, no approval needed. */
    suspend fun grep(
        projectId: String,
        pattern: String,
        path: String,
        maxResults: Int = 40
    ): String = withContext(Dispatchers.IO) {
        val regex = runCatching { Regex(pattern) }.getOrElse {
            return@withContext "ERROR: invalid regex: ${it.message}"
        }
        val base = safePathOrNull(projectId, path.ifBlank { "." }) ?: return@withContext "ERROR: invalid path"
        if (!base.exists()) return@withContext "ERROR: path does not exist"
        val searchRoot = if (base.isDirectory) base else base.parentFile ?: base
        val out = StringBuilder()
        var results = 0
        searchRoot.walkTopDown().forEach { file ->
            if (results >= maxResults) return@forEach
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return@forEach
            val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
            if (text.indexOf('\u0000') >= 0) return@forEach
            text.lineSequence().forEachIndexed { index, line ->
                if (results >= maxResults) return@forEachIndexed
                if (regex.containsMatchIn(line)) {
                    out.appendLine("${file.toRelativeString(searchRoot)}:${index + 1}: ${line.trim().take(200)}")
                    results++
                }
            }
        }
        if (out.isEmpty()) "no matches for /$pattern/" else out.toString().trimEnd() + "\n($results matches)"
    }

    /** Imports a repository as a zip from GitHub codeload into the workspace. */
    suspend fun importFromGitHub(
        projectId: String,
        owner: String,
        repo: String,
        branch: String,
        token: String?
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val dir = dirFor(projectId)
        val url = "https://codeload.github.com/$owner/$repo/zip/refs/heads/$branch"
        val builder = Request.Builder().url(url).header("User-Agent", "AgentDeck-Android")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer ${token.trim()}")
        val call = client.newCall(builder.build())
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("GitHub returned ${response.code} for $repo@$branch"))
                }
                val temp = File(dir.parentFile, "import-${System.currentTimeMillis()}.zip")
                try {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_IMPORT_BYTES) {
                                    throw IOException("Repository zip exceeds ${MAX_IMPORT_BYTES / 1_000_000} MB cap")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    val summary = unzipInto(temp, dir)
                    Result.success(summary)
                } finally {
                    temp.delete()
                }
            }
        } catch (error: IOException) {
            Result.failure(error)
        }
    }

    /** Extracts a GitHub zip (single root folder) into the target dir. */
    private fun unzipInto(zip: File, target: File): ImportSummary {
        target.mkdirs()
        var entries = 0
        var bytes = 0L
        ZipInputStream(zip.inputStream().buffered()).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (entries >= MAX_IMPORT_ENTRIES) throw IOException("Repository has more than $MAX_IMPORT_ENTRIES files")
                val name = entry.name.removePrefix("/").replace('\\', '/')
                if (name.isEmpty() || name.contains("..")) continue
                // GitHub zips wrap everything in <repo>-<branch>/; strip it.
                val rel = name.substringAfter('/', "")
                if (rel.isEmpty()) continue
                val out = File(target, rel)
                val canonical = out.canonicalFile
                if (!canonical.path.startsWith(target.canonicalPath + File.separator)) continue
                if (entry.isDirectory) {
                    canonical.mkdirs()
                } else {
                    canonical.parentFile?.mkdirs()
                    FileOutputStream(canonical).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0L
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            size += read
                            bytes += read
                            if (size > MAX_ENTRY_BYTES) throw IOException("File $rel exceeds entry size cap")
                            output.write(buffer, 0, read)
                        }
                    }
                    entries++
                }
                stream.closeEntry()
            }
        }
        return ImportSummary(files = entries, bytes = bytes)
    }

    suspend fun clear(projectId: String): Boolean = withContext(Dispatchers.IO) {
        val dir = dirFor(projectId)
        if (dir.exists()) dir.deleteRecursively() else true
    }

    fun stats(projectId: String): Pair<Int, Long> {
        val dir = dirFor(projectId)
        if (!dir.exists()) return 0 to 0L
        var files = 0
        var bytes = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                files++
                bytes += file.length()
            }
        }
        return files to bytes
    }

    data class ImportSummary(val files: Int, val bytes: Long)

    companion object {
        /** Pure path confinement: resolves [rel] under [root] safely, or null. */
        fun confine(root: File, rel: String): File? {
            val trimmed = rel.trim().trimStart('/')
            if (trimmed.isEmpty() || trimmed.length > 300) return null
            if (trimmed.contains("..") || trimmed.contains('\\') || trimmed.contains(':')) return null
            if (!root.isDirectory && !root.mkdirs()) return null
            val canonical = File(root, trimmed).canonicalFile
            val canonicalRoot = root.canonicalFile
            if (canonical.path != canonicalRoot.path &&
                !canonical.path.startsWith(canonicalRoot.path + File.separator)
            ) return null
            return canonical
        }

        const val MAX_READ_CHARS = 60_000
        const val MAX_WRITE_CHARS = 120_000
        const val MAX_FILE_BYTES = 2_000_000L
        const val MAX_ENTRY_BYTES = 20_000_000L
        const val MAX_IMPORT_BYTES = 300_000_000L
        const val MAX_IMPORT_ENTRIES = 5_000

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()
    }
}
