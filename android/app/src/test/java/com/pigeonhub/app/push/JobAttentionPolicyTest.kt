package com.pigeonhub.app.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MVP-005/010: job attention policy — inbox-only while running, one alert per terminal state. */
class JobAttentionPolicyTest {

    private fun job(state: JobPayload.State, jobId: String = "job-1", source: String = "cli") =
        JobPayload(
            source = source, jobId = jobId, jobName = "Crawler", state = state,
            startedAt = null, finishedAt = null, progressCurrent = null, progressTotal = null,
            attentionReason = null, resultSummary = null, deepLink = null,
        )

    @Test
    fun `running and progress never notify`() {
        val store = mutableMapOf<String, Long>()
        assertFalse(JobAttentionPolicy.shouldNotify(job(JobPayload.State.RUNNING), store).notify)
        assertFalse(JobAttentionPolicy.shouldNotify(job(JobPayload.State.PROGRESS), store).notify)
        assertTrue(store.isEmpty())
    }

    @Test
    fun `done notifies once`() {
        val store = mutableMapOf<String, Long>()
        assertTrue(JobAttentionPolicy.shouldNotify(job(JobPayload.State.DONE), store).notify)
        assertFalse(JobAttentionPolicy.shouldNotify(job(JobPayload.State.DONE), store).notify)
    }

    @Test
    fun `failed and needs_action notify once each`() {
        val store = mutableMapOf<String, Long>()
        assertTrue(JobAttentionPolicy.shouldNotify(job(JobPayload.State.FAILED), store).notify)
        assertTrue(JobAttentionPolicy.shouldNotify(job(JobPayload.State.NEEDS_ACTION), store).notify)
        // FAILED again after a state TRANSITION (FAILED → NEEDS_ACTION → FAILED)
        // is a new event, not a redelivery: it re-alerts by design.
        assertTrue(JobAttentionPolicy.shouldNotify(job(JobPayload.State.FAILED), store).notify)
        // An exact redelivery of the same state does not.
        assertFalse(JobAttentionPolicy.shouldNotify(job(JobPayload.State.FAILED), store).notify)
    }

    @Test
    fun `re-run re-arms terminal alerts`() {
        val store = mutableMapOf<String, Long>()
        assertTrue(JobAttentionPolicy.shouldNotify(job(JobPayload.State.DONE), store).notify)
        assertFalse(JobAttentionPolicy.shouldNotify(job(JobPayload.State.RUNNING), store).notify)
        assertTrue(JobAttentionPolicy.shouldNotify(job(JobPayload.State.DONE), store).notify)
    }

    @Test
    fun `jobs are isolated by job key`() {
        val store = mutableMapOf<String, Long>()
        assertTrue(JobAttentionPolicy.shouldNotify(job(JobPayload.State.FAILED), store).notify)
        assertTrue(
            JobAttentionPolicy.shouldNotify(job(JobPayload.State.FAILED, jobId = "job-2"), store).notify,
        )
        assertFalse(JobAttentionPolicy.shouldNotify(job(JobPayload.State.FAILED), store).notify)
    }
}
