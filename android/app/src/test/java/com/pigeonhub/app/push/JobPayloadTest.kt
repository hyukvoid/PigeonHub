package com.pigeonhub.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MVP-005: optional structured Job layer on the wire format. */
class JobPayloadTest {

    private fun jobData(vararg extra: Pair<String, String?>): MutableMap<String, String?> {
        val base: MutableMap<String, String?> = mutableMapOf(
            "message_id" to "msg-job-1",
            "title" to "Crawler",
            "message" to "18,431 / 50,000",
            "priority" to "normal",
            "job_source" to "cli",
            "job_id" to "job-42",
            "job_state" to "PROGRESS",
            "job_progress_current" to "18431",
            "job_progress_total" to "50000",
        )
        extra.forEach { (k, v) -> base[k] = v }
        return base
    }

    @Test
    fun `unstructured payload has null job`() {
        val data = mutableMapOf<String, String?>(
            "message_id" to "m1", "title" to "t", "message" to "m",
        )
        val payload = (PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid).payload
        assertNull(payload.job)
    }

    @Test
    fun `job event parses with progress`() {
        val payload = (PushPayloadValidator.validate(jobData()) as PushPayloadValidator.ParseResult.Valid).payload
        val job = payload.job!!
        assertEquals("cli", job.source)
        assertEquals("job-42", job.jobId)
        assertEquals(JobPayload.State.PROGRESS, job.state)
        assertEquals(18431, job.progressCurrent)
        assertEquals(50000, job.progressTotal)
        assertEquals("cli:job-42", job.jobKey)
        assertTrue(job.isInFlight)
    }

    @Test
    fun `state parses case-insensitively`() {
        val data = jobData("job_state" to "needs_action")
        val job = (PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid).payload.job!!
        assertEquals(JobPayload.State.NEEDS_ACTION, job.state)
        assertFalse(job.isInFlight)
    }

    @Test
    fun `job without state is dropped but message survives`() {
        val data = jobData("job_state" to null)
        val payload = (PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid).payload
        assertNull(payload.job)
    }

    @Test
    fun `unknown state drops job part with warning`() {
        val data = jobData("job_state" to "MAYBE")
        val result = PushPayloadValidator.validate(data)
        assertTrue(result is PushPayloadValidator.ParseResult.Valid)
        assertNull((result as PushPayloadValidator.ParseResult.Valid).payload.job)
        assertTrue(result.warnings.any { it.contains("job") })
    }

    @Test
    fun `non-https job deep link is dropped`() {
        val data = jobData("job_deep_link" to "http://insecure.example/x")
        val job = (PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid).payload.job!!
        assertNull(job.deepLink)
    }
}
