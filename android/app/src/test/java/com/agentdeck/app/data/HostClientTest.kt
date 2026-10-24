package com.agentdeck.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostClientTest {

    @Test
    fun `https host url is accepted`() {
        assertNull(HostClient.baseUrlError("https://host.example.com:8787"))
    }

    @Test
    fun `loopback http is accepted without the insecure flag`() {
        assertNull(HostClient.baseUrlError("http://127.0.0.1:8787"))
        assertNull(HostClient.baseUrlError("http://10.0.2.2:8787"))
        assertNull(HostClient.baseUrlError("http://localhost:8787"))
    }

    @Test
    fun `lan http requires the insecure flag`() {
        val error = HostClient.baseUrlError("http://192.168.1.10:8787")
        assertNotNull(error)
        assertTrue(error!!.contains("unencrypted LAN"))
        assertNull(HostClient.baseUrlError("http://192.168.1.10:8787", allowInsecureLan = true))
    }

    @Test
    fun `invalid and credential-bearing urls are rejected`() {
        assertNotNull(HostClient.baseUrlError(""))
        assertNotNull(HostClient.baseUrlError("not a url"))
        assertNotNull(HostClient.baseUrlError("https://user:pass@host:8787"))
        assertNotNull(HostClient.baseUrlError("https://host:8787/?token=abc"))
    }

    @Test
    fun `endpoint joins the base url and path`() {
        val url = HostClient.endpointOrNull("http://192.168.1.10:8787/", "/v1/ping")
        assertEquals("http://192.168.1.10:8787/v1/ping", url.toString())
    }

    @Test
    fun `endpoint rejects unusable bases`() {
        assertNull(HostClient.endpointOrNull("", "/v1/ping"))
        assertNull(HostClient.endpointOrNull("ftp://host", "/v1/ping"))
    }

    @Test
    fun `isLoopback covers emulator and localhost forms`() {
        assertTrue(HostClient.isLoopback("10.0.2.2"))
        assertTrue(HostClient.isLoopback("localhost"))
        assertTrue(HostClient.isLoopback("127.0.0.1"))
        assertTrue(HostClient.isLoopback("::1"))
    }

    @Test
    fun `plain http url parses for loopback policy checks`() {
        assertNotNull("http://127.0.0.1:8787".toHttpUrlOrNull())
    }
}
