package com.agentdeck.app.data

import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the phone tools against the real device services and launchers.
 *
 * These launch system UI, so run them one method at a time when the foreground
 * activity matters:
 * `adb shell am instrument -w -e class com.agentdeck.app.data.PhoneToolsDeviceTest#openUrlOpensBrowser com.agentdeck.app.test/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
class PhoneToolsDeviceTest {

    private val tools: PhoneTools
        get() = PhoneTools(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun alarmHandlersAreVisibleToTheApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handlers = context.packageManager
            .queryIntentActivities(android.content.Intent(AlarmClock.ACTION_SET_ALARM), 0)
        val detail = handlers.joinToString { info ->
            "${info.activityInfo.packageName}/${info.activityInfo.name} exported=${info.activityInfo.exported}"
        }
        android.util.Log.i("PhoneToolsDeviceTest", "alarm handlers: $detail")
        assertTrue("handlers=$detail", handlers.isNotEmpty())
    }

    @Test
    fun listAppsReturnsInstalledApps() {
        val result = tools.listApps()
        assertFalse(result, result.startsWith("ERROR"))
        assertTrue(result, result.contains("launchable apps:"))
    }

    @Test
    fun openUrlOpensBrowser() {
        val result = tools.openUrl("https://example.com")
        assertFalse(result, result.startsWith("ERROR"))
        assertTrue(result, result.contains("https://example.com"))
    }

    @Test
    fun shareTextOpensSystemSheet() {
        val result = tools.shareText("meeting notes")
        assertFalse(result, result.startsWith("ERROR"))
        assertTrue(result, result.contains("share sheet"))
    }

    @Test
    fun setAlarmOpensClock() {
        val result = tools.setAlarm("07:30", "Standup")
        assertFalse(result, result.startsWith("ERROR"))
        assertTrue(result, result.contains("07:30"))
    }

    @Test
    fun setTimerOpensClock() {
        val result = tools.setTimer("10m", "Tea")
        assertFalse(result, result.startsWith("ERROR"))
        assertTrue(result, result.contains("10m"))
    }

    @Test
    fun calendarEventOpensCalendar() {
        val result = tools.calendarEvent(
            title = "Dentist",
            start = "2026-10-30 14:30",
            durationMinutes = "45"
        )
        assertFalse(result, result.startsWith("ERROR"))
        assertTrue(result, result.contains("Dentist"))
    }
}
