package com.agentdeck.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun `parses headings and paragraphs`() {
        val blocks = parseMarkdown("# Title\n\nHello\nworld\n\n## Plan")

        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "Title"),
                MarkdownBlock.Paragraph("Hello world"),
                MarkdownBlock.Heading(2, "Plan")
            ),
            blocks
        )
    }

    @Test
    fun `parses flat bullet and numbered lists`() {
        val blocks = parseMarkdown("- one\n- two\n  continued\n\n3. third\n4. fourth")

        assertEquals(
            listOf(
                MarkdownBlock.BulletList(listOf("one", "two continued")),
                MarkdownBlock.NumberedList(3, listOf("third", "fourth"))
            ),
            blocks
        )
    }

    @Test
    fun `keeps fenced code verbatim and handles an unclosed fence`() {
        val fenced = parseMarkdown("```kotlin\nfun main() {\n    println(\"hi\")\n}\n```")
        assertEquals(
            listOf(
                MarkdownBlock.CodeFence(
                    "kotlin",
                    "fun main() {\n    println(\"hi\")\n}"
                )
            ),
            fenced
        )

        val unclosed = parseMarkdown("Intro\n\n```\nalpha\nbeta")
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("Intro"),
                MarkdownBlock.CodeFence("", "alpha\nbeta")
            ),
            unclosed
        )
    }

    @Test
    fun `parses quotes and rules`() {
        val blocks = parseMarkdown("> first\n> second\n\n---\n\nafter")

        assertEquals(
            listOf(
                MarkdownBlock.Quote("first\nsecond"),
                MarkdownBlock.Rule,
                MarkdownBlock.Paragraph("after")
            ),
            blocks
        )
    }

    @Test
    fun `parses inline code, bold, italic, links, and punctuation`() {
        val segments = parseInline(
            "Use `code` and **bold** and *italic* and [docs](https://example.com)."
        )

        assertEquals(
            listOf(
                InlineSegment("Use "),
                InlineSegment("code", code = true),
                InlineSegment(" and "),
                InlineSegment("bold", bold = true),
                InlineSegment(" and "),
                InlineSegment("italic", italic = true),
                InlineSegment(" and "),
                InlineSegment("docs", link = "https://example.com"),
                InlineSegment(".")
            ),
            segments
        )
    }

    @Test
    fun `parses escaped markers and ignores underscores inside words`() {
        assertEquals(
            listOf(InlineSegment("*literal* `code`")),
            parseInline("\\*literal\\* \\`code\\`")
        )
        assertEquals(
            listOf(InlineSegment("snake_case stays")),
            parseInline("snake_case stays")
        )
        assertEquals(
            listOf(InlineSegment("italic", italic = true)),
            parseInline("_italic_")
        )
    }

    @Test
    fun `only http and https links are clickable`() {
        assertTrue(safeLink("https://example.com"))
        assertTrue(safeLink("HTTP://10.0.2.2:8787/v1"))
        assertFalse(safeLink("javascript:alert(1)"))
        assertFalse(safeLink("file:///etc/passwd"))
        assertFalse(safeLink("mailto:test@example.com"))
        assertFalse(safeLink(""))
    }

    @Test
    fun `empty input produces no blocks`() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("  \n\n ").isEmpty())
        assertTrue(parseInline("").isEmpty())
    }
}
