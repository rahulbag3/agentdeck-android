package com.agentdeck.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay

/**
 * Maps [CloudError]s to user-facing failure info so the UI and the retry loop can
 * share one decision.
 */
object CloudOperationHandler {

    fun classifyAndHandle(error: CloudError): CloudOperationResult.Failure = when (error) {
        is CloudError.NetworkFailure -> CloudOperationResult.Failure(
            type = FailureType.NETWORK,
            message = "Network connection unavailable\nPlease check your internet connection and try again.",
            retryable = true,
            showRetry = true,
            requiresConfigFix = false
        )

        is CloudError.AuthenticationError -> CloudOperationResult.Failure(
            type = FailureType.CONFIGURATION,
            message = "Authentication failed\nYour API key was rejected by the provider. Please check your key in Settings.",
            retryable = false,
            showRetry = false,
            requiresConfigFix = true
        )

        is CloudError.RateLimitError -> CloudOperationResult.Failure(
            type = FailureType.RATE_LIMITED,
            message = error.message,
            retryable = true,
            showRetry = error.retryAfterSeconds == null,
            requiresConfigFix = false
        )

        is CloudError.TimeoutError -> CloudOperationResult.Failure(
            type = FailureType.TIMEOUT,
            message = error.message,
            retryable = true,
            showRetry = true,
            requiresConfigFix = false
        )

        is CloudError.ValidationFailed -> CloudOperationResult.Failure(
            type = FailureType.VALIDATION,
            message = error.message,
            retryable = false,
            showRetry = false,
            requiresConfigFix = false
        )

        is CloudError.HttpError -> CloudOperationResult.Failure(
            type = when (error.statusCode) {
                null -> FailureType.UNKNOWN
                404 -> FailureType.NOT_FOUND
                in 500..599 -> FailureType.SERVER_ERROR
                else -> FailureType.UNKNOWN
            },
            message = error.message,
            retryable = error.isRetryable,
            showRetry = error.isRetryable,
            requiresConfigFix = false
        )

        is CloudError.InvalidResponse -> CloudOperationResult.Failure(
            type = FailureType.UNKNOWN,
            message = "Unexpected response from provider\n${error.message}",
            retryable = false,
            showRetry = false,
            requiresConfigFix = false
        )
    }

    /** Exponential backoff with a hard ceiling, shared with any future retry queue. */
    fun buildRetryBackoff(attempt: Int): Long =
        (1000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(10_000L)
}

sealed class CloudOperationResult {
    data object Success : CloudOperationResult()

    data class Failure(
        val type: FailureType,
        val message: String,
        val retryable: Boolean,
        val showRetry: Boolean,
        val requiresConfigFix: Boolean
    ) : CloudOperationResult()
}

enum class FailureType {
    NETWORK,
    CONFIGURATION,
    RATE_LIMITED,
    TIMEOUT,
    VALIDATION,
    SERVER_ERROR,
    NOT_FOUND,
    UNKNOWN
}

fun FailureType.description(): String = when (this) {
    FailureType.NETWORK -> "Network Error"
    FailureType.CONFIGURATION -> "Configuration Error"
    FailureType.RATE_LIMITED -> "Rate Limit Exceeded"
    FailureType.TIMEOUT -> "Request Timed Out"
    FailureType.VALIDATION -> "Invalid Request"
    FailureType.SERVER_ERROR -> "Server Error"
    FailureType.NOT_FOUND -> "Not Found"
    FailureType.UNKNOWN -> "Unknown Error"
}

/** Connectivity watcher used for the offline banner and retry gating. */
class NetworkMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    var isOnline: Boolean = true
        private set

    fun start(onChange: (Boolean) -> Unit) {
        if (callback != null) return
        refresh()
        val manager = connectivityManager ?: return
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = publish(onChange)
            override fun onLost(network: android.net.Network) = publish(onChange)
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                capabilities: NetworkCapabilities
            ) = publish(onChange)

            private fun publish(listener: (Boolean) -> Unit) {
                val online = hasInternet()
                isOnline = online
                mainHandler.post { listener(online) }
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(networkCallback) }
            .onSuccess { callback = networkCallback }
    }

    fun stop() {
        val registered = callback ?: return
        runCatching { connectivityManager?.unregisterNetworkCallback(registered) }
        callback = null
    }

    fun refresh(): Boolean {
        isOnline = hasInternet()
        return isOnline
    }

    private fun hasInternet(): Boolean = runCatching {
        val manager = connectivityManager ?: return@runCatching true
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    suspend fun waitForConnection(timeoutMs: Long = 30_000L): Boolean {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < timeoutMs) {
            if (refresh()) return true
            delay(500)
        }
        return isOnline
    }
}
