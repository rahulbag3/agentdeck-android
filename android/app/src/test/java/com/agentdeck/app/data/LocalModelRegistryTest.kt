package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelRegistryTest {

    private val model = ImportedModel(
        id = "local-abcd1234",
        name = "Qwen2.5-Coder 1.5B",
        path = "/data/user/0/com.agentdeck.app/files/models/local-abcd1234.gguf",
        sizeBytes = 1_100_000_000L,
        importedAt = 1_700_000_000_000L
    )

    @Test
    fun `encode then decode preserves imported models`() {
        val decoded = LocalModelRegistry.decode(LocalModelRegistry.encode(listOf(model)))
        assertEquals(listOf(model), decoded)
    }

    @Test
    fun `empty catalogue round trips`() {
        assertEquals(emptyList<ImportedModel>(), LocalModelRegistry.decode(LocalModelRegistry.encode(emptyList())))
    }

    @Test
    fun `malformed json yields an empty catalogue`() {
        assertTrue(LocalModelRegistry.decode("not json").isEmpty())
        assertTrue(LocalModelRegistry.decode("{}").isEmpty())
    }

    @Test
    fun `rows without an id or path are skipped`() {
        val json = """
            [
              {"id": "local-a", "name": "Good", "path": "/tmp/a.gguf"},
              {"name": "No id", "path": "/tmp/b.gguf"},
              {"id": "local-c", "name": "No path"},
              "not an object"
            ]
        """.trimIndent()

        val decoded = LocalModelRegistry.decode(json)

        assertEquals(1, decoded.size)
        assertEquals("local-a", decoded.first().id)
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val decoded = LocalModelRegistry.decode("""[{"id":"local-x","path":"/tmp/x.gguf"}]""")
        assertEquals("Local model", decoded.first().name)
        assertEquals(0L, decoded.first().sizeBytes)
        assertEquals(0L, decoded.first().importedAt)
    }
}
