package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the real-world shapes models use when they try to
 * call tools. Every case here used to parse to zero calls and silently end the
 * agent run.
 */
class ToolParserResilienceTest {

    @Test
    fun `unclosed final fence still runs`() {
        val calls = ToolParser.parse("""```agentdeck-tool read path="src/App.kt"""")

        assertEquals(1, calls.size)
        assertEquals("read", calls[0].name)
        assertEquals("src/App.kt", calls[0].args["path"])
    }

    @Test
    fun `single quoted and unquoted args are accepted`() {
        val single = ToolParser.parse("```agentdeck-tool read path='src/App.kt'\n```")
        val bare = ToolParser.parse("```agentdeck-tool list path=src\n```")

        assertEquals("src/App.kt", single.single().args["path"])
        assertEquals("src", bare.single().args["path"])
    }

    @Test
    fun `header on the next line inside a plain fence`() {
        val text = """
            ```text
            agentdeck-tool grep pattern="TODO" path="src"
            ```
        """.trimIndent()

        val call = ToolParser.parse(text).single()
        assertEquals("grep", call.name)
        assertEquals("TODO", call.args["pattern"])
        assertEquals("src", call.args["path"])
    }

    @Test
    fun `bare protocol marker with the tool name on the next line`() {
        val text = """
            ```agentdeck-tool
            read path="a.kt"
            ```
        """.trimIndent()

        val call = ToolParser.parse(text).single()
        assertEquals("read", call.name)
        assertEquals("a.kt", call.args["path"])
    }

    @Test
    fun `colon after the tool marker and inline backticks are accepted`() {
        val colon = ToolParser.parse("```agentdeck-tool: list path=\"src\"\n```")
        val inline = ToolParser.parse("`agentdeck-tool read path=\"a.kt\"`")

        assertEquals("list", colon.single().name)
        assertEquals("src", colon.single().args["path"])
        assertEquals("read", inline.single().name)
        assertEquals("a.kt", inline.single().args["path"])
    }

    @Test
    fun `multiple calls in one fence are all parsed`() {
        val text = """
            ```agentdeck-tool
            read path="a.kt"
            read path="b.kt"
            ```
        """.trimIndent()

        assertEquals(listOf("a.kt", "b.kt"), ToolParser.parse(text).map { it.args["path"] })
    }

    @Test
    fun `json function_calls are accepted`() {
        val json = """
            {"function_calls":[{"name":"web_search","arguments":{"query":"kotlin 2.2"}}],"confidence":0.91}
        """.trimIndent()

        val call = ToolParser.parse(json).single()
        assertEquals("web_search", call.name)
        assertEquals("kotlin 2.2", call.args["query"])
    }

    @Test
    fun `openai style tool_calls with a string arguments blob are accepted`() {
        val json = """
            {"tool_calls":[{"type":"function","function":{"name":"read","arguments":"{\"path\":\"src/a.kt\"}"}}]}
        """.trimIndent()

        val call = ToolParser.parse(json).single()
        assertEquals("read", call.name)
        assertEquals("src/a.kt", call.args["path"])
    }

    @Test
    fun `low confidence needle output is ignored`() {
        val json = """{"function_calls":[{"name":"run","arguments":{"command":"rm -rf /"}}],"confidence":0.2}"""

        assertTrue(NeedleEngine.parseToolCalls(json).isEmpty())
    }

    @Test
    fun `confident needle output is accepted`() {
        val json = """{"function_calls":[{"name":"read","arguments":{"path":"a.kt"}}],"confidence":0.8}"""

        assertEquals("a.kt", NeedleEngine.parseToolCalls(json).single().args["path"])
    }

    @Test
    fun `write keeps the whole body but is marked truncated when the fence is unclosed`() {
        val text = """
            ```agentdeck-tool write path="notes/todo.md"
            # Todo

            - one
            - two
        """.trimIndent()

        val call = ToolParser.parse(text).single()
        assertEquals("notes/todo.md", call.args["path"])
        assertEquals("# Todo\n\n- one\n- two", call.body)
        assertTrue(call.truncated)
    }

    @Test
    fun `closed write is not marked truncated`() {
        val text = """
            ```agentdeck-tool write path="notes/todo.md"
            # Todo
            ```
        """.trimIndent()

        val call = ToolParser.parse(text).single()
        assertEquals("# Todo", call.body)
        assertFalse(call.truncated)
    }

    @Test
    fun `a json sample in prose is not treated as a tool call`() {
        val text = """
            Here is the response shape we expect:

            {"name":"read","arguments":{"path":"a.kt"}}
        """.trimIndent()

        assertTrue(ToolParser.parse(text).isEmpty())
    }

    @Test
    fun `a response that is pure json is still accepted`() {
        val text = """{"name":"read","arguments":{"path":"a.kt"}}"""

        assertEquals("a.kt", ToolParser.parse(text).single().args["path"])
    }

    @Test
    fun `normal fences without the protocol are ignored`() {
        val text = """
            Here is some code:

            ```kotlin
            val agentdeckTool = "nope"
            ```

            That is all.
        """.trimIndent()

        assertTrue(ToolParser.parse(text).isEmpty())
    }

    @Test
    fun `a malformed attempt is detectable so the loop can ask for a repair`() {
        val text = """
            ```agentdeck-tool
            path="a.kt"
            ```
        """.trimIndent()

        assertTrue(ToolParser.parse(text).isEmpty())
        assertTrue(ToolParser.looksLikeToolAttempt(text))
        assertTrue(ToolParser.repairPrompt().contains("closing fence"))
    }

    @Test
    fun `needle catalogue never offers write or commit`() {
        val json = NeedleEngine.toolsJson()

        assertFalse(json.contains("\"write\""))
        assertFalse(json.contains("\"commit\""))
    }

    @Test
    fun `dots xml call is inferred as web search`() {
        val text = """
            <dots_function_call>
            <agentdeck-tool">
            <parameter name="query">latest Kotlin release version 2025 2026 </parameter>
            </invoke>
            </dots_function_call>
        """.trimIndent()

        val call = ToolParser.parse(text).single()
        assertEquals("web_search", call.name)
        assertEquals("latest Kotlin release version 2025 2026", call.args["query"])
    }

    @Test
    fun `named xml invoke supplies its tool and path`() {
        val text = """
            <invoke name="read">
            <parameter name="path">src/App.kt</parameter>
            </invoke>
        """.trimIndent()

        val call = ToolParser.parse(text).single()
        assertEquals("read", call.name)
        assertEquals("src/App.kt", call.args["path"])
    }

    @Test
    fun `xml function shorthand still needs approval for a command`() {
        val text = """
            <function_call>
            <function=run>
            <parameter name="command">ls -la</parameter>
            </function_call>
        """.trimIndent()

        val call = ToolParser.parse(text).single()
        assertEquals("run", call.name)
        assertEquals("ls -la", call.args["command"])
        assertTrue(call.needsApproval)
    }

    @Test
    fun `xml write keeps its content as the file body`() {
        val text = """
            <invoke name="write">
            <parameter name="path">notes/todo.md</parameter>
            <parameter name="content" string="true"># Todo
            - one</parameter>
            </invoke>
        """.trimIndent()

        val call = ToolParser.parse(text).single()
        assertEquals("write", call.name)
        assertEquals("notes/todo.md", call.args["path"])
        assertEquals("# Todo\n- one", call.body)
    }

    @Test
    fun `xml entities are decoded in parameter values`() {
        val text = """
            <invoke name="web_search">
            <parameter name="query">kotlin &quot;coroutines&quot; &amp; flows</parameter>
            </invoke>
        """.trimIndent()

        assertEquals("kotlin \"coroutines\" & flows", ToolParser.parse(text).single().args["query"])
    }

    @Test
    fun `xml that names no known tool and has unknown params is ignored`() {
        val text = """
            <invoke name="delete">
            <parameter name="target">everything</parameter>
            </invoke>
        """.trimIndent()

        assertTrue(ToolParser.parse(text).isEmpty())
    }
}
