package com.agentdeck.app.data

private const val MAX_CLOUD_TURNS = 14
private const val MAX_CLOUD_TURN_CHARS = 6_000
private const val MAX_CLOUD_CONTEXT_CHARS = 30_000

// Local models fit a small on-device context; keep the prompt well inside it.
private const val MAX_LOCAL_TURNS = 8
private const val MAX_LOCAL_TURN_CHARS = 1_200
private const val MAX_LOCAL_CONTEXT_CHARS = 8_000

private val TEXT_ATTACHMENT_EXTENSIONS = setOf(
    "txt", "text", "md", "markdown", "html", "htm", "xhtml", "css", "scss",
    "js", "jsx", "ts", "tsx", "json", "xml", "svg", "yaml", "yml", "csv",
    "tsv", "log", "kt", "kts", "java", "py", "rb", "go", "rs", "swift",
    "c", "h", "cpp", "cs", "php", "sql", "sh", "bash", "zsh", "ps1",
    "toml", "ini", "properties", "gradle", "diff", "patch", "srt", "vtt"
)

internal fun attachmentIsText(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in TEXT_ATTACHMENT_EXTENSIONS

/**
 * The text a model sees for a message: the user's words plus a manifest of
 * attached files, which live in the project workspace so `read` can open them.
 */
internal fun Message.modelText(imagesVisible: Boolean = false): String {
    if (attachments.isEmpty()) return text
    val manifest = attachments.joinToString("\n") { file ->
        val size = when {
            file.size <= 0L -> ""
            file.size < 1_024 -> ", ${file.size} B"
            file.size < 1_048_576 -> ", ${file.size / 1_024} KB"
            else -> ", ${file.size / 1_048_576} MB"
        }
        val note = when {
            attachmentIsText(file.name) -> ""
            ImageInput.isImage(file.name) && imagesVisible -> " — image attached"
            ImageInput.isImage(file.name) -> " — image, not readable as text"
            else -> " — binary, not readable as text"
        }
        "- ${file.path} (${file.name}$size$note)"
    }
    val head = text.ifBlank { "Please review the attached files." }
    return "$head\n\nAttached files in the workspace (open text files with read path=\"…\"):\n$manifest"
}

/**
 * Builds the provider conversation from the task transcript.
 *
 * System notices are excluded. When [dropTrailingAssistant] is true (a retry),
 * trailing agent messages are omitted so a truncated partial reply is not sent
 * as the final assistant turn. The transcript still keeps that partial reply.
 */
internal fun buildCloudTurns(
    messages: List<Message>,
    dropTrailingAssistant: Boolean = false,
    imagesVisible: Boolean = false
): List<CloudTurn> {
    val filtered = messages.filter {
        (it.role == MessageRole.USER || it.role == MessageRole.AGENT) &&
            (it.text.isNotBlank() || it.attachments.isNotEmpty())
    }
    val selected = if (dropTrailingAssistant) {
        filtered.dropLastWhile { it.role == MessageRole.AGENT }
    } else {
        filtered
    }
    val history = selected.takeLast(MAX_CLOUD_TURNS)
    var total = 0
    val turns = ArrayDeque<CloudTurn>()
    for (message in history.asReversed()) {
        val text = message.modelText(imagesVisible).take(MAX_CLOUD_TURN_CHARS)
        if (total + text.length > MAX_CLOUD_CONTEXT_CHARS && turns.isNotEmpty()) break
        total += text.length
        turns.addFirst(
            CloudTurn(
                role = if (message.role == MessageRole.USER) "user" else "assistant",
                content = text
            )
        )
    }
    return turns.toList()
}

/** Same shape as [buildCloudTurns] with a budget that fits the on-device context. */
internal fun buildLocalTurns(
    messages: List<Message>,
    dropTrailingAssistant: Boolean = false
): List<CloudTurn> {
    val filtered = messages.filter {
        (it.role == MessageRole.USER || it.role == MessageRole.AGENT) &&
            (it.text.isNotBlank() || it.attachments.isNotEmpty())
    }
    val selected = if (dropTrailingAssistant) {
        filtered.dropLastWhile { it.role == MessageRole.AGENT }
    } else {
        filtered
    }
    val history = selected.takeLast(MAX_LOCAL_TURNS)
    var total = 0
    val turns = ArrayDeque<CloudTurn>()
    for (message in history.asReversed()) {
        val text = message.modelText().take(MAX_LOCAL_TURN_CHARS)
        if (total + text.length > MAX_LOCAL_CONTEXT_CHARS && turns.isNotEmpty()) break
        total += text.length
        turns.addFirst(
            CloudTurn(
                role = if (message.role == MessageRole.USER) "user" else "assistant",
                content = text
            )
        )
    }
    return turns.toList()
}
