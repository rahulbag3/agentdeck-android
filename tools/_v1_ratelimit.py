import re

p = 'data/CloudClient.kt'
s = open(p, encoding='utf-8').read()

# 1) RateLimitError carries the provider's own message
old = '''    class RateLimitError(val retryAfterSeconds: Int? = null) : CloudError(
        "Rate limit or quota reached. " + (retryAfterSeconds?.let { "Try again in $it seconds." } ?: "Wait a moment and try again."),
        statusCode = 429,
        isRetryable = true
    )'''
new = '''    class RateLimitError(
        val retryAfterSeconds: Int? = null,
        val detail: String? = null
    ) : CloudError(
        (detail?.take(160)?.let { "$it " } ?: "Rate limit or quota reached. ") +
            (retryAfterSeconds?.let { "Try again in $it seconds." } ?: "Wait a moment and try again."),
        statusCode = 429,
        isRetryable = true
    )'''
assert old in s
s = s.replace(old, new, 1)

# 2) 429 handler includes the provider message
old2 = '''            429 -> CloudError.RateLimitError(
                retryAfterSeconds = parseRetryAfter(bodyText)
            )'''
new2 = '''            429 -> CloudError.RateLimitError(
                retryAfterSeconds = parseRetryAfter(bodyText),
                detail = providerMessage
            )'''
assert old2 in s
s = s.replace(old2, new2, 1)

# 3) Retry loop: 429 waits long and retries at most once (no quota burn)
old3 = '''            val error = outcome.exceptionOrNull() as? CloudError
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
            delay(backoffMs(attempt))'''
new3 = '''            val error = outcome.exceptionOrNull() as? CloudError
            val rateLimited = error is CloudError.RateLimitError
            // Rate limits must not be hammered: one retry after a real wait,
            // otherwise the app burns the free-tier quota by itself.
            val maxForThisError = if (rateLimited) 2 else MAX_ATTEMPTS
            val retry = error != null &&
                error.isRetryable &&
                !delivered &&
                attempt < maxForThisError
            if (!retry) return@withContext outcome
            Logger.warn(
                "Retrying cloud request",
                null,
                mapOf(
                    "attempt" to attempt,
                    "error" to error.javaClass.simpleName,
                    "detail" to error.message?.take(120).orEmpty()
                )
            )
            delay(
                if (rateLimited) {
                    ((error as CloudError.RateLimitError).retryAfterSeconds ?: 30).coerceAtMost(90) * 1000L
                } else {
                    backoffMs(attempt)
                }
            )'''
assert old3 in s
s = s.replace(old3, new3, 1)
open(p, 'w', encoding='utf-8').write(s)
print('cloudclient ok')

# 4) State: model fallback skips 429 (provider-wide limit) and tracks the flag
p = 'data/AgentDeckState.kt'
s = open(p, encoding='utf-8').read()
old4 = '''                            failHard = candidateIndex >= modelCandidates.size - 1
                        )'''
new4 = '''                            failHard = candidateIndex >= modelCandidates.size - 1 || lastCloudRateLimited
                        )'''
assert old4 in s
s = s.replace(old4, new4, 1)

# declare flag + set it in streamAgentReply failure
old5 = '''            var candidateIndex = 0'''
new5 = '''            var candidateIndex = 0
            var lastCloudRateLimited = false'''
assert old5 in s
s = s.replace(old5, new5, 1)

old6 = '''            onFailure = { error ->
                if (added) {
                    updateMessage(taskId, replyId) { it.copy(text = "$text\\n\\n[stream interrupted]", at = now) }
                }
                if (failHard) {
                    agentFailure(taskId, error.message ?: "The request failed.")
                }
                null
            }'''
new6 = '''            onFailure = { error ->
                lastCloudRateLimited = error is CloudError.RateLimitError
                if (added) {
                    updateMessage(taskId, replyId) { it.copy(text = "$text\\n\\n[stream interrupted]", at = now) }
                }
                if (failHard) {
                    agentFailure(taskId, error.message ?: "The request failed.")
                }
                null
            }'''
assert old6 in s
s = s.replace(old6, new6, 1)

# fallback branch must not run on rate limit (it is provider-wide)
old7 = '''                    } ?: run {
                        candidateIndex++
                        if (usingCloud && candidateIndex < modelCandidates.size) {'''
new7 = '''                    } ?: run {
                        candidateIndex++
                        if (usingCloud && candidateIndex < modelCandidates.size && !lastCloudRateLimited) {'''
assert old7 in s
s = s.replace(old7, new7, 1)
open(p, 'w', encoding='utf-8').write(s)
print('state ok')
