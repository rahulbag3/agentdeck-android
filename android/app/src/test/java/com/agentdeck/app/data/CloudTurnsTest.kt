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
    fun `attachments add a workspace manifest the model can read`() {
        val messages = listOf(
            Message(
                id = "u1",
                role = MessageRole.USER,
                text = "Summarise this log",
                at = 0,
                attachments = listOf(
                    Attachment("parser.log", "attachments/1-parser.log", 2_048)
                )
            )
        )

        val turn = buildCloudTurns(messages).single()

        assertEquals("user", turn.role)
        assertEquals(
            "Summarise this log\n\nAttached files in the workspace (open text files with read path=\"…\"):\n" +
                "- attachments/1-parser.log (parser.log, 2 KB)",
            turn.content
        )
    }

    @Test
    fun `attachment only messages survive the blank filter`() {
        val messages = listOf(
            Message(
                id = "u1",
                role = MessageRole.USER,
                text = "",
                at = 0,
                attachments = listOf(Attachment("notes.md", "attachments/2-notes.md", 900))
            )
        )

        assertEquals(
            listOf(CloudTurn("user", "Please review the attached files.\n\nAttached files in the workspace (open text files with read path=\"…\"):\n- attachments/2-notes.md (notes.md, 900 B)")),
            buildLocalTurns(messages)
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

    @Test
    fun `image attachments are marked unreadable until images are sent`() {
        val messages = listOf(
            Message(
                id = "u1",
                role = MessageRole.USER,
                text = "What is this?",
                at = 0,
                attachments = listOf(Attachment("photo.jpg", "attachments/1-photo.jpg", 58_000))
            )
        )

        val textOnly = buildCloudTurns(messages).single().content
        assertEquals(true, textOnly.contains("photo.jpg, 56 KB — image, not readable as text"))

        val withImages = buildCloudTurns(messages, imagesVisible = true).single().content
        assertEquals(true, withImages.contains("photo.jpg, 56 KB — image attached"))
    }

    @Test
    fun `image detection is based on the file extension`() {
        assertEquals(true, ImageInput.isImage("photo.jpg"))
        assertEquals(true, ImageInput.isImage("screenshot.PNG"))
        assertEquals(true, ImageInput.isImage("design.webp"))
        assertEquals(false, ImageInput.isImage("report.pdf"))
        assertEquals(false, ImageInput.isImage("notes.md"))
    }

    @Test
    fun `text attachment detection covers code and documents`() {
        assertEquals(true, attachmentIsText("notes.md"))
        assertEquals(true, attachmentIsText("page.html"))
        assertEquals(true, attachmentIsText("Main.kt"))
        assertEquals(true, attachmentIsText("data.csv"))
        assertEquals(false, attachmentIsText("photo.jpg"))
        assertEquals(false, attachmentIsText("report.pdf"))
        assertEquals(false, attachmentIsText("archive.zip"))
    }
}
