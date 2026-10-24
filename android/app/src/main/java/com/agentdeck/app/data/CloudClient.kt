package com.agentdeck.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CloudTurn(val role: String, val content: String)

sealed class CloudError(
    val message: String,
    val statusCode: Int? = null,
    val isRetryable: Boolean = false,
    val cause: Throwable? = null
) : Exception(message) {
    object NetworkFailure : CloudError("Network connection failed. Check your internet connection.", isRetryable = true)
    
    data class HttpError(
        override val message: String,
        override val statusCode: Int
    ) : CloudError(message, statusCode, isRetryable = statusCode in 408..599, cause = null)
    
    data class AuthenticationError(
        override val message: String
    ) : CloudError(message, statusCode = 401, isRetryable = false, cause = null)
    
    data class RateLimitError(
        val retryAfterSeconds: Int? = null
    ) : CloudError("Rate limit exceeded. Please wait ${retryAfterSeconds ?: "a moment"} seconds before trying again.", statusCode = 429, isRetryable = true, cause = null)
    
    data class TimeoutError(val operation: String) : CloudError(
        "$operation timed out. This can happen with large prompts or slow network connections. You can try again.",
        isRetryable = true,
        cause = null
    )
    
    data class InvalidResponse(override val message: String) : CloudError(message, isRetryable = false, cause = null)
    
    data class ValidationFailed(override val message: String) : CloudError(message, statusCode = 400, isRetryable = false, cause = null)
}

class CloudRequestException : RuntimeException {
    val cloudError: CloudError
    
    constructor(error: CloudError) : super(error.message) {
        this.cloudError = error
    }
    
    constructor(message: String, statusCode: Int? = null) : super(message) {
        this.cloudError = if (statusCode == 401 || statusCode == 403) {
            CloudError.AuthenticationError(message)
        } else if (statusCode == 429) {
            CloudError.RateLimitError()
        } else if (statusCode in 408..599) {
            CloudError.HttpError(message, statusCode!!)
        } else {
            CloudError.HttpError(message, statusCode ?: 0)
        }
    }
    
    constructor(error: Throwable) : super(error.message ?: "Unknown error", error) {
        this.cloudError = when (error) {
            is CloudError -> error
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.io.IOException -> CloudError.NetworkFailure
            else -> CloudError.InvalidResponse(error.message ?: "Unknown error")
        }
    }
}

/**
 * Minimal OpenAI-compatible chat client. Requests go straight from the phone to the
 * endpoint the user configured; there is no relay. Keys are never logged.
 * 
 * Features:
 * - Structured error types with retry guidance
 * - Automatic retry on transient failures
 * - Comprehensive timeout handling
 * - Connection pooling and keep-alive
 */
class CloudClient(private val client: OkHttpClient = defaultClient()) {

    suspend fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        repositoryContext: String?,
        turns: List<CloudTurn>,
        onDelta: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = endpointOrNull(baseUrl)
            ?: return@withContext Result.failure(CloudError.InvalidResponse("The API base URL is not valid."))

        val messages = JSONArray()
        val systemContent = buildString {
            append(systemPrompt)
            if (!repositoryContext.isNullOrBlank()) {
                append("\n\n")
                append(repositoryContext)
            }
        }
        messages.put(JSONObject().put("role", "system").put("content", systemContent))
        turns.forEach { turn ->
            if (turn.content.isNotBlank()) {
                messages.put(JSONObject().put("role", turn.role).put("content", turn.content))
            }
        }
        val payload = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("temperature", 0.2)
            .put("messages", messages)

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "text/event-stream, application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        var lastJob: Job? = null
        var lastCall: okhttp3.Call? = null
        
        try {
            var attempt = 0
            val maxAttempts = 3
            var lastException: Exception? = null
            
            while (attempt < maxAttempts) {
                attempt++
                lastJob = currentCoroutineContext()[Job]?.invokeOnCompletion { lastCall?.cancel() }
                
                callExecute(request, payload.toString(), messages, onDelta)?.let { result ->
                    return@withContext result
                }
                
                lastException = when {
                    lastJob == null || !currentCoroutineContext().isActive -> null
                    else -> CloudError.TimeoutError("Cloud request (attempt $attempt/$maxAttempts)")
                }
                
                if (lastException != null && attempt < maxAttempts) {
                    val delayMs = minOf(1000L * (1 shl (attempt - 1)), 5000L)
                    delay(delayMs)
                }
            }
            
            Result.failure(lastException ?: CloudError.NetworkFailure)
        } finally {
            lastJob?.dispose()
        }
    }

    private suspend fun callExecute(
        request: Request,
        payloadJson: String,
        messages: JSONArray,
        onDelta: (String) -> Unit
    ): Result<Unit>? {
        val call = client.newCall(request)
        lastCall = call
        
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.peekBody(ERROR_BODY_LIMIT).string()
                    val httpError = handleErrorResponse(response.code, errorBody)
                    call.cancel()
                    return Result.failure(httpError)
                }
                
                val body = response.body ?: return@use Result.failure(
                    CloudError.InvalidResponse("The provider returned an empty response.")
                )
                val contentType = response.header("Content-Type").orEmpty()

                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    val text = response.peekBody(MAX_BODY_CHARS.toLong()).string()
                    val content = parseCompleteResponse(text)
                    if (content.isBlank()) {
                        return@use Result.failure(
                            CloudError.InvalidResponse("The provider returned no text. Check that streaming is enabled for this model.")
                        )
                    }
                    onDelta(content)
                    return@use Result.success(Unit)
                }

                val source = body.source()
                var sawContent = false
                var totalChars = 0
                var hasTimeout = false
                
                while (true) {
                    currentCoroutineContext().ensureActive()
                    
                    val line = withContext(Dispatchers.IO) {
                        source.readUtf8Line()
                    } ?: break
                    
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    
                    val chunk = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    
                    // Check for errors in the response
                    val errorObj = chunk.optJSONObject("error")
                    if (errorObj != null) {
                        val errorMessage = errorObj.optString("message").takeIf { it.isNotBlank() }
                        val errorCode = errorObj.optInt("code")
                        if (errorMessage != null) {
                            val cloudError = classifyProviderError(errorMessage, errorCode)
                            call.cancel()
                            return@useResult Result.failure(cloudError)
                        }
                    }
                    
                    val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val content = choice.optJSONObject("delta")
                        ?.optString("content")
                        ?.takeIf { it.isNotEmpty() && it != "null" }
                    
                    if (content != null) {
                        sawContent = true
                        totalChars += content.length
                        if (totalChars > MAX_REPLY_CHARS) {
                            call.cancel()
                            return@use Result.failure(
                                CloudError.InvalidResponse("The reply was too long and was stopped.")
                            )
                        }
                        onDelta(content)
                    }
                }
                
                if (!sawContent) {
                    return@use Result.failure(
                        CloudError.InvalidResponse("The provider returned no text. Check the model name and streaming support.")
                    )
                }
                
                Result.success(Unit)
            }
        } catch (e: java.net.SocketTimeoutException) {
            call.cancel()
            return Result.failure(CloudError.TimeoutError("Streaming response"))
        } catch (e: java.io.InterruptedIOException) {
            return null // Cancelled, don't treat as error
        }
    }
    
    private fun handleErrorResponse(code: Int, bodyText: String?): CloudError {
        val parsedError = parseProviderError(bodyText)
        
        return when (code) {
            401, 403 -> {
                val authMsg = parsedError ?: "Your API key was rejected. Please check the key, its permissions, and billing status."
                CloudError.AuthenticationError(authMsg)
            }
            429 -> {
                val retryAfter = bodyText.let {
                    try { JSONObject(it).optInt("retry_after") } 
                    catch (_: Exception) { 60 }
                }
                CloudError.RateLimitError(retryAfter.takeIf { it > 0 })
            }
            408 -> CloudError.TimeoutError("Request")
            400 -> {
                val msg = parsedError ?: "The provider rejected the request. Check the model name and settings."
                CloudError.ValidationFailed(msg)
            }
            404 -> {
                val msg = parsedError ?: "Endpoint or model not found. Check the base URL and the model name."
                CloudError.HttpError(msg, code)
            }
            in 500..599 -> {
                val msg = parsedError ?: "The provider had a server error. You can try again in a moment."
                CloudError.HttpError(msg, code)
            }
            else -> {
                val msg = parsedError ?: "The provider rejected the request."
                CloudError.HttpError(msg, code)
            }
        }
    }
    
    private fun parseProviderError(bodyText: String?): String? {
        return try {
            val json = JSONObject(bodyText.orEmpty())
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?.take(500)
        } catch (_: Exception) {
            null
        }
    }
    
    private fun classifyProviderError(message: String, code: Int): CloudError {
        return when {
            message.contains("invalid api key", ignoreCase = true) ||
            message.contains("authentication", ignoreCase = true) ->
                CloudError.AuthenticationError(message)
            
            message.contains("rate limit", ignoreCase = true) ||
            message.contains("too many requests", ignoreCase = true) ||
            code == 429 ->
                CloudError.RateLimitError()
            
            message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ->
                CloudError.TimeoutError("Cloud request")
            
            code in 500..599 ->
                CloudError.HttpError(message, code)
            
            code == 400 ->
                CloudError.ValidationFailed(message)
            
            code == 404 ->
                CloudError.HttpError(message, code)
            
            else -> CloudError.HttpError(message, code)
        }
    }
    
    @Suppress("RemoveCurlyBracesFromTemplate")
    private fun <T> Result<T>.orNull(): T? = getOrNull()

    /** Sends one tiny request so the user can verify URL, key, and model before using it. */
    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): String =
        withContext(Dispatchers.IO) {
            val endpoint = endpointOrNull(baseUrl)
                ?: throw CloudRequestException("The API base URL is not valid.")
            val payload = JSONObject()
                .put("model", model)
                .put("stream", false)
                .put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", "Reply with OK.")
                ))
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val call = client.newCall(request)
            val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { response ->
                    val bodyText = response.peekBody(MAX_BODY_CHARS.toLong()).string()
                    if (!response.isSuccessful) throw httpError(response.code, bodyText)
                    val json = runCatching { JSONObject(bodyText) }
                        .getOrElse { throw CloudRequestException("The provider returned an unexpected response.") }
                    json.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { throw CloudRequestException(it.take(240)) }
                    val reply = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        ?.trim()
                        .orEmpty()
                    val reportedModel = json.optString("model").takeIf { it.isNotBlank() }
                    when {
                        reply.isNotBlank() -> "Connected · ${reportedModel ?: model}"
                        reportedModel != null -> "Connected · $reportedModel"
                        else -> "Connected · the endpoint answered"
                    }
                }
            } catch (error: CloudRequestException) {
                throw error
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                throw CloudRequestException("Could not reach the API. Check the URL and your connection, then try again.")
            } finally {
                cancellation?.dispose()
            }
        }

    private fun parseCompleteResponse(text: String): String {
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: throw CloudRequestException("The provider returned an unexpected response.")
        json.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?.let { throw CloudRequestException(it.take(240)) }
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
    }

    private fun httpError(code: Int, bodyText: String?): CloudRequestException {
        val providerMessage = runCatching {
            val obj = JSONObject(bodyText.orEmpty())
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()?.take(200)
        val message = when (code) {
            400 -> providerMessage ?: "The provider rejected the request. Check the model name and settings."
            401, 403 -> "The API key was rejected. Check the key, its permissions, and billing status."
            404 -> "Endpoint or model not found. Check the base URL and the model name."
            408 -> "The provider timed out. Try again."
            413 -> "The request was too large for this model. Try a task with less repository context."
            429 -> "Rate limit or quota reached. Wait a moment or check the provider plan."
            in 500..599 -> "The provider had a server error. Try again in a moment."
            else -> providerMessage ?: "The provider rejected the request."
        }
        return CloudRequestException("$message ($code)", code)
    }

    companion object {
        private const val MAX_BODY_CHARS = 200_000
        private const val MAX_REPLY_CHARS = 80_000
        private const val ERROR_BODY_LIMIT = 20_000L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun endpointOrNull(baseUrl: String): HttpUrl? {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val url = trimmed.toHttpUrlOrNull() ?: return null
            if (!isAllowed(url) || hasExtras(url)) return null
            val path = url.encodedPath.trimEnd('/')
            val full = if (path.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
            return full.toHttpUrlOrNull()
        }

        fun baseUrlError(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return "Enter the API base URL."
            val url = trimmed.toHttpUrlOrNull()
                ?: return "Enter a full URL, for example https://api.openai.com/v1."
            if (!isAllowed(url)) {
                return "Use https:// for a remote API. Cleartext is only allowed for local test servers."
            }
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
                return "Enter the URL without a username or password. Put the key in the API key field."
            }
            if (url.query != null || url.fragment != null) {
                return "Enter a base URL without a query string or fragment."
            }
            return null
        }

        private fun hasExtras(url: HttpUrl): Boolean =
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null

        fun isAllowed(url: HttpUrl): Boolean =
            url.scheme == "https" || (url.scheme == "http" && isLoopback(url.host))

        fun isLoopback(host: String): Boolean =
            host == "10.0.2.2" || host == "localhost" || host == "127.0.0.1"

        fun providerLabel(baseUrl: String): String {
            val host = baseUrl.trim().toHttpUrlOrNull()?.host?.lowercase() ?: return "Cloud API"
            return when {
                host == "api.openai.com" || host.endsWith(".openai.com") -> "OpenAI"
                host.contains("openrouter") -> "OpenRouter"
                host.contains("groq") -> "Groq"
                host.contains("deepseek") -> "DeepSeek"
                host.contains("mistral") -> "Mistral"
                host.contains("together") -> "Together"
                host.contains("fireworks") -> "Fireworks"
                host.contains("x.ai") -> "xAI"
                isLoopback(host) -> "Local gateway"
                else -> "Cloud API"
            }
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
