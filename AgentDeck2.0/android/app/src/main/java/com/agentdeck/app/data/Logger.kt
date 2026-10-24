package com.agentdeck.app.data

import android.util.Log
import com.google.firebase.crashlytics.Crashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * Centralized logging utility for AgentDeck.
 * - Logs to Android logcat (debug builds)
 * - Reports errors to Firebase Crashlytics (production)
 * - Tracks analytics events (optional in future)
 */
object Logger {
    
    private const val TAG = "AgentDeck"
    private var crashlytics: Crashlytics? = null
    
    init {
        try {
            crashlytics = Firebase.crashlytics
        } catch (e: Exception) {
            // Firebase not initialized (expected in debug/dev builds without google-services.json)
        }
    }
    
    enum class LogLevel { DEBUG, INFO, WARN, ERROR, FATAL }
    
    /**
     * Log a message to both Android logcat and optionally Crashlytics.
     */
    fun log(
        level: LogLevel,
        message: String,
        throwable: Throwable? = null,
        tags: Map<String, Any?>? = null
    ) {
        val prefix = when (level) {
            LogLevel.DEBUG -> "[D]"
            LogLevel.INFO -> "[I]"
            LogLevel.WARN -> "[W]"
            LogLevel.ERROR -> "[E]"
            LogLevel.FATAL -> "[F]"
        }
        
        val logTag = "$prefix $TAG"
        
        // Log to Android system log
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, "$message ${tagsToString(tags)}")
            LogLevel.INFO -> Log.i(TAG, "$message ${tagsToString(tags)}")
            LogLevel.WARN -> Log.w(TAG, "$message ${tagsToString(tags)}")
            LogLevel.ERROR -> Log.e(TAG, "$message ${tagsToString(tags)}", throwable)
            LogLevel.FATAL -> Log.e(TAG, "$message ${tagsToString(tags)}", throwable)
        }
        
        // Report to Crashlytics if available
        if (crashlytics != null && (throwable != null || level in listOf(LogLevel.ERROR, LogLevel.FATAL))) {
            try {
                // Add custom keys for context tracking
                tags?.forEach { (key, value) ->
                    crashlytics?.setCustomKey("log.$key", value.toString())
                }
                
                if (throwable != null) {
                    when (level) {
                        LogLevel.ERROR -> crashlytics?.log("$message\n${throwable.message}")
                        LogLevel.FATAL -> crashlytics?.recordException(throwable)
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Silent failure - we don't want logger itself to cause crashes
            }
        }
    }
    
    /**
     * Convenience methods for common scenarios.
     */
    
    fun debug(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) {
        log(LogLevel.DEBUG, message, throwable, tags)
    }
    
    fun info(message: String, tags: Map<String, Any?>? = null) {
        log(LogLevel.INFO, message, null, tags)
    }
    
    fun warn(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) {
        log(LogLevel.WARN, message, throwable, tags)
    }
    
    fun error(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) {
        log(LogLevel.ERROR, message, throwable, tags)
    }
    
    fun fatal(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) {
        log(LogLevel.FATAL, message, throwable, tags)
    }
    
    /**
     * Record an unhandled exception as a fatal error.
     */
    fun recordException(exception: Throwable, context: String = "") {
        val message = "${context} - ${exception.message}"
        fatal(message, exception)
    }
    
    /**
     * Set custom user identifier for tracking issues per user.
     */
    fun setUserIdentifier(identifier: String?) {
        if (identifier == null) return
        try {
            crashlytics?.setUserId(identifier)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Set custom keys for structured data tracking.
     */
    fun setCustomKeys(keys: Map<String, Any?>) {
        try {
            keys.forEach { (key, value) ->
                crashlytics?.setCustomKey(key, value.toString())
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Clear custom keys (before logging another request).
     */
    fun clearCustomKeys() {
        try {
            crashlytics?.clearCustomKeys()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun tagsToString(tags: Map<String, Any?>?): String {
        if (tags == null || tags.isEmpty()) return ""
        return tags.map { "${it.key}=${it.value}" }.joinToString(", ")
    }
}

/**
 * Extension function for logging with automatic tag generation.
 */
fun Any.logDebug(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) =
    Logger.debug(message, throwable, tags)

fun Any.logInfo(message: String, tags: Map<String, Any?>? = null) =
    Logger.info(message, tags)

fun Any.logWarn(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) =
    Logger.warn(message, throwable, tags)

fun Any.logError(message: String, throwable: Throwable? = null, tags: Map<String, Any?>? = null) =
    Logger.error(message, throwable, tags)
