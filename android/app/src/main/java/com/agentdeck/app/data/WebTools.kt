package com.agentdeck.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real web access for the agent harness. No simulation: fetches happen from
 * the phone over OkHttp, with size caps and honest failures.
 */
class WebTools(private val client: OkHttpClient = defaultClient()) {

    data class Page(val url: String, val title: String, val text: String)
    data class SearchHit(val title: String, val url: String, val snippet: String)

    /** Fetches a web page and returns readable text (tags stripped). */
    suspend fun fetchPage(rawUrl: String, maxChars: Int = MAX_PAGE_CHARS): Result<Page> =
        withContext(Dispatchers.IO) {
            val url = rawUrl.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext Result.failure(IOException("Only http(s) URLs are supported."))
            }
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) AgentDeck/1.0")
                    .header("Accept", "text/html,application/json,text/plain;q=0.9,*/*;q=0.5")
                    .build()
            )
            val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("HTTP ${response.code} for $url"))
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    val body = response.peekBody(MAX_BODY_BYTES).string()
                    val page = when {
                        contentType.contains("json") -> Page(url, url, JSONObject(body).toString(2).take(maxChars))
                        contentType.contains("html") -> Page(url, extractTitle(body), extractText(body).take(maxChars))
                        else -> Page(url, url, body.take(maxChars))
                    }
                    Result.success(page)
                }
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                Result.failure(error)
            } finally {
                cancellation?.dispose()
            }
        }

    /**
     * Web search via DuckDuckGo's HTML endpoint (no key required). This is a
     * scrape of a public page, not a contract — if the markup changes the
     * tool reports an error instead of pretending.
     */
    suspend fun search(query: String, maxResults: Int = 8): Result<List<SearchHit>> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext Result.failure(IOException("Empty query."))
            val call = client.newCall(
                Request.Builder()
                    .url("https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) AgentDeck/1.0")
                    .build()
            )
            val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Search returned HTTP ${response.code}."))
                    }
                    val html = response.peekBody(MAX_BODY_BYTES).string()
                    val hits = parseDuckDuckGo(html).take(maxResults)
                    if (hits.isEmpty()) {
                        Result.failure(IOException("No results parsed — the search page layout may have changed."))
                    } else {
                        Result.success(hits)
                    }
                }
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                Result.failure(error)
            } finally {
                cancellation?.dispose()
            }
        }

    internal fun parseDuckDuckGo(html: String): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        val linkRegex = Regex(
            """<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val snippetRegex = Regex(
            """class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val snippets = snippetRegex.findAll(html).map { stripTags(it.groupValues[1]) }.toList()
        linkRegex.findAll(html).forEachIndexed { index, match ->
            var url = match.groupValues[1]
            // DDG wraps results in a redirect: //duckduckgo.com/l/?uddg=<encoded>
            val uddg = Regex("uddg=([^&]+)").find(url)?.groupValues?.get(1)
            if (uddg != null) url = java.net.URLDecoder.decode(uddg, "UTF-8")
            if (url.startsWith("//")) url = "https:$url"
            val title = stripTags(match.groupValues[2])
            if (title.isNotBlank() && url.startsWith("http")) {
                hits += SearchHit(title, url, snippets.getOrNull(index).orEmpty().take(200))
            }
        }
        return hits
    }

    private fun extractTitle(html: String): String =
        Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.take(200).orEmpty()

    internal fun extractText(html: String): String {
        var text = html
            .replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?is)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>|</tr>"), "\n")
        text = text.replace(Regex("<[^>]+>"), " ")
        text = decodeEntities(text)
        return text.split('\n').joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }

    private fun stripTags(html: String): String =
        decodeEntities(html.replace(Regex("<[^>]+>"), " ")).replace(Regex("[ \t]+"), " ").trim()

    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&nbsp;", " ").replace("&#x2F;", "/").replace("&mdash;", "—")
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }

    /**
     * Raw HTTP API call (GET/POST) the model can use for user-chosen services.
     * Consequential (sends data out) — the executor gates it behind approval.
     */
    suspend fun api(
        method: String,
        rawUrl: String,
        body: String?,
        maxChars: Int = MAX_PAGE_CHARS
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext Result.failure(IOException("Only http(s) URLs are supported."))
        }
        val m = method.uppercase().let { if (it == "POST" || it == "GET" || it == "PUT" || it == "DELETE") it else "GET" }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) AgentDeck/1.0")
            .method(m, if (body != null) body.toRequestBody("application/json".toMediaType()) else null)
            .build()
        val call = client.newCall(request)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val text = response.peekBody(MAX_BODY_BYTES).string()
                Result.success(
                    "HTTP ${response.code}\n" + text.take(maxChars) +
                        if (text.length > maxChars) "\n… truncated" else ""
                )
            }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            Result.failure(error)
        } finally {
            cancellation?.dispose()
        }
    }

    companion object {
        const val MAX_PAGE_CHARS = 40_000
        const val MAX_BODY_BYTES = 3_000_000L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
