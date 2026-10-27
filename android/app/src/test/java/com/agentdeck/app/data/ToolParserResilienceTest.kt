package com.agentdeck.app.data

import org.json.JSONArray
import org.json.JSONObject
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
    fun `engine supplied low confidence triggered call is trusted`() {
        // The engine withholds < 0.1 into suppressed_calls. A below-floor call
        // in function_calls means a triggers regex matched, which is the
        // documented opt-in; re-filtering here would drop it.
        val json = """{"function_calls":[{"name":"read","arguments":{"path":"Main.kt"}}],"confidence":0.05}"""

        assertEquals("Main.kt", NeedleEngine.parseToolCalls(json).single().args["path"])
    }

    @Test
    fun `moderate confidence needle output is accepted`() {
        val json = """{"function_calls":[{"name":"web_search","arguments":{"query":"latest Kotlin"}}],"confidence":0.35}"""

        assertEquals("latest Kotlin", NeedleEngine.parseToolCalls(json).single().args["query"])
    }

    @Test
    fun `suppressed calls are never auto executed`() {
        val json = """
            {"function_calls":[],"suppressed_calls":[{"name":"run","arguments":{"command":"rm -rf /"}}],"confidence":0.02}
        """.trimIndent()

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
    fun `needle coding catalogue stays at five tools and never offers write or commit`() {
        val names = toolNames(NeedleEngine.toolsJson(coding = true))

        assertEquals(setOf("read", "list", "grep", "run", "git"), names)
        assertFalse(names.contains("write"))
        assertFalse(names.contains("commit"))
    }

    @Test
    fun `needle chat catalogue stays at four web and memory tools`() {
        val names = toolNames(NeedleEngine.toolsJson(coding = false))

        assertEquals(setOf("web_search", "web_fetch", "memory_save", "memory_recall"), names)
    }

    @Test
    fun `needle git action is a closed enum without commit`() {
        val action = toolByName(NeedleEngine.toolsJson(coding = true), "git")
            .getJSONObject("parameters")
            .getJSONObject("properties")
            .getJSONObject("action")
        val values = action.getJSONArray("enum")

        assertEquals(
            listOf("status", "diff", "log"),
            (0 until values.length()).map { values.getString(it) }
        )
    }

    @Test
    fun `needle grep requires a pattern and accepts an optional path`() {
        val parameters = toolByName(NeedleEngine.toolsJson(coding = true), "grep")
            .getJSONObject("parameters")
        val required = parameters.getJSONArray("required")

        assertEquals(listOf("pattern"), (0 until required.length()).map { required.getString(it) })
        assertTrue(parameters.getJSONObject("properties").has("pattern"))
        assertTrue(parameters.getJSONObject("properties").has("path"))
    }

    @Test
    fun `needle list defaults to the workspace root`() {
        val path = toolByName(NeedleEngine.toolsJson(coding = true), "list")
            .getJSONObject("parameters")
            .getJSONObject("properties")
            .getJSONObject("path")

        assertEquals(".", path.getString("default"))
    }

    @Test
    fun `needle fetch url pattern is compiled from the schema`() {
        val url = toolByName(NeedleEngine.toolsJson(coding = false), "web_fetch")
            .getJSONObject("parameters")
            .getJSONObject("properties")
            .getJSONObject("url")

        assertEquals("^https?://", url.getString("pattern"))
    }

    @Test
    fun `needle memory tools are not trigger forced`() {
        assertFalse(toolByName(NeedleEngine.toolsJson(coding = false), "memory_save").has("triggers"))
        assertFalse(toolByName(NeedleEngine.toolsJson(coding = false), "memory_recall").has("triggers"))
    }

    @Test
    fun `needle catalogues declare descriptions and typed required arguments`() {
        for (coding in listOf(true, false)) {
            val tools = JSONArray(NeedleEngine.toolsJson(coding))
            for (index in 0 until tools.length()) {
                val tool = tools.getJSONObject(index)
                val name = tool.getString("name")
                assertTrue("$name has no description", tool.getString("description").isNotBlank())

                val parameters = tool.getJSONObject("parameters")
                val properties = parameters.getJSONObject("properties")
                val required = parameters.getJSONArray("required")
                assertTrue("$name has no required argument", required.length() > 0)
                for (argIndex in 0 until required.length()) {
                    val arg = required.getString(argIndex)
                    assertTrue("$name.$arg is not declared", properties.has(arg))
                    assertTrue(
                        "$name.$arg has no type",
                        properties.getJSONObject(arg).getString("type").isNotBlank()
                    )
                }
            }
        }
    }

    @Test
    fun `needle explicit fetch trigger only matches a url`() {
        val triggers = toolByName(NeedleEngine.toolsJson(coding = false), "web_fetch")
            .getJSONArray("triggers")

        assertEquals("https?://", triggers.getString(0))
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

    private fun toolNames(toolsJson: String): Set<String> {
        val tools = JSONArray(toolsJson)
        return (0 until tools.length())
            .map { tools.getJSONObject(it).getString("name") }
            .toSet()
    }

    private fun toolByName(toolsJson: String, name: String): JSONObject {
        val tools = JSONArray(toolsJson)
        for (index in 0 until tools.length()) {
            val tool = tools.getJSONObject(index)
            if (tool.getString("name") == name) return tool
        }
        throw AssertionError("tool $name not found in catalogue")
    }
}
