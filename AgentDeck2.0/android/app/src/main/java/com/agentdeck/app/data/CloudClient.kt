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

class CloudRequestException(message: String, val statusCode: Int? = null) : Exception(message)

/**
 * Minimal OpenAI-compatible chat client. Requests go straight from the phone to the
 * endpoint the user configured; there is no relay. Keys are never logged.
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
    ): Unit = withContext(Dispatchers.IO) {
        val endpoint = endpointOrNull(baseUrl)
            ?: throw CloudRequestException("The API base URL is not valid.")

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

        val call = client.newCall(request)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw httpError(response.code, response.peekBody(ERROR_BODY_LIMIT).string())
                }
                val body = response.body ?: throw CloudRequestException("The provider returned an empty response.")
                val contentType = response.header("Content-Type").orEmpty()

                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    val text = response.peekBody(MAX_BODY_CHARS.toLong()).string()
                    val content = parseCompleteResponse(text)
                    if (content.isBlank()) {
                        throw CloudRequestException("The provider returned no text. Check that streaming is enabled for this model.")
                    }
                    onDelta(content)
                    return@use
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
                    chunk.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { throw CloudRequestException(it.take(240)) }
                    val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val content = choice.optJSONObject("delta")
                        ?.optString("content")
                        ?.takeIf { it.isNotEmpty() && it != "null" }
                    if (content != null) {
                        sawContent = true
                        totalChars += content.length
                        if (totalChars > MAX_REPLY_CHARS) {
                            throw CloudRequestException("The reply was too long and was stopped.")
                        }
                        onDelta(content)
                    }
                }
                if (!sawContent) {
                    throw CloudRequestException("The provider returned no text. Check the model name and streaming support.")
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
