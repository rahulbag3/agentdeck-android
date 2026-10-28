package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PhoneToolsTest {

    @Test
    fun `phone numbers keep dial characters and reject junk`() {
        assertEquals("+919876543210", PhoneArgs.normalizeNumber("+91 98765-43210"))
        assertEquals("98765", PhoneArgs.normalizeNumber("98765"))
        assertEquals("*#06#", PhoneArgs.normalizeNumber("*#06#"))
        assertNull(PhoneArgs.normalizeNumber("call mom"))
        assertNull(PhoneArgs.normalizeNumber("12"))
        assertNull(PhoneArgs.normalizeNumber("+91+99"))
        assertNull(PhoneArgs.normalizeNumber("91+99"))
    }

    @Test
    fun `clock parser accepts 24 hour time only`() {
        assertEquals(7 to 30, PhoneArgs.parseClock("07:30"))
        assertEquals(7 to 5, PhoneArgs.parseClock("7:05"))
        assertEquals(23 to 59, PhoneArgs.parseClock("23:59"))
        assertNull(PhoneArgs.parseClock("24:00"))
        assertNull(PhoneArgs.parseClock("12:60"))
        assertNull(PhoneArgs.parseClock("7.30"))
        assertNull(PhoneArgs.parseClock(""))
    }

    @Test
    fun `duration parser handles hours minutes seconds and bare minutes`() {
        assertEquals(600L, PhoneArgs.parseDurationSeconds("10m"))
        assertEquals(5_400L, PhoneArgs.parseDurationSeconds("1h30m"))
        assertEquals(7_200L, PhoneArgs.parseDurationSeconds("2h"))
        assertEquals(90L, PhoneArgs.parseDurationSeconds("90s"))
        assertEquals(600L, PhoneArgs.parseDurationSeconds("10"))
        assertNull(PhoneArgs.parseDurationSeconds("0"))
        assertNull(PhoneArgs.parseDurationSeconds("25h"))
        assertNull(PhoneArgs.parseDurationSeconds("soon"))
    }

    @Test
    fun `start parser rolls a past time to tomorrow`() {
        val now = LocalDateTime.of(2026, 10, 28, 18, 0)

        assertEquals(
            LocalDateTime.of(2026, 10, 28, 19, 0),
            PhoneArgs.parseStart("19:00", now)
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 29, 9, 0),
            PhoneArgs.parseStart("09:00", now)
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 30, 14, 30),
            PhoneArgs.parseStart("2026-10-30 14:30", now)
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 24, 9, 0),
            PhoneArgs.parseStart("2026-10-24", now)
        )
        assertNull(PhoneArgs.parseStart("tomorrow", now))
    }

    @Test
    fun `volume percent maps to the stream index`() {
        assertEquals(0, PhoneArgs.volumeIndex(0, 15))
        assertEquals(15, PhoneArgs.volumeIndex(100, 15))
        assertEquals(8, PhoneArgs.volumeIndex(50, 15))
        assertEquals(5, PhoneArgs.volumeIndex(33, 15))
        assertEquals(15, PhoneArgs.volumeIndex(150, 15))
        assertEquals(0, PhoneArgs.volumeIndex(50, 0))
    }

    @Test
    fun `settings panels cover the common screens`() {
        assertTrue(PhoneArgs.SETTINGS_PANELS.containsAll(
            listOf(
                "wifi", "bluetooth", "data", "airplane", "battery", "display",
                "sound", "apps", "app_info", "location", "accessibility",
                "developer", "notifications"
            )
        ))
        assertEquals(26, PhoneArgs.SETTINGS_PANELS.size)
    }

    @Test
    fun `communication tools require approval and safe tools do not`() {
        for (name in listOf("dial", "sms_compose", "share_text")) {
            val call = ToolCall(name, emptyMap(), "")
            assertTrue("$name should need approval", call.needsApproval)
        }
        for (name in listOf(
            "device_status", "list_apps", "open_app", "open_settings",
            "open_url", "set_alarm", "set_timer", "calendar_event", "set_volume"
        )) {
            val call = ToolCall(name, emptyMap(), "")
            assertFalse("$name should not need approval", call.needsApproval)
        }
    }

    @Test
    fun `phone tools parse from the fenced protocol`() {
        val call = ToolParser.parse(
            "```agentdeck-tool dial number=\"+919876543210\"\n```"
        ).single()

        assertEquals("dial", call.name)
        assertEquals("+919876543210", call.args["number"])
        assertEquals("dial +919876543210", call.label)
    }

    @Test
    fun `phone tools parse from JSON function calls`() {
        val call = ToolParser.parse(
            """{"function_calls":[{"name":"open_app","arguments":{"name":"WhatsApp"}}]}"""
        ).single()

        assertEquals("open_app", call.name)
        assertEquals("WhatsApp", call.args["name"])
        assertFalse(call.needsApproval)
    }
}
