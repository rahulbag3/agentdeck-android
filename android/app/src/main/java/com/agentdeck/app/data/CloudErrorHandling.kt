package com.agentdeck.app.data

import kotlinx.coroutines.delay

/**
 * Enhanced cloud operation handling with structured error types and user-friendly messages.
 * Provides retry guidance, offline queue support, and graceful degradation.
 */
object CloudOperationHandler {
    
    /**
     * Handles cloud operations with comprehensive error classification and user feedback.
     * Returns a result indicating success or the specific error type.
     */
    fun classifyAndHandle(error: CloudError): CloudOperationResult {
        return when (error) {
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
                type = FailureType.LIMIT_EXCEEDED,
                message = if (error.retryAfterSeconds != null) {
                    "Rate limit reached\nWait ${error.retryAfterSeconds} seconds before trying again."
                } else {
                    "Rate limit reached\nYou've made too many requests. Please wait a moment and try again."
                },
                retryable = true,
                showRetry = error.retryAfterSeconds == null, // Auto-wait if we know the time
                requiresConfigFix = false
            )
            
            is CloudError.TimeoutError -> CloudOperationResult.Failure(
                type = FailureType.TIMEOUT,
                message = "${error.operation} timed out\nThis can happen with large prompts or slow networks. You can try again.",
                retryable = true,
                showRetry = true,
                requiresConfigFix = false
            )
            
            is CloudError.HttpError -> CloudOperationResult.Failure(
                type = when (error.statusCode) {
                    400 -> FailureType.VALIDATION
                    401, 403 -> FailureType.CONFIGURATION
                    404 -> FailureType.NOT_FOUND
                    500..599 -> FailureType.SERVER_ERROR
                    else -> FailureType.UNKNOWN
                },
                message = error.message,
                retryable = error.isRetryable,
                showRetry = error.isRetryable,
                requiresConfigFix = error.statusCode in listOf(401, 403)
            )
            
            is CloudError.InvalidResponse -> CloudOperationResult.Failure(
                type = FailureType.UNKNOWN,
                message = "Unexpected response from provider\n${error.message}",
                retryable = false,
                showRetry = false,
                requiresConfigFix = false
            )
            
            is CloudError.ValidationFailed -> CloudOperationResult.Failure(
                type = FailureType.VALIDATION,
                message = error.message,
                retryable = false,
                showRetry = false,
                requiresConfigFix = false
            )
        }
    }
    
    /**
     * Builds retry guidance based on error type and attempt count.
     */
    fun buildRetryBackoff(attempt: Int): Long {
        return when {
            attempt == 1 -> 1000L           // 1 second
            attempt == 2 -> 2000L           // 2 seconds  
            attempt == 3 -> 4000L           // 4 seconds
            attempt >= 5 -> 10000L          // Cap at 10 seconds
            else -> 1000L * (1 shl (attempt - 1))
        }.coerceAtMost(10000L)
    }
    
    /**
     * Determines if an error should trigger auto-retry vs manual retry button.
     */
    fun shouldAutoRetry(error: CloudError, maxRetries: Int = 3): Boolean {
        return error.isRetryable && !shouldShowRetryButton(error, maxRetries)
    }
    
    /**
     * Determines if a retry button should be shown to the user.
     */
    fun shouldShowRetryButton(error: CloudError, currentAttempt: Int, maxRetries: Int = 3): Boolean {
        return error.isRetryable && currentAttempt < maxRetries
    }
}

/**
 * Result of a cloud operation with detailed error classification.
 */
sealed class CloudOperationResult {
    data object Success : CloudOperationResult()
    
    data class Failure(
        val type: FailureType,
        val message: String,
        val retryable: Boolean,
        val showRetry: Boolean,
        val requiresConfigFix: Boolean
    ) : CloudOperationResult()
    
    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure
    fun failureOrNull(): Failure? = this as? Failure
}

enum class FailureType {
    NETWORK,              // No internet, server unreachable
    CONFIGURATION,        // Invalid API key, wrong URL
    RATE_LIMITED,         // Too many requests
    TIMEOUT,              // Operation timed out
    VALIDATION,           // Bad request parameters
    SERVER_ERROR,         // 5xx server errors
    NOT_FOUND,            // Model not found
    UNAUTHORIZED,         // Auth required but missing
    UNKNOWN               // Unknown/unclassified error
}

fun FailureType.description(): String = when (this) {
    FailureType.NETWORK -> "Network Error"
    FailureType.CONFIGURATION -> "Configuration Error"
    FailureType.RATE_LIMITED -> "Rate Limit Exceeded"
    FailureType.TIMEOUT -> "Request Timed Out"
    FailureType.VALIDATION -> "Invalid Request"
    FailureType.SERVER_ERROR -> "Server Error"
    FailureType.NOT_FOUND -> "Not Found"
    FailureType.UNAUTHORIZED -> "Unauthorized"
    FailureType.UNKNOWN -> "Unknown Error"
}

/**
 * Network connectivity monitor for app-wide connectivity state.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager = 
        context.getSystemService(android.net.ConnectivityManager::class.java)
    
    var isConnected by mutableStateOf(false)
        private set
    
    var isOnline by mutableStateOf(true)
        private set
    
    init {
        checkConnectivity()
    }
    
    private fun checkConnectivity() {
        try {
            val network = connectivityManager.activeNetwork ?: run {
                isConnected = false
                isOnline = false
                return
            }
            val networkInfo = connectivityManager.getNetworkCapabilities(network) ?: run {
                isConnected = false
                isOnline = false
                return
            }
            
            isConnected = true
            isOnline = networkInfo.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                          networkInfo.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
        } catch (e: Exception) {
            isConnected = false
            isOnline = false
        }
    }
    
    suspend fun waitForConnection(timeoutMs: Long = 30000L): Boolean {
        val startTime = System.currentTimeMillis()
        while (!isOnline && (System.currentTimeMillis() - startTime) < timeoutMs) {
            delay(500L)
            checkConnectivity()
        }
        return isOnline
    }
}
