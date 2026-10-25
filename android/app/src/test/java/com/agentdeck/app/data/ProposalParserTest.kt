package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalParserTest {

    @Test
    fun `parses a single agentdeck-file block`() {
        val text = """
            Here is the fix:
            ```agentdeck-file path="src/main.kt"
            fun main() {}
            ```
            Done.
        """.trimIndent()

        val files = ProposalParser.parse(text)

        assertEquals(1, files.size)
        assertEquals("src/main.kt", files[0].path)
        assertEquals("fun main() {}", files[0].content)
    }

    @Test
    fun `ignores normal fenced code blocks`() {
        val text = """
            ```kotlin
            fun unrelated() {}
            ```
        """.trimIndent()

        assertTrue(ProposalParser.parse(text).isEmpty())
    }

    @Test
    fun `later blocks for the same path win`() {
        val text = """
            ```agentdeck-file path="a.txt"
            first
            ```
            ```agentdeck-file path="a.txt"
            second
            ```
        """.trimIndent()

        val files = ProposalParser.parse(text)
        assertEquals(1, files.size)
        assertEquals("second", files[0].content)
    }

    @Test
    fun `parses multiple paths and preserves order`() {
        val text = """
            ```agentdeck-file path="b.txt"
            B
            ```
            ```agentdeck-file path="a.txt"
            A
            ```
        """.trimIndent()

        val files = ProposalParser.parse(text)
        assertEquals(listOf("b.txt", "a.txt"), files.map { it.path })
    }

    @Test
    fun `rejects invalid paths`() {
        val text = """
            ```agentdeck-file path="/etc/passwd"
            no
            ```
            ```agentdeck-file path="../escape.txt"
            no
            ```
            ```agentdeck-file
            no path
            ```
        """.trimIndent()

        assertTrue(ProposalParser.parse(text).isEmpty())
    }

    @Test
    fun `skips unclosed fences`() {
        val text = """
            ```agentdeck-file path="open.txt"
            never closed
        """.trimIndent()

        assertTrue(ProposalParser.parse(text).isEmpty())
    }

    @Test
    fun `supports nested backticks inside a four-backtick fence`() {
        val text = """
            ````agentdeck-file path="doc.md"
            ```kotlin
            nested
            ```
            ````
        """.trimIndent()

        val files = ProposalParser.parse(text)
        assertEquals(1, files.size)
        assertTrue(files[0].content.contains("```kotlin"))
    }

    @Test
    fun `accumulates across agent messages with last write wins`() {
        val messages = listOf(
            Message(id = "1", role = MessageRole.USER, text = "fix it", at = 1),
            Message(
                id = "2", role = MessageRole.AGENT, at = 2,
                text = "```agentdeck-file path=\"x.txt\"\nold\n```"
            ),
            Message(
                id = "3", role = MessageRole.AGENT, at = 3,
                text = "```agentdeck-file path=\"y.txt\"\nnew file\n```"
            ),
            Message(
                id = "4", role = MessageRole.AGENT, at = 4,
                text = "```agentdeck-file path=\"x.txt\"\nnew\n```"
            )
        )

        val files = ProposalParser.accumulate(messages)

        assertEquals(2, files.size)
        assertEquals("new", files.first { it.path == "x.txt" }.content)
        assertEquals("new file", files.first { it.path == "y.txt" }.content)
    }

    @Test
    fun `line diff marks new files as added`() {
        val file = LineDiff.changedFile(
            ProposalFile("src/new.ts", "a\nb"),
            oldContent = null,
            id = "f1"
        )

        assertEquals(ChangeType.ADDED, file.changeType)
        assertEquals(2, file.additions)
        assertEquals(0, file.deletions)
        assertEquals("TypeScript", file.language)
        assertTrue(file.diffLines.all { it.type == DiffLineType.ADD })
    }

    @Test
    fun `line diff computes additions and deletions with shared context`() {
        val old = "one\ntwo\nthree\nfour\nfive\nsix\nseven"
        val new = "one\ntwo\nTHREE\nfour\nfive\nsix\nseven"

        val file = LineDiff.changedFile(ProposalFile("a.txt", new), oldContent = old, id = "f1")

        assertEquals(ChangeType.MODIFIED, file.changeType)
        assertEquals(1, file.additions)
        assertEquals(1, file.deletions)
        assertTrue(file.diffLines.any { it.type == DiffLineType.CONTEXT && it.text == "one" })
        assertTrue(file.diffLines.any { it.type == DiffLineType.DELETE && it.text == "three" })
        assertTrue(file.diffLines.any { it.type == DiffLineType.ADD && it.text == "THREE" })
    }

    @Test
    fun `identical content produces an empty diff`() {
        val file = LineDiff.changedFile(
            ProposalFile("a.txt", "same\nlines"),
            oldContent = "same\nlines",
            id = "f1"
        )

        assertEquals(0, file.additions)
        assertEquals(0, file.deletions)
    }
}
