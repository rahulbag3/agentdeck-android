package com.agentdeck.app.data

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Centralized logging: logcat always, Crashlytics when Firebase is configured.
 * Every Crashlytics call is guarded so logging can never take the app down.
 */
object Logger {

    private const val TAG = "AgentDeck"

    private val crashlytics: FirebaseCrashlytics? by lazy {
        runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
    }

    enum class Level { DEBUG, INFO, WARN, ERROR, FATAL }

    fun debug(message: String, tags: Map<String, Any?>? = null) {
        Log.d(TAG, render(message, tags))
    }

    fun info(message: String, tags: Map<String, Any?>? = null) {
        Log.i(TAG, render(message, tags))
    }

    fun warn(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) {
        Log.w(TAG, render(message, tags), throwable)
        record(throwable, message, tags, fatal = false)
    }

    fun error(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) {
        Log.e(TAG, render(message, tags), throwable)
        record(throwable, message, tags, fatal = false)
    }

    fun fatal(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) {
        Log.e(TAG, render(message, tags), throwable)
        record(throwable, message, tags, fatal = true)
    }

    fun setUserIdentifier(identifier: String?) {
        runCatching { crashlytics?.setUserId(identifier ?: "") }
    }

    private fun record(
        throwable: Throwable?,
        message: String,
        tags: Map<String, Any?>?,
        fatal: Boolean
    ) {
        val client = crashlytics ?: return
        runCatching {
            tags?.forEach { (key, value) -> client.setCustomKey(key, value?.toString() ?: "null") }
            client.log(message)
            if (throwable != null) {
                if (fatal) client.recordException(throwable) else client.log(throwable.stackTraceToString())
            }
        }
    }

    private fun render(message: String, tags: Map<String, Any?>?): String {
        if (tags.isNullOrEmpty()) return message
        return message + " · " + tags.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
}
