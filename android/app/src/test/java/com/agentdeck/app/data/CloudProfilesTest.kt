package com.agentdeck.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudProfilesTest {

    @Test
    fun `profiles round-trip through the codec`() {
        val profiles = listOf(
            CloudProfile(
                id = "p1", label = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1",
                models = listOf("a", "b"), lastModel = "a", keySaved = true
            ),
            CloudProfile(id = "p2", label = "Groq", baseUrl = "https://api.groq.com/openai/v1")
        )

        val decoded = CloudProfilesCodec.decode(CloudProfilesCodec.encode(profiles))

        assertEquals(profiles, decoded)
    }

    @Test
    fun `decode is tolerant of junk and missing fields`() {
        assertTrue(CloudProfilesCodec.decode("not json").isEmpty())
        assertTrue(CloudProfilesCodec.decode("[]").isEmpty())

        val one = JSONArray()
            .put(JSONObject().put("id", "p1").put("baseUrl", "https://x.example/v1"))
            .put(JSONObject().put("id", "").put("baseUrl", "https://y.example/v1"))
            .toString()

        val decoded = CloudProfilesCodec.decode(one)
        assertEquals(1, decoded.size)
        assertEquals("p1", decoded[0].id)
        assertTrue(decoded[0].label.isNotBlank())
    }

    @Test
    fun `model ids parse from an openai-style response`() {
        val json = JSONObject()
            .put(
                "data",
                JSONArray()
                    .put(JSONObject().put("id", "gpt-4.1-mini"))
                    .put(JSONObject().put("id", "gpt-4.1-mini"))
                    .put(JSONObject().put("id", "gpt-4.1"))
                    .put(JSONObject().put("object", "model"))
            )
            .toString()

        assertEquals(listOf("gpt-4.1-mini", "gpt-4.1"), CloudClient.parseModelIds(json))
    }

    @Test
    fun `model id parsing handles junk`() {
        assertTrue(CloudClient.parseModelIds("nope").isEmpty())
        assertTrue(CloudClient.parseModelIds("""{"data": []}""").isEmpty())
    }
}
