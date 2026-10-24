package com.agentdeck.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class CloudTurn(val role: String, val content: String)

/**
 * Structured cloud errors. Each subtype knows whether a retry can plausibly
 * succeed, so the UI and the retry loop can make the same decision.
 */
sealed class CloudError(
    override val message: String,
    val statusCode: Int? = null,
    val isRetryable: Boolean = false
) : Exception(message) {
    data object NetworkFailure :
        CloudError("Could not reach the API. Check the URL and your connection, then try again.", isRetryable = true)

    class HttpError(message: String, statusCode: Int) :
        CloudError(message, statusCode, isRetryable = statusCode == 408 || statusCode in 500..599)

    class AuthenticationError(message: String) :
        CloudError(message, statusCode = 401)

    class RateLimitError(val retryAfterSeconds: Int? = null) : CloudError(
        "Rate limit or quota reached. " + (retryAfterSeconds?.let { "Try again in $it seconds." } ?: "Wait a moment and try again."),
        statusCode = 429,
        isRetryable = true
    )

    class TimeoutError(val operation: String) : CloudError(
        "$operation timed out. Large prompts and slow networks can cause this. Try again.",
        isRetryable = true
    )

    class InvalidResponse(message: String) : CloudError(message)

    class ValidationFailed(message: String) :
        CloudError(message, statusCode = 400)
}

/** Kept for call sites that prefer an exception; wraps a [CloudError]. */
class CloudRequestException(val cloudError: CloudError) : RuntimeException(cloudError.message)

/**
 * Minimal OpenAI-compatible chat client. Requests go straight from the phone to the
 * endpoint the user configured; there is no relay. Keys are never logged.
 *
 * Transient failures are retried only while no reply text has been delivered, so a
 * retry can never duplicate a partial stream.
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
            ?: return@withContext Result.failure(
                CloudError.InvalidResponse("The API base URL is not valid.")
            )

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
            .toString()

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "text/event-stream, application/json")
            .post(payload.toRequestBody(JSON))
            .build()

        var delivered = false
        var attempt = 0
        while (true) {
            attempt++
            val outcome = executeStream(request) { delta ->
                delivered = true
                onDelta(delta)
            }
            val error = outcome.exceptionOrNull() as? CloudError
            val retry = error != null &&
                error.isRetryable &&
                !delivered &&
                attempt < MAX_ATTEMPTS
            if (!retry) return@withContext outcome
            Logger.warn(
                "Retrying cloud request",
                null,
                mapOf("attempt" to attempt, "error" to error.javaClass.simpleName)
            )
            delay(backoffMs(attempt))
        }
        @Suppress("UNREACHABLE_CODE")
        Result.failure(CloudError.NetworkFailure)
    }

    /** Sends one tiny request so the user can verify URL, key, and model before using it. */
    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): Result<String> =
        withContext(Dispatchers.IO) {
            val endpoint = endpointOrNull(baseUrl)
                ?: return@withContext Result.failure(
                    CloudError.InvalidResponse("The API base URL is not valid.")
                )
            val payload = JSONObject()
                .put("model", model)
                .put("stream", false)
                .put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with OK."))
                )
                .toString()
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .post(payload.toRequestBody(JSON))
                .build()

            val call = client.newBuilder()
                .callTimeout(CONNECTION_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
                .newCall(request)
            val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { response ->
                    val bodyText = response.peekBody(MAX_BODY_CHARS.toLong()).string()
                    if (!response.isSuccessful) {
                        val error = handleErrorResponse(response.code, bodyText)
                        Logger.warn("Connection test failed", null, mapOf("status" to response.code))
                        return@withContext Result.failure(error)
                    }
                    val json = runCatching { JSONObject(bodyText) }.getOrNull()
                        ?: return@withContext Result.failure(
                            CloudError.InvalidResponse("The provider returned an unexpected response.")
                        )
                    json.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { message ->
                            return@withContext Result.failure(
                                classifyProviderError(message, json.optJSONObject("error")?.optInt("code") ?: 0)
                            )
                        }
                    val reply = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        ?.trim()
                        .orEmpty()
                    val reportedModel = json.optString("model").takeIf { it.isNotBlank() }
                    Result.success(
                        when {
                            reply.isNotBlank() -> "Connected · ${reportedModel ?: model}"
                            reportedModel != null -> "Connected · $reportedModel"
                            else -> "Connected · the endpoint answered"
                        }
                    )
                }
            } catch (error: SocketTimeoutException) {
                Result.failure(CloudError.TimeoutError("The connection test"))
            } catch (error: InterruptedIOException) {
                currentCoroutineContext().ensureActive()
                Result.failure(CloudError.NetworkFailure)
            } catch (error: java.io.IOException) {
                currentCoroutineContext().ensureActive()
                Result.failure(CloudError.NetworkFailure)
            } finally {
                cancellation?.dispose()
            }
        }

    private suspend fun executeStream(
        request: Request,
        onDelta: (String) -> Unit
    ): Result<Unit> {
        val call = client.newCall(request)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.peekBody(ERROR_BODY_LIMIT).string()
                    val error = handleErrorResponse(response.code, errorBody)
                    Logger.warn(
                        "Cloud request rejected",
                        null,
                        mapOf("status" to response.code, "kind" to error.javaClass.simpleName)
                    )
                    return Result.failure(error)
                }
                val body = response.body
                    ?: return Result.failure(
                        CloudError.InvalidResponse("The provider returned an empty response.")
                    )
                val contentType = response.header("Content-Type").orEmpty()

                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    val text = response.peekBody(MAX_BODY_CHARS.toLong()).string()
                    val content = parseCompleteResponse(text)
                    if (content.isBlank()) {
                        return Result.failure(
                            CloudError.InvalidResponse(
                                "The provider returned no text. Check that streaming is enabled for this model."
                            )
                        )
                    }
                    onDelta(content)
                    return Result.success(Unit)
                }

                val source = body.source()
                var sawContent = false
                var totalChars = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val chunk = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    chunk.optJSONObject("error")?.let { errorObj ->
                        val message = errorObj.optString("message").takeIf { it.isNotBlank() }
                        if (message != null) {
                            return Result.failure(classifyProviderError(message, errorObj.optInt("code")))
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
                            return Result.failure(
                                CloudError.InvalidResponse("The reply was too long and was stopped.")
                            )
                        }
                        onDelta(content)
                    }
                }
                if (!sawContent) {
                    return Result.failure(
                        CloudError.InvalidResponse(
                            "The provider returned no text. Check the model name and streaming support."
                        )
                    )
                }
                return Result.success(Unit)
            }
        } catch (error: SocketTimeoutException) {
            currentCoroutineContext().ensureActive()
            return Result.failure(CloudError.TimeoutError("The cloud request"))
        } catch (error: InterruptedIOException) {
            currentCoroutineContext().ensureActive()
            return Result.failure(CloudError.NetworkFailure)
        } catch (error: java.io.IOException) {
            currentCoroutineContext().ensureActive()
            return Result.failure(CloudError.NetworkFailure)
        } finally {
            cancellation?.dispose()
        }
    }

    private fun handleErrorResponse(code: Int, bodyText: String?): CloudError {
        val providerMessage = parseProviderError(bodyText)
        return when (code) {
            400 -> CloudError.ValidationFailed(
                providerMessage ?: "The provider rejected the request. Check the model name and settings."
            )
            401, 403 -> CloudError.AuthenticationError(
                providerMessage ?: "The API key was rejected. Check the key, its permissions, and billing status."
            )
            404 -> CloudError.HttpError(
                providerMessage ?: "Endpoint or model not found. Check the base URL and the model name.",
                code
            )
            408 -> CloudError.TimeoutError("The provider request")
            413 -> CloudError.ValidationFailed(
                "The request was too large for this model. Try a task with less repository context."
            )
            429 -> CloudError.RateLimitError(
                retryAfterSeconds = parseRetryAfter(bodyText)
            )
            in 500..599 -> CloudError.HttpError(
                providerMessage ?: "The provider had a server error. Try again in a moment.",
                code
            )
            else -> CloudError.HttpError(
                providerMessage ?: "The provider rejected the request.",
                code
            )
        }
    }

    private fun parseProviderError(bodyText: String?): String? = runCatching {
        val obj = JSONObject(bodyText.orEmpty())
        obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: obj.optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()?.take(240)

    private fun parseRetryAfter(bodyText: String?): Int? = runCatching {
        JSONObject(bodyText.orEmpty()).optInt("retry_after").takeIf { it > 0 }
    }.getOrNull()

    private fun classifyProviderError(message: String, code: Int): CloudError = when {
        code == 401 || code == 403 ||
            message.contains("invalid api key", ignoreCase = true) ||
            message.contains("authentication", ignoreCase = true) ->
            CloudError.AuthenticationError(message.take(240))
        code == 429 ||
            message.contains("rate limit", ignoreCase = true) ||
            message.contains("too many requests", ignoreCase = true) ->
            CloudError.RateLimitError()
        code == 400 -> CloudError.ValidationFailed(message.take(240))
        code == 404 -> CloudError.HttpError(message.take(240), code)
        code in 500..599 -> CloudError.HttpError(message.take(240), code)
        message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ->
            CloudError.TimeoutError("The cloud request")
        else -> CloudError.InvalidResponse(message.take(240))
    }

    private fun parseCompleteResponse(text: String): String {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return ""
        json.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?.let { throw CloudRequestException(classifyProviderError(it, 0)) }
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
    }

    private fun backoffMs(attempt: Int): Long =
        minOf(1000L shl (attempt - 1).coerceIn(0, 3), 8000L)

    companion object {
        private const val MAX_BODY_CHARS = 200_000
        private const val MAX_REPLY_CHARS = 80_000
        private const val ERROR_BODY_LIMIT = 20_000L
        private const val MAX_ATTEMPTS = 3
        private const val CONNECTION_TEST_TIMEOUT_SECONDS = 20L
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
