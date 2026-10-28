package com.agentdeck.app.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Phone-control tools built exclusively on public Android intents and system
 * services. Nothing here uses root, hidden APIs, or an accessibility service.
 *
 * Communication tools ([dial], [composeSms], [shareText]) deliberately stop at
 * the system UI: the agent fills the dialer or message, and the user makes the
 * final call/send. That keeps the app inside the normal Android permission
 * model. Direct calling and SMS sending require runtime permissions and are a
 * separate, explicit opt-in.
 */
class PhoneTools(private val context: Context) {

    private val packageManager get() = context.packageManager

    // ---------------------------------------------------------------- status

    fun status(): String = buildString {
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("locale: ${Locale.getDefault().toLanguageTag()}")

        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        if (battery != null) {
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            appendLine(
                "battery: ${if (percent >= 0) "$percent%" else "unknown"}" +
                    if (charging) " (charging)" else ""
            )
        }

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "connected"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        appendLine("network: $transport${if (validated) " (internet)" else ""}")

        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val freeGb = stat.availableBytes / 1_073_741_824.0
        val totalGb = stat.totalBytes / 1_073_741_824.0
        appendLine("storage: ${"%.1f".format(Locale.US, freeGb)} GB free of ${"%.1f".format(Locale.US, totalGb)} GB")

        val audio = context.getSystemService(AudioManager::class.java)
        if (audio != null) {
            appendLine(
                "volume: music ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}/" +
                    "${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}, " +
                    "ring ${audio.getStreamVolume(AudioManager.STREAM_RING)}/" +
                    "${audio.getStreamMaxVolume(AudioManager.STREAM_RING)}"
            )
            appendLine(
                "ringer: " + when (audio.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> "silent"
                    AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                    else -> "normal"
                }
            )
        }

        val power = context.getSystemService(PowerManager::class.java)
        appendLine("screen: ${if (power?.isInteractive == true) "on" else "off"}")
        val minutes = SystemClock.elapsedRealtime() / 60_000
        appendLine("uptime: ${minutes / 60}h ${minutes % 60}m")
    }.trimEnd()

    // ------------------------------------------------------------------ apps

    fun listApps(): String {
        val apps = launchableApps()
        if (apps.isEmpty()) return "ERROR: no launchable apps found"
        val shown = apps.take(MAX_APP_LIST)
        return buildString {
            appendLine("${apps.size} launchable apps:")
            shown.forEach { appendLine("${it.label} — ${it.pkg}") }
            if (apps.size > shown.size) appendLine("… ${apps.size - shown.size} more")
        }.trimEnd()
    }

    fun openApp(name: String): String {
        val query = name.trim()
        if (query.isBlank()) return "ERROR: open_app needs name=\"…\""
        val apps = launchableApps()
        if (apps.isEmpty()) return "ERROR: no launchable apps found"

        val lower = query.lowercase(Locale.US)
        val exact = apps.filter {
            it.pkg.equals(lower, ignoreCase = true) || it.label.equals(lower, ignoreCase = true)
        }
        val matches = if (exact.isNotEmpty()) {
            exact
        } else if (lower.length >= 3) {
            apps.filter {
                it.label.lowercase(Locale.US).contains(lower) ||
                    it.pkg.lowercase(Locale.US).contains(lower)
            }
        } else {
            emptyList()
        }

        if (matches.isEmpty()) return "ERROR: no app matches \"$query\""
        if (matches.size > 1) {
            return "ERROR: multiple apps match \"$query\": " +
                matches.take(8).joinToString(", ") { "${it.label} (${it.pkg})" }
        }

        val app = matches.single()
        val intent = packageManager.getLaunchIntentForPackage(app.pkg)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(app.pkg)
        return launch(intent, "opened ${app.label} (${app.pkg})")
    }

    // -------------------------------------------------------------- settings

    fun openSettings(panel: String, packageName: String = ""): String {
        val key = panel.trim().lowercase(Locale.US)
        if (key.isBlank()) return "ERROR: open_settings needs panel=\"…\"; valid: ${PhoneArgs.SETTINGS_PANELS.joinToString()}"
        if (key !in PhoneArgs.SETTINGS_PANELS) {
            return "ERROR: unknown settings panel \"$panel\"; valid: ${PhoneArgs.SETTINGS_PANELS.joinToString()}"
        }
        if (key == "app_info" && packageName.isBlank()) {
            return "ERROR: open_settings panel=\"app_info\" needs package=\"…\""
        }
        if (key == "notifications" && packageName.isBlank()) {
            return "ERROR: open_settings panel=\"notifications\" needs package=\"…\""
        }

        val intent = when (key) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "data" -> Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            "privacy" -> Intent(Settings.ACTION_PRIVACY_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "developer" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            "date" -> Intent(Settings.ACTION_DATE_SETTINGS)
            "language" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
            "nfc" -> Intent(Settings.ACTION_NFC_SETTINGS)
            "cast" -> Intent(Settings.ACTION_CAST_SETTINGS)
            "vpn" -> Intent(Settings.ACTION_VPN_SETTINGS)
            "home" -> Intent(Settings.ACTION_HOME_SETTINGS)
            "device_info" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "write_settings" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            "default_apps" -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            "app_info" -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName.trim(), null)
            )
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName.trim())
            else -> return "ERROR: unsupported settings panel \"$panel\""
        }
        return launch(intent, "opened settings: $key")
    }

    // ------------------------------------------------------------ web/share

    fun openUrl(url: String): String {
        val trimmed = url.trim()
        if (!trimmed.matches(Regex("^https?://.+", RegexOption.IGNORE_CASE))) {
            return "ERROR: open_url needs a full http(s) URL"
        }
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)), "opened $trimmed")
    }

    fun shareText(text: String): String {
        if (text.isBlank()) return "ERROR: share_text needs text=\"…\""
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return launch(Intent.createChooser(send, "Share with"), "opened the share sheet")
    }

    // --------------------------------------------------------- communication

    fun dial(number: String): String {
        val normalized = PhoneArgs.normalizeNumber(number)
            ?: return "ERROR: dial needs a valid number, for example +919876543210"
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized"))
        return launch(intent, "opened the dialer for $normalized — press call to place it")
    }

    fun composeSms(number: String, text: String): String {
        val normalized = PhoneArgs.normalizeNumber(number)
            ?: return "ERROR: sms_compose needs a valid number, for example +919876543210"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$normalized")).apply {
            if (text.isNotBlank()) putExtra("sms_body", text)
        }
        return launch(
            intent,
            "opened Messages for $normalized with the message prefilled — press send"
        )
    }

    // -------------------------------------------------------- clock/calendar

    fun setAlarm(time: String, label: String = ""): String {
        val (hour, minute) = PhoneArgs.parseClock(time)
            ?: return "ERROR: set_alarm needs time=\"HH:mm\", for example 07:30"
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        return launchAmbiguous(intent, "opened Clock with alarm ${PhoneArgs.formatClock(hour, minute)} — confirm to save")
    }

    fun setTimer(duration: String, label: String = ""): String {
        val seconds = PhoneArgs.parseDurationSeconds(duration)
            ?: return "ERROR: set_timer needs duration=\"10m\", \"1h30m\", or \"90s\""
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt())
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        return launchAmbiguous(intent, "opened Clock with a ${PhoneArgs.formatDuration(seconds)} timer — confirm to start")
    }

    fun calendarEvent(
        title: String,
        start: String,
        durationMinutes: String = "",
        location: String = "",
        description: String = ""
    ): String {
        if (title.isBlank()) return "ERROR: calendar_event needs title=\"…\""
        val begin = PhoneArgs.parseStart(start)
            ?: return "ERROR: calendar_event needs start=\"yyyy-MM-dd HH:mm\" or \"HH:mm\""
        val minutes = durationMinutes.trim().toIntOrNull() ?: 60
        if (minutes !in 5..1_440) return "ERROR: duration_minutes must be 5–1440"
        val zone = ZoneId.systemDefault()
        val beginMillis = begin.atZone(zone).toInstant().toEpochMilli()
        val endMillis = begin.plusMinutes(minutes.toLong()).atZone(zone).toInstant().toEpochMilli()

        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.Events.TITLE, title)
            if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            if (description.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, description)
        }
        return launchAmbiguous(intent, "opened Calendar with \"$title\" at ${PhoneArgs.formatStart(begin)} — save to add")
    }

    // ---------------------------------------------------------------- volume

    fun setVolume(level: String, stream: String = "music"): String {
        val percent = level.trim().toIntOrNull()
            ?: return "ERROR: set_volume needs level=\"0\"–\"100\""
        if (percent !in 0..100) return "ERROR: set_volume level must be 0–100"
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return "ERROR: audio service unavailable"
        val streamId = PhoneArgs.audioStream(stream)
            ?: return "ERROR: stream must be music, ring, alarm, notification, or call"
        val max = audio.getStreamMaxVolume(streamId)
        val index = PhoneArgs.volumeIndex(percent, max)
        return try {
            audio.setStreamVolume(streamId, index, 0)
            "set $stream volume to $percent% ($index/$max)"
        } catch (e: SecurityException) {
            "ERROR: Android did not allow changing $stream volume: ${e.message?.take(120)}"
        }
    }

    // --------------------------------------------------------------- helpers

    private data class AppEntry(val label: String, val pkg: String)

    @Suppress("DEPRECATION")
    private fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(packageManager).toString() }
                    .getOrDefault(pkg)
                AppEntry(label, pkg)
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase(Locale.US) }
    }

    private fun launch(intent: Intent, success: String): String = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        success
    } catch (e: ActivityNotFoundException) {
        "ERROR: no app on this phone can handle that action"
    } catch (e: SecurityException) {
        "ERROR: Android blocked that action: ${e.message?.take(120)}"
    } catch (e: Exception) {
        "ERROR: ${e.javaClass.simpleName}: ${e.message?.take(120)}"
    }

    /**
     * Some launchers (MIUI) show an empty resolver/chooser when a non-system app
     * asks to handle an implicit intent, even though the app can see the
     * handlers. Resolve the handler here and launch it explicitly; the user
     * still confirms the action inside the clock or calendar app.
     */
    private fun launchAmbiguous(intent: Intent, success: String): String {
        val handlers = packageManager.queryIntentActivities(intent, 0)
        if (handlers.isEmpty()) return launch(intent, success)
        val preferred = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val chosen = preferred?.activityInfo
            ?.takeIf { it.packageName != "android" }
            ?: handlers.first().activityInfo
        val direct = Intent(intent).setComponent(
            ComponentName(chosen.packageName, chosen.name)
        )
        return launch(direct, success)
    }

    private companion object {
        const val MAX_APP_LIST = 120
    }
}

/**
 * Pure argument parsing for the phone tools. Kept separate from [PhoneTools]
 * so the rules can be unit-tested without an Android runtime.
 */
object PhoneArgs {

    val SETTINGS_PANELS = setOf(
        "wifi", "bluetooth", "data", "airplane", "battery", "display", "sound",
        "apps", "app_info", "location", "security", "privacy", "storage",
        "accessibility", "developer", "date", "language", "nfc", "cast", "vpn",
        "home", "device_info", "usage", "write_settings", "default_apps",
        "notifications"
    )

    private val CLOCK = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")
    private val DURATION = Regex(
        "^(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?$",
        RegexOption.IGNORE_CASE
    )
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val DATE_TIME_T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /** Returns null unless the input has at least three digits and safe tel characters. */
    fun normalizeNumber(raw: String): String? {
        val cleaned = raw.trim().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val digits = cleaned.count { it.isDigit() }
        val ussd = cleaned.length >= 3 && cleaned.any { it == '*' || it == '#' }
        if (digits < 3 && !ussd) return null
        if (cleaned.count { it == '+' } > 1) return null
        if (cleaned.contains('+') && !cleaned.startsWith("+")) return null
        return cleaned.take(32)
    }

    fun parseClock(raw: String): Pair<Int, Int>? {
        val match = CLOCK.matchEntire(raw.trim()) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    fun formatClock(hour: Int, minute: Int): String =
        "%02d:%02d".format(Locale.US, hour, minute)

    /**
     * Accepts `90s`, `10m`, `1h30m`, `2h`, or a bare number of minutes.
     * Returns null outside 1 second – 24 hours.
     */
    fun parseDurationSeconds(raw: String): Long? {
        val text = raw.trim().lowercase(Locale.US)
        if (text.isBlank()) return null
        if (text.all { it.isDigit() }) {
            val minutes = text.toLongOrNull() ?: return null
            return (minutes * 60).takeIf { it in 1L..86_400L }
        }
        val match = DURATION.matchEntire(text) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: 0
        val minutes = match.groupValues[2].toLongOrNull() ?: 0
        val seconds = match.groupValues[3].toLongOrNull() ?: 0
        if (hours == 0L && minutes == 0L && seconds == 0L) return null
        val total = hours * 3_600 + minutes * 60 + seconds
        return total.takeIf { it in 1L..86_400L }
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3_600
        val m = (seconds % 3_600) / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("${h}h")
            if (m > 0) add("${m}m")
            if (s > 0) add("${s}s")
        }.joinToString(" ")
    }

    /**
     * Accepts `yyyy-MM-dd HH:mm`, `yyyy-MM-dd'T'HH:mm`, `HH:mm` (today, or
     * tomorrow when that time already passed), or `yyyy-MM-dd` (09:00).
     */
    fun parseStart(raw: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val text = raw.trim()
        if (text.isBlank()) return null
        runCatching { LocalDateTime.parse(text, DATE_TIME) }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(text, DATE_TIME_T) }.getOrNull()?.let { return it }
        runCatching { LocalDate.parse(text) }.getOrNull()?.let {
            return it.atTime(LocalTime.of(9, 0))
        }
        val time = runCatching { LocalTime.parse(text) }.getOrNull() ?: return null
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun formatStart(value: LocalDateTime): String = value.format(DATE_TIME)

    fun volumeIndex(percent: Int, max: Int): Int {
        if (max <= 0) return 0
        val clamped = percent.coerceIn(0, 100)
        return ((clamped * max + 50) / 100).coerceIn(0, max)
    }

    fun audioStream(name: String): Int? = when (name.trim().lowercase(Locale.US)) {
        "", "music", "media" -> AudioManager.STREAM_MUSIC
        "ring", "ringer" -> AudioManager.STREAM_RING
        "alarm" -> AudioManager.STREAM_ALARM
        "notification", "notifications" -> AudioManager.STREAM_NOTIFICATION
        "call", "voice" -> AudioManager.STREAM_VOICE_CALL
        else -> null
    }
}
