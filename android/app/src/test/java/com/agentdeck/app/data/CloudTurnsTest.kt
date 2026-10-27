package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudTurnsTest {

    private fun message(id: String, role: MessageRole, text: String): Message =
        Message(id = id, role = role, text = text, at = 0)

    @Test
    fun `normal conversation keeps the trailing assistant turn`() {
        val messages = listOf(
            message("u1", MessageRole.USER, "first"),
            message("a1", MessageRole.AGENT, "answer"),
            message("u2", MessageRole.USER, "second"),
            message("a2", MessageRole.AGENT, "partial")
        )

        assertEquals(
            listOf(
                CloudTurn("user", "first"),
                CloudTurn("assistant", "answer"),
                CloudTurn("user", "second"),
                CloudTurn("assistant", "partial")
            ),
            buildCloudTurns(messages)
        )
    }

    @Test
    fun `retry drops trailing partial assistant turns`() {
        val messages = listOf(
            message("u1", MessageRole.USER, "first"),
            message("a1", MessageRole.AGENT, "answer"),
            message("u2", MessageRole.USER, "second"),
            message("a2", MessageRole.AGENT, "partial"),
            message("a3", MessageRole.AGENT, "another partial")
        )

        assertEquals(
            listOf(
                CloudTurn("user", "first"),
                CloudTurn("assistant", "answer"),
                CloudTurn("user", "second")
            ),
            buildCloudTurns(messages, dropTrailingAssistant = true)
        )
    }

    @Test
    fun `local retry drops trailing partial assistant turns`() {
        val messages = listOf(
            message("u1", MessageRole.USER, "first"),
            message("a1", MessageRole.AGENT, "partial")
        )

        assertEquals(
            listOf(CloudTurn("user", "first")),
            buildLocalTurns(messages, dropTrailingAssistant = true)
        )
    }

    @Test
    fun `system notices and blank messages are excluded`() {
        val messages = listOf(
            message("s1", MessageRole.SYSTEM, "Cloud request failed"),
            message("u1", MessageRole.USER, "hello"),
            message("a1", MessageRole.AGENT, "   "),
            message("a2", MessageRole.AGENT, "reply"),
            message("s2", MessageRole.SYSTEM, "Generation stopped")
        )

        assertEquals(
            listOf(
                CloudTurn("user", "hello"),
                CloudTurn("assistant", "reply")
            ),
            buildCloudTurns(messages)
        )
    }

    @Test
    fun `history is capped to the last fourteen turns`() {
        val messages = (0 until 20).map { index ->
            message("u$index", MessageRole.USER, "m$index")
        }

        val turns = buildCloudTurns(messages)

        assertEquals(14, turns.size)
        assertEquals("m6", turns.first().content)
        assertEquals("m19", turns.last().content)
    }

    @Test
    fun `each turn and the total context are bounded`() {
        val long = message("u1", MessageRole.USER, "x".repeat(7_000))
        assertEquals(6_000, buildCloudTurns(listOf(long)).single().content.length)

        val many = (0 until 6).map { index ->
            message("u$index", MessageRole.USER, "y".repeat(6_000))
        }
        val turns = buildCloudTurns(many)
        assertEquals(5, turns.size)
        assertEquals(30_000, turns.sumOf { it.content.length })
    }
}
