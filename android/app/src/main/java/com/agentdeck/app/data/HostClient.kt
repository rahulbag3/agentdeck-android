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
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** One changed file streamed by the host. */
data class HostFileChange(
    val path: String,
    val changeType: ChangeType,
    val language: String,
    val diff: String
)

/** One completed check streamed by the host. */
data class HostCheck(
    val kind: String,
    val name: String,
    val passed: Boolean,
    val summary: String,
    val command: String,
    val duration: String,
    val output: String
)

sealed interface HostEvent {
    data class Plan(val steps: List<PlanStep>) : HostEvent
    data class Status(val text: String) : HostEvent
    data class Message(val text: String) : HostEvent
    data class File(val file: HostFileChange) : HostEvent
    data class Check(val check: HostCheck) : HostEvent
    data class Permission(
        val requestId: String,
        val title: String,
        val body: String,
        val command: String
    ) : HostEvent
    data class Done(val status: String, val summary: String) : HostEvent
    data class Failure(val message: String) : HostEvent
}

data class HostInfo(
    val name: String,
    val platform: String,
    val projectIds: List<String>,
    val protocol: Int
)

class HostRequestException(message: String, val statusCode: Int? = null) : Exception(message)

/**
 * Talks to the AgentDeck host agent over HTTP + SSE. The token is sent as a
 * bearer header and is never logged.
 */
class HostClient(
    private val client: OkHttpClient = defaultClient()
) {

    suspend fun ping(baseUrl: String, token: String): Result<HostInfo> = withContext(Dispatchers.IO) {
        val url = endpointOrNull(baseUrl, "/v1/ping")
            ?: return@withContext Result.failure(HostRequestException("The host URL is not valid."))
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        HostRequestException(httpMessage(response.code), response.code)
                    )
                }
                val json = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@withContext Result.failure(
                        HostRequestException("The host returned an unexpected response.")
                    )
                val projects = json.optJSONArray("projects") ?: JSONArray()
                val ids = ArrayList<String>(projects.length())
                for (index in 0 until projects.length()) {
                    projects.optJSONObject(index)?.optString("id")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(ids::add)
                }
                Result.success(
                    HostInfo(
                        name = json.optString("name", "Host"),
                        platform = json.optString("platform", "Unknown"),
                        projectIds = ids,
                        protocol = json.optInt("protocol", 1)
                    )
                )
            }
        } catch (error: SocketTimeoutException) {
            Result.failure(HostRequestException("The host did not answer in time."))
        } catch (error: java.io.IOException) {
            currentCoroutineContext().ensureActive()
            Result.failure(
                HostRequestException("Could not reach the host. Check the URL, the token, and that the host is running.")
            )
        }
    }

    suspend fun run(
        baseUrl: String,
        token: String,
        taskId: String,
        projectId: String,
        prompt: String,
        branch: String,
        turns: List<CloudTurn>,
        onEvent: suspend (HostEvent) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = endpointOrNull(baseUrl, "/v1/run")
            ?: return@withContext Result.failure(HostRequestException("The host URL is not valid."))
        val body = JSONObject()
            .put("taskId", taskId)
            .put("projectId", projectId)
            .put("prompt", prompt)
            .put("branch", branch)
            .put("turns", JSONArray().also { array ->
                turns.forEach { turn ->
                    array.put(JSONObject().put("role", turn.role).put("content", turn.content))
                }
            })
            .toString()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON))
            .build()

        val call = client.newCall(request)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.peekBody(8_000).string()
                    return@use Result.failure(
                        HostRequestException(httpMessage(response.code, text), response.code)
                    )
                }
                val source = response.body?.source()
                    ?: return@use Result.failure(HostRequestException("The host returned an empty stream."))
                var eventName = "message"
                val data = StringBuilder()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trim())
                        }
                        line.isBlank() -> {
                            if (data.isNotEmpty()) {
                                val parsed = parseEvent(eventName, data.toString())
                                data.setLength(0)
                                eventName = "message"
                                if (parsed != null) {
                                    onEvent(parsed)
                                    if (parsed is HostEvent.Done) break
                                }
                            }
                        }
                    }
                }
                Result.success(Unit)
            }
        } catch (error: SocketTimeoutException) {
            currentCoroutineContext().ensureActive()
            Result.failure(HostRequestException("The host stopped responding."))
        } catch (error: java.io.IOException) {
            currentCoroutineContext().ensureActive()
            Result.failure(HostRequestException("The host connection dropped."))
        } finally {
            cancellation?.dispose()
        }
    }

    suspend fun decidePermission(
        baseUrl: String,
        token: String,
        taskId: String,
        requestId: String,
        allow: Boolean
    ): Result<Unit> = post(baseUrl, token, "/v1/permission", JSONObject()
        .put("taskId", taskId)
        .put("requestId", requestId)
        .put("decision", if (allow) "allow" else "deny"))

    suspend fun cancel(baseUrl: String, token: String, taskId: String): Result<Unit> =
        post(baseUrl, token, "/v1/cancel", JSONObject().put("taskId", taskId))

    private suspend fun post(
        baseUrl: String,
        token: String,
        path: String,
        payload: JSONObject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = endpointOrNull(baseUrl, path)
            ?: return@withContext Result.failure(HostRequestException("The host URL is not valid."))
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token.trim()}")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(HostRequestException(httpMessage(response.code), response.code))
                }
            }
        } catch (error: java.io.IOException) {
            currentCoroutineContext().ensureActive()
            Result.failure(HostRequestException("Could not reach the host."))
        }
    }

    private fun parseEvent(name: String, raw: String): HostEvent? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return when (name) {
            "plan" -> {
                val steps = json.optJSONArray("steps") ?: return null
                HostEvent.Plan(
                    (0 until steps.length()).mapNotNull { index ->
                        val step = steps.optJSONObject(index) ?: return@mapNotNull null
                        val text = step.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val state = runCatching {
                            PlanState.valueOf(step.optString("state", "TODO").uppercase())
                        }.getOrDefault(PlanState.TODO)
                        PlanStep(text, state)
                    }
                )
            }
            "status" -> HostEvent.Status(json.optString("text")).takeIf { it.text.isNotBlank() }
            "message" -> HostEvent.Message(json.optString("text")).takeIf { it.text.isNotBlank() }
            "file" -> {
                val path = json.optString("path").takeIf { it.isNotBlank() } ?: return null
                val changeType = runCatching {
                    ChangeType.valueOf(json.optString("changeType", "MODIFIED").uppercase())
                }.getOrDefault(ChangeType.MODIFIED)
                HostEvent.File(
                    HostFileChange(
                        path = path,
                        changeType = changeType,
                        language = json.optString("language", "Text"),
                        diff = json.optString("diff")
                    )
                )
            }
            "check" -> HostEvent.Check(
                HostCheck(
                    kind = json.optString("kind", "check"),
                    name = json.optString("name", "Check"),
                    passed = json.optString("status").equals("PASSED", ignoreCase = true),
                    summary = json.optString("summary"),
                    command = json.optString("command"),
                    duration = json.optString("duration"),
                    output = json.optString("output")
                )
            )
            "permission" -> HostEvent.Permission(
                requestId = json.optString("requestId"),
                title = json.optString("title", "Run the agent on this project?"),
                body = json.optString("body", ""),
                command = json.optString("command", "")
            )
            "done" -> HostEvent.Done(
                status = json.optString("status", "review"),
                summary = json.optString("summary", "")
            )
            "error" -> HostEvent.Failure(json.optString("message", "The host run failed."))
            else -> null
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Returns an error message for an unusable host URL, or null when it is allowed. */
        fun baseUrlError(raw: String, allowInsecureLan: Boolean = false): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return "Enter the host URL."
            val url = trimmed.toHttpUrlOrNull()
                ?: return "Enter a full URL, for example http://192.168.1.10:8787."
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
                return "Enter the URL without a username or password. Put the token in the token field."
            }
            if (url.query != null || url.fragment != null) {
                return "Enter a base URL without a query string or fragment."
            }
            if (url.scheme == "http" && !isLoopback(url.host) && !allowInsecureLan) {
                return "Use HTTPS, or enable unencrypted LAN for a trusted local network."
            }
            if (url.scheme != "https" && url.scheme != "http") {
                return "The host URL must use http:// or https://."
            }
            return null
        }

        fun isLoopback(host: String): Boolean =
            host == "10.0.2.2" || host == "localhost" || host == "127.0.0.1" || host == "::1"

        fun endpointOrNull(baseUrl: String, path: String): HttpUrl? {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val parsed = trimmed.toHttpUrlOrNull() ?: return null
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
            if (parsed.query != null || parsed.fragment != null) return null
            return (trimmed + path).toHttpUrlOrNull()
        }

        private fun httpMessage(code: Int, body: String? = null): String {
            val detail = runCatching {
                JSONObject(body.orEmpty()).optString("error").takeIf { it.isNotBlank() }
            }.getOrNull()
            return when (code) {
                401, 403 -> "The host rejected the token."
                404 -> detail ?: "The host does not expose that endpoint."
                409 -> detail ?: "That task is already running on the host."
                in 500..599 -> detail ?: "The host had an error."
                else -> detail ?: "The host rejected the request ($code)."
            }
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
