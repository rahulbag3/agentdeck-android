package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudClientTest {

    @Test
    fun `accepts https remote endpoints`() {
        assertNull(CloudClient.baseUrlError("https://api.openai.com/v1"))
        assertNull(CloudClient.baseUrlError("https://openrouter.ai/api/v1"))
    }

    @Test
    fun `allows cleartext only for local test hosts`() {
        assertNull(CloudClient.baseUrlError("http://10.0.2.2:8787/v1"))
        assertNull(CloudClient.baseUrlError("http://localhost:8787/v1"))
        assertNull(CloudClient.baseUrlError("http://127.0.0.1:8787/v1"))
        assertEquals(
            "Use https:// for a remote API. Cleartext is only allowed for local test servers.",
            CloudClient.baseUrlError("http://example.com/v1")
        )
    }

    @Test
    fun `rejects credentials, queries, fragments, and malformed urls`() {
        assertTrue(CloudClient.baseUrlError("https://user:pass@example.com/v1")!!.contains("username or password"))
        assertTrue(CloudClient.baseUrlError("https://example.com/v1?key=abc")!!.contains("query string"))
        assertTrue(CloudClient.baseUrlError("https://example.com/v1#frag")!!.contains("query string"))
        assertTrue(CloudClient.baseUrlError("not a url")!!.contains("full URL"))
        assertTrue(CloudClient.baseUrlError("")!!.contains("base URL"))
    }

    @Test
    fun `builds the chat completions endpoint exactly once`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            CloudClient.endpointOrNull("https://api.openai.com/v1")?.toString()
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            CloudClient.endpointOrNull("https://api.openai.com/v1/chat/completions")?.toString()
        )
        assertEquals(
            "http://10.0.2.2:8787/v1/chat/completions",
            CloudClient.endpointOrNull("http://10.0.2.2:8787/v1/")?.toString()
        )
    }

    @Test
    fun `endpoint builder rejects disallowed or decorated urls`() {
        assertNull(CloudClient.endpointOrNull("http://example.com/v1"))
        assertNull(CloudClient.endpointOrNull("https://user:pass@example.com/v1"))
        assertNull(CloudClient.endpointOrNull("https://example.com/v1?key=abc"))
        assertNull(CloudClient.endpointOrNull("file:///tmp/chat"))
        assertNull(CloudClient.endpointOrNull(""))
    }

    @Test
    fun `maps known providers and local gateways`() {
        assertEquals("OpenAI", CloudClient.providerLabel("https://api.openai.com/v1"))
        assertEquals("OpenRouter", CloudClient.providerLabel("https://openrouter.ai/api/v1"))
        assertEquals("DeepSeek", CloudClient.providerLabel("https://api.deepseek.com/v1"))
        assertEquals("xAI", CloudClient.providerLabel("https://api.x.ai/v1"))
        assertEquals("Local gateway", CloudClient.providerLabel("http://10.0.2.2:8787/v1"))
        assertEquals("Cloud API", CloudClient.providerLabel("https://example.com/v1"))
    }

    @Test
    fun `loopback check is exact`() {
        assertTrue(CloudClient.isLoopback("10.0.2.2"))
        assertTrue(CloudClient.isLoopback("localhost"))
        assertTrue(CloudClient.isLoopback("127.0.0.1"))
        assertFalse(CloudClient.isLoopback("127.0.0.1.example.com"))
        assertFalse(CloudClient.isLoopback("10.0.2.20"))
        assertFalse(CloudClient.isLoopback("example.com"))
    }
}
