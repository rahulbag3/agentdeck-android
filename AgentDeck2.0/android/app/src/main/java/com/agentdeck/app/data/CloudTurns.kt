package com.agentdeck.app.data

private const val MAX_CLOUD_TURNS = 14
private const val MAX_CLOUD_TURN_CHARS = 6_000
private const val MAX_CLOUD_CONTEXT_CHARS = 30_000

/**
 * Builds the provider conversation from the task transcript.
 *
 * System notices are excluded. When [dropTrailingAssistant] is true (a retry),
 * trailing agent messages are omitted so a truncated partial reply is not sent
 * as the final assistant turn. The transcript still keeps that partial reply.
 */
internal fun buildCloudTurns(
    messages: List<Message>,
    dropTrailingAssistant: Boolean = false
): List<CloudTurn> {
    val filtered = messages.filter {
        (it.role == MessageRole.USER || it.role == MessageRole.AGENT) && it.text.isNotBlank()
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
        val text = message.text.take(MAX_CLOUD_TURN_CHARS)
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
