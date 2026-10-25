package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentToolsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `parses tool blocks with args and ignores normal fences`() {
        val text = """
            Let me look first.
            ```agentdeck-tool list path="src"
            ```
            ```kotlin
            not a tool
            ```
            ```agentdeck-tool read path="src/App.kt"
            ```
        """.trimIndent()

        val calls = ToolParser.parse(text)

        assertEquals(listOf("list", "read"), calls.map { it.name })
        assertEquals("src", calls[0].args["path"])
        assertEquals("src/App.kt", calls[1].args["path"])
    }

    @Test
    fun `write body is the file content`() {
        val text = """
            ```agentdeck-tool write path="notes/a.md"
            # Hello
            body line
            ```
        """.trimIndent()

        val calls = ToolParser.parse(text)

        assertEquals(1, calls.size)
        assertEquals("write", calls[0].name)
        assertTrue(calls[0].body.contains("# Hello"))
        assertTrue(calls[0].body.contains("body line"))
    }

    @Test
    fun `run and git commit need approval, reads do not`() {
        fun tool(text: String) = ToolParser.parse("$text\n```").first()
        val run = tool("```agentdeck-tool run command=\"ls\"")
        val commit = tool("```agentdeck-tool git action=\"commit\" message=\"m\"")
        val read = tool("```agentdeck-tool read path=\"a\"")
        val status = tool("```agentdeck-tool git action=\"status\"")

        assertTrue(run.needsApproval)
        assertTrue(commit.needsApproval)
        assertFalse(read.needsApproval)
        assertFalse(status.needsApproval)
    }

    @Test
    fun `unknown tools are ignored`() {
        assertTrue(ToolParser.parse("```agentdeck-tool delete path=\"x\"\n```").isEmpty())
    }

    @Test
    fun `results text frames tool output for the model`() {
        val text = ToolParser.resultsText(
            listOf(
                ToolOutcome(ToolCall("read", mapOf("path" to "a"), ""), "file contents here")
            )
        )
        assertTrue(text.startsWith("[tool results]"))
        assertTrue(text.contains("--- read a ---"))
        assertTrue(text.contains("file contents here"))
    }

    @Test
    fun `workspace confinement rejects escapes and accepts real paths`() {
        val root = temp.newFolder("ws")

        val inside = Workspace.confine(root, "src/main.kt")
        assertTrue(inside != null && inside.path.startsWith(root.canonicalPath))
        assertNull(Workspace.confine(root, "../outside.txt"))
        assertNull(Workspace.confine(root, "a/../.."))
        assertNull(Workspace.confine(root, "src\\escape"))
        assertNull(Workspace.confine(root, "c:/win"))
    }

    @Test
    fun `unified diff files split into changed entries`() {
        val diff = """
            diff --git a/src/App.kt b/src/App.kt
            index 111..222 100644
            --- a/src/App.kt
            +++ b/src/App.kt
            @@ -1,2 +1,2 @@
             line
            -old
            +new
            diff --git a/README.md b/README.md
            new file mode 100644
            --- /dev/null
            +++ b/README.md
            @@ -0,0 +1,1 @@
            +hello
        """.trimIndent()

        val files = UnifiedDiffFiles.parse(diff)

        assertEquals(2, files.size)
        assertEquals("src/App.kt", files[0].path)
        assertEquals(ChangeType.MODIFIED, files[0].changeType)
        assertEquals(1, files[0].additions)
        assertEquals(1, files[0].deletions)
        assertEquals("README.md", files[1].path)
        assertEquals(ChangeType.ADDED, files[1].changeType)
        assertEquals(1, files[1].additions)
    }
}
