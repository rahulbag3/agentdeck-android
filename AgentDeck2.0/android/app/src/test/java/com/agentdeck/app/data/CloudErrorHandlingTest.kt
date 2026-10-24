package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudErrorHandlingTest {

    @Test
    fun `network failure is retryable and offers retry`() {
        val result = CloudOperationHandler.classifyAndHandle(CloudError.NetworkFailure)
        assertEquals(FailureType.NETWORK, result.type)
        assertTrue(result.retryable)
        assertTrue(result.showRetry)
        assertFalse(result.requiresConfigFix)
        assertTrue(result.message.contains("internet connection", ignoreCase = true))
    }

    @Test
    fun `authentication failure requires config fix and hides retry`() {
        val result = CloudOperationHandler.classifyAndHandle(
            CloudError.AuthenticationError("Invalid API key")
        )
        assertEquals(FailureType.CONFIGURATION, result.type)
        assertFalse(result.retryable)
        assertFalse(result.showRetry)
        assertTrue(result.requiresConfigFix)
    }

    @Test
    fun `rate limit without retry-after offers manual retry`() {
        val result = CloudOperationHandler.classifyAndHandle(CloudError.RateLimitError())
        assertEquals(FailureType.RATE_LIMITED, result.type)
        assertTrue(result.retryable)
        assertTrue(result.showRetry)
    }

    @Test
    fun `rate limit with retry-after suppresses manual retry button`() {
        val result = CloudOperationHandler.classifyAndHandle(
            CloudError.RateLimitError(retryAfterSeconds = 30)
        )
        assertEquals(FailureType.RATE_LIMITED, result.type)
        assertTrue(result.retryable)
        assertFalse(result.showRetry)
        assertTrue(result.message.contains("30"))
    }

    @Test
    fun `timeout is retryable`() {
        val result = CloudOperationHandler.classifyAndHandle(
            CloudError.TimeoutError("The cloud request")
        )
        assertEquals(FailureType.TIMEOUT, result.type)
        assertTrue(result.retryable)
        assertTrue(result.showRetry)
    }

    @Test
    fun `validation failure is not retryable`() {
        val result = CloudOperationHandler.classifyAndHandle(
            CloudError.ValidationFailed("Bad model name")
        )
        assertEquals(FailureType.VALIDATION, result.type)
        assertFalse(result.retryable)
    }

    @Test
    fun `server error is retryable`() {
        val result = CloudOperationHandler.classifyAndHandle(
            CloudError.HttpError("Server exploded", 503)
        )
        assertEquals(FailureType.SERVER_ERROR, result.type)
        assertTrue(result.retryable)
        assertTrue(result.showRetry)
    }

    @Test
    fun `not found is not retryable`() {
        val result = CloudOperationHandler.classifyAndHandle(
            CloudError.HttpError("Model missing", 404)
        )
        assertEquals(FailureType.NOT_FOUND, result.type)
        assertFalse(result.retryable)
    }

    @Test
    fun `invalid response maps to unknown`() {
        val result = CloudOperationHandler.classifyAndHandle(
            CloudError.InvalidResponse("Unexpected payload")
        )
        assertEquals(FailureType.UNKNOWN, result.type)
        assertFalse(result.retryable)
    }

    @Test
    fun `retry backoff grows and caps`() {
        assertEquals(1_000L, CloudOperationHandler.buildRetryBackoff(1))
        assertEquals(2_000L, CloudOperationHandler.buildRetryBackoff(2))
        assertEquals(4_000L, CloudOperationHandler.buildRetryBackoff(3))
        assertEquals(8_000L, CloudOperationHandler.buildRetryBackoff(4))
        assertEquals(10_000L, CloudOperationHandler.buildRetryBackoff(9))
    }

    @Test
    fun `failure type descriptions are stable labels`() {
        assertEquals("Network Error", FailureType.NETWORK.description())
        assertEquals("Configuration Error", FailureType.CONFIGURATION.description())
        assertEquals("Rate Limit Exceeded", FailureType.RATE_LIMITED.description())
        assertEquals("Request Timed Out", FailureType.TIMEOUT.description())
        assertEquals("Invalid Request", FailureType.VALIDATION.description())
        assertEquals("Server Error", FailureType.SERVER_ERROR.description())
        assertEquals("Not Found", FailureType.NOT_FOUND.description())
        assertEquals("Unknown Error", FailureType.UNKNOWN.description())
    }
}
