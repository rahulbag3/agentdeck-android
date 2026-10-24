package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostDiffTest {

    @Test
    fun `parses added, deleted, and context lines with line numbers`() {
        val diff = """
            diff --git a/src/app.ts b/src/app.ts
            index 111..222 100644
            --- a/src/app.ts
            +++ b/src/app.ts
            @@ -3,3 +3,4 @@
             const a = 1
            -const b = 2
            +const b = 3
            +const c = 4
             export { a }
        """.trimIndent()

        val lines = HostDiff.parse(diff)

        assertEquals(5, lines.size)
        assertEquals(DiffLineType.CONTEXT, lines[0].type)
        assertEquals(3, lines[0].oldNumber)
        assertEquals(3, lines[0].newNumber)
        assertEquals(DiffLineType.DELETE, lines[1].type)
        assertEquals(4, lines[1].oldNumber)
        assertEquals(null, lines[1].newNumber)
        assertEquals(DiffLineType.ADD, lines[2].type)
        assertEquals(null, lines[2].oldNumber)
        assertEquals(4, lines[2].newNumber)
        assertEquals("const b = 3", lines[2].text)
        assertEquals(DiffLineType.ADD, lines[3].type)
        assertEquals(5, lines[3].newNumber)
    }

    @Test
    fun `skips file headers before the first hunk`() {
        val diff = """
            new file mode 100644
            --- /dev/null
            +++ b/docs/guide.md
            @@ -0,0 +1,2 @@
            +# Guide
            +Body
        """.trimIndent()

        val lines = HostDiff.parse(diff)

        assertEquals(2, lines.size)
        assertEquals("Guide", lines[0].text.removePrefix("# "))
        assertEquals(DiffLineType.ADD, lines[1].type)
    }

    @Test
    fun `no newline markers are ignored`() {
        val diff = """
            @@ -1,1 +1,1 @@
            -old
            \ No newline at end of file
            +new
            \ No newline at end of file
        """.trimIndent()

        val lines = HostDiff.parse(diff)

        assertEquals(2, lines.size)
        assertEquals(DiffLineType.DELETE, lines[0].type)
        assertEquals(DiffLineType.ADD, lines[1].type)
    }

    @Test
    fun `empty diff yields no lines`() {
        assertTrue(HostDiff.parse("").isEmpty())
    }

    @Test
    fun `line count is capped`() {
        val builder = StringBuilder("@@ -1,1 +1,1 @@\n")
        repeat(3_000) { builder.append("+line $it\n") }

        val lines = HostDiff.parse(builder.toString())

        assertEquals(2_000, lines.size)
    }
}
