package com.agentdeck.app.data

/**
 * Converts a unified diff hunk body into the app's [DiffLine] model.
 * File headers and hunk metadata are skipped; only hunk content is rendered.
 */
object HostDiff {

    private val HUNK = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
    private const val MAX_LINES = 2_000

    fun parse(unified: String): List<DiffLine> {
        val lines = ArrayList<DiffLine>()
        var oldLine = 0
        var newLine = 0
        var inHunk = false
        for (raw in unified.lineSequence()) {
            if (raw.startsWith("@@")) {
                val match = HUNK.find(raw)
                if (match != null) {
                    oldLine = match.groupValues[1].toInt()
                    newLine = match.groupValues[3].toInt()
                    inHunk = true
                }
                continue
            }
            if (!inHunk) continue
            if (raw.startsWith("\\ No newline")) continue
            when {
                raw.startsWith("+") -> lines += DiffLine(
                    DiffLineType.ADD,
                    oldNumber = null,
                    newNumber = newLine++,
                    text = raw.drop(1)
                )
                raw.startsWith("-") -> lines += DiffLine(
                    DiffLineType.DELETE,
                    oldNumber = oldLine++,
                    newNumber = null,
                    text = raw.drop(1)
                )
                raw.startsWith(" ") || raw.isEmpty() -> lines += DiffLine(
                    DiffLineType.CONTEXT,
                    oldNumber = oldLine++,
                    newNumber = newLine++,
                    text = raw.removePrefix(" ")
                )
            }
            if (lines.size >= MAX_LINES) break
        }
        return lines
    }
}
