package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class HarnessExtrasTest {

    @Test
    fun `memory store saves, recalls by term, caps entries`() {
        val dir = Files.createTempDirectory("mem").toFile()
        val store = MemoryStore(dir)
        store.save("User prefers concise answers")
        store.save("Deploy target is the staging cluster")

        assertEquals(2, store.all().size)
        assertTrue(store.recall("concise").first().text.contains("concise"))
        assertEquals(2, store.recall("nothing matches this").size) // falls back to recent
    }

    @Test
    fun `html text extraction strips scripts, tags, entities`() {
        val tools = WebTools()
        val html = "<html><head><title>My &amp; Page</title></head>" +
            "<body><script>evil()</script><style>.x{}</style>" +
            "<h1>Hello&nbsp;world</h1><p>A &lt;b&gt; tag &amp; text</p></body></html>"
        val text = tools.extractText(html)
        assertTrue(!text.contains("evil") && !text.contains(".x"))
        assertTrue(text.contains("Hello world"))
        assertTrue(text.contains("A <b> tag & text"))
    }

    @Test
    fun `system prompt embeds memories and instructions per mode`() {
        val chat = ToolParser.buildSystemPrompt(false, "- likes tea", "Be brief")
        assertTrue(chat.contains("web_search") && chat.contains("Be brief") && chat.contains("likes tea"))
        val coding = ToolParser.buildSystemPrompt(true, "", null)
        assertTrue(coding.contains("agentdeck-tool write") && !coding.contains("web_search query=\"today"))
    }

    @Test
    fun `duckduckgo results parse out of wrapped redirects`() {
        val tools = WebTools()
        val html = """
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa">Result <b>One</b></a>
            <a class="result__snippet" href="#">The <b>first</b> snippet</a>
            <a rel="nofollow" class="result__a" href="https://direct.example.com/b">Result Two</a>
        """.trimIndent()
        val hits = tools.parseDuckDuckGo(html)
        assertEquals(2, hits.size)
        assertEquals("https://example.com/a", hits[0].url)
        assertEquals("Result One", hits[0].title)
        assertEquals("The first snippet", hits[0].snippet)
        assertEquals("https://direct.example.com/b", hits[1].url)
    }
}
