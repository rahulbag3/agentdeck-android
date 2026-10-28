package com.agentdeck.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyExportTest {

    @Test
    fun `code file uses the fence language`() {
        val file = codeReplyFile("python", "print('hi')", "greeting")
        assertEquals("greeting.py", file.name)
        assertEquals("text/x-python", file.mime)
        assertEquals("print('hi')", file.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `unlabelled code sniffs html and json`() {
        assertEquals("page.html", codeReplyFile("code", "<!DOCTYPE html><html></html>", "page").name)
        assertEquals("data.json", codeReplyFile("", "{\"ok\":true}", "data").name)
        assertEquals("agentdeck-reply.txt", codeReplyFile("code", "plain words", "agentdeck-reply").name)
    }

    @Test
    fun `unknown language still gets a sane file`() {
        val file = codeReplyFile("brainfuck++", "+++", "odd")
        assertEquals("odd.brainfuck", file.name)
        assertEquals("text/plain", file.mime)
    }

    @Test
    fun `dotted languages map to real extensions`() {
        assertEquals("main.cpp", codeReplyFile("c++", "int main(){}", "main").name)
        assertEquals("script.sh", codeReplyFile("bash", "echo hi", "script").name)
        assertEquals("schema.yaml", codeReplyFile("yaml", "a: 1", "schema").name)
    }

    @Test
    fun `file base is a slug of the first line`() {
        assertEquals("weather-in-lahore", replyFileBase("## Weather in Lahore\nSunny, 32 C."))
        assertEquals("p-hi-p", replyFileBase("```html\n<p>hi</p>\n```"))
        assertEquals("agentdeck-reply", replyFileBase(""))
        assertFalse(replyFileBase("A".repeat(200)).length > 48)
    }

    @Test
    fun `file base skips html tags and prefers a heading`() {
        assertEquals(
            "here-is-the-file",
            replyFileBase("<!DOCTYPE html>\n<html>\nHere is the file.\n```html\n<p>hi</p>\n```")
        )
        assertEquals("setup-steps", replyFileBase("Sure, here you go.\n\n## Setup steps\n1. Run it"))
    }

    @Test
    fun `html export renders markdown and escapes model output`() {
        val html = htmlDocument(
            "Test",
            "## Title\n\n**bold** and `<tag>`\n\n```kotlin\nval x = 1 < 2\n```\n\n<script>alert(1)</script>"
        )
        assertTrue(html.contains("<h2>Title</h2>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("&lt;tag&gt;"))
        assertTrue(html.contains("class=\"language-kotlin\""))
        assertTrue(html.contains("val x = 1 &lt; 2"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script>alert(1)</script>"))
    }

    @Test
    fun `draft files carry the right mime and extension`() {
        assertEquals("note.md", markdownReplyFile("hi", "note").name)
        assertEquals("text/markdown", markdownReplyFile("hi", "note").mime)
        assertEquals("note.txt", textReplyFile("hi", "note").name)
        assertEquals("note.html", htmlReplyFile("hi", "note").name)
    }
}
