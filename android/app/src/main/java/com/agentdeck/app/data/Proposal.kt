package com.agentdeck.app.data

/**
 * Machine-readable file proposals extracted from cloud replies.
 *
 * The cloud system prompt asks the model to emit each proposed file as a
 * fenced block with an `agentdeck-file` info string:
 *
 * ```agentdeck-file path="src/main.kt"
 * <full new file content>
 * ```
 *
 * Blocks are plain data proposed by the model; nothing touches GitHub until
 * the user explicitly applies a proposal.
 */
data class ProposalFile(
    val path: String,
    val content: String
)

object ProposalParser {

    private const val MAX_FILES = 20
    private const val MAX_FILE_CHARS = 100_000
    private const val MAX_TOTAL_CHARS = 600_000
    private val INFO = Regex("agentdeck-file[^\n]*", RegexOption.IGNORE_CASE)
    private val PATH = Regex("path\\s*=\\s*\"([^\"]+)\"")

    /**
     * Parses `agentdeck-file` fences from a single message. Later blocks for
     * the same path win. Normal fenced code blocks are ignored.
     */
    fun parse(text: String): List<ProposalFile> {
        val byPath = LinkedHashMap<String, String>()
        FenceScanner.fences(text).forEach { fence ->
            if (!fence.isClosed) return@forEach
            val info = INFO.find(fence.info) ?: return@forEach
            val path = PATH.find(info.value)?.groupValues?.get(1)?.trim() ?: return@forEach
            if (!isValidPath(path)) return@forEach
            byPath[path] = fence.body.take(MAX_FILE_CHARS)
        }
        val files = byPath.entries
            .take(MAX_FILES)
            .map { ProposalFile(it.key, it.value) }
        var total = 0
        return files.takeWhile { file ->
            total += file.content.length
            total <= MAX_TOTAL_CHARS
        }
    }

    /** Last-write-wins accumulation across a task's agent messages, in order. */
    fun accumulate(messages: List<Message>): List<ProposalFile> {
        val byPath = LinkedHashMap<String, String>()
        messages.asSequence()
            .filter { it.role == MessageRole.AGENT }
            .forEach { message ->
                parse(message.text).forEach { byPath[it.path] = it.content }
            }
        return byPath.map { ProposalFile(it.key, it.value) }
    }

    private fun isValidPath(path: String): Boolean {
        if (path.isEmpty() || path.length > 300) return false
        if (path.startsWith("/") || path.contains("..") || path.contains('\\')) return false
        if (path.contains("://")) return false
        return true
    }
}

/** Finds fenced code blocks in Markdown text, tolerating ``` and ~~~ fences. */
internal object FenceScanner {

    internal data class Fence(
        val info: String,
        val body: String,
        val isClosed: Boolean
    )

    fun fences(text: String): List<Fence> {
        val out = mutableListOf<Fence>()
        val lines = text.split('\n')
        var index = 0
        while (index < lines.size) {
            val marker = openingMarker(lines[index])
            if (marker == null) {
                index++
                continue
            }
            val info = lines[index].trim().removePrefix(marker).trim()
            val body = StringBuilder()
            var closed = false
            var cursor = index + 1
            while (cursor < lines.size) {
                val line = lines[cursor].trim()
                val isCloser = line.length >= marker.length &&
                    line.all { it == marker[0] }
                if (isCloser) {
                    closed = true
                    break
                }
                body.appendLine(lines[cursor])
                cursor++
            }
            out += Fence(info, body.toString().trimEnd('\n'), closed)
            index = if (closed) cursor + 1 else lines.size
        }
        return out
    }

    private fun openingMarker(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.length < 3) return null
        return when (trimmed[0]) {
            '`' -> if (trimmed.startsWith("```")) "```" else null
            '~' -> if (trimmed.startsWith("~~~")) "~~~" else null
            else -> null
        }
    }
}

/** Builds a compact line diff between old and new file contents. */
object LineDiff {

    fun changedFile(proposal: ProposalFile, oldContent: String?, id: String): ChangedFile {
        val newLines = proposal.content.split('\n')
        val oldLines = oldContent?.split('\n') ?: emptyList()
        var prefix = 0
        while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++
        var suffix = 0
        while (
            suffix < oldLines.size - prefix &&
            suffix < newLines.size - prefix &&
            oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
        ) suffix++

        val lines = mutableListOf<DiffLine>()
        var oldNumber = prefix + 1
        var newNumber = prefix + 1
        repeat(DiffContext) {
            val at = prefix - DiffContext + it
            if (at >= 0 && at < prefix) {
                lines += DiffLine(DiffLineType.CONTEXT, at + 1, at + 1, oldLines[at])
            }
        }
        for (index in prefix until oldLines.size - suffix) {
            lines += DiffLine(DiffLineType.DELETE, oldNumber++, null, oldLines[index])
        }
        for (index in prefix until newLines.size - suffix) {
            lines += DiffLine(DiffLineType.ADD, null, newNumber++, newLines[index])
        }
        run {
            val tail = oldLines.size - suffix
            for (offset in 0 until DiffContext) {
                val at = tail + offset
                if (at < oldLines.size) lines += DiffLine(DiffLineType.CONTEXT, at + 1, at + 1, oldLines[at])
            }
        }

        val additions = newLines.size - prefix - suffix
        val deletions = oldLines.size - prefix - suffix
        return ChangedFile(
            id = id,
            path = proposal.path,
            changeType = when {
                oldContent == null -> ChangeType.ADDED
                deletions == 0 && additions == 0 -> ChangeType.MODIFIED
                else -> ChangeType.MODIFIED
            },
            additions = additions.coerceAtLeast(0),
            deletions = deletions.coerceAtLeast(0),
            language = languageFor(proposal.path),
            diffLines = lines
        )
    }

    private const val DiffContext = 3

    fun languageFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "ts", "tsx" -> "TypeScript"
        "js", "jsx", "mjs", "cjs" -> "JavaScript"
        "py" -> "Python"
        "rs" -> "Rust"
        "go" -> "Go"
        "rb" -> "Ruby"
        "cs" -> "C#"
        "cpp", "cc", "c", "h", "hpp" -> "C/C++"
        "swift" -> "Swift"
        "md" -> "Markdown"
        "json", "jsonc" -> "JSON"
        "yml", "yaml" -> "YAML"
        "toml" -> "TOML"
        "xml" -> "XML"
        "html" -> "HTML"
        "css", "scss" -> "CSS"
        "sql" -> "SQL"
        "sh", "bat", "ps1" -> "Shell"
        "gradle" -> "Gradle"
        else -> "Text"
    }
}
