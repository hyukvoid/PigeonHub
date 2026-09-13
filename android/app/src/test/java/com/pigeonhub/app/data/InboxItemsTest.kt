package com.pigeonhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** MVP-006: same (job_source, job_id) collapses into one Job card. */
class InboxItemsTest {

    private fun msg(
        id: String,
        seq: Int,
        jobSource: String? = null,
        jobId: String? = null,
        jobState: String? = null,
        progressCurrent: Int? = null,
    ) = InboxMessage(
        message_id = id, channel_id = "ch", seq = seq,
        title = "T$id", message = "M$id", priority = "normal", url = null,
        created_at = "", expires_at = "", received_via = "FCM", local_received_at = 1000L + seq,
        job_source = jobSource, job_id = jobId, job_state = jobState, job_progress_current = progressCurrent,
    )

    @Test
    fun `unstructured messages stay individual`() {
        val items = collapseInboxItems(listOf(msg("a", 2), msg("b", 1)))
        assertEquals(2, items.size)
        assertEquals("a", (items[0] as InboxItem.Message).entry.message_id)
    }

    @Test
    fun `job events collapse to latest state`() {
        val items = collapseInboxItems(
            listOf(
                msg("done", 4, "cli", "j1", "DONE"),
                msg("p2", 3, "cli", "j1", "PROGRESS", progressCurrent = 71),
                msg("p1", 2, "cli", "j1", "PROGRESS", progressCurrent = 30),
                msg("run", 1, "cli", "j1", "RUNNING"),
            ),
        )
        assertEquals(1, items.size)
        val job = items[0] as InboxItem.Job
        assertEquals("DONE", job.entry.job_state)
        assertEquals(4, job.updateCount)
        assertEquals("cli:j1", job.jobKey)
    }

    @Test
    fun `distinct jobs stay distinct and sort by latest activity`() {
        val items = collapseInboxItems(
            listOf(
                msg("a1", 5, "comfyui", "video", "RUNNING"),
                msg("b1", 4, "cli", "crawl", "FAILED"),
                msg("plain", 3),
            ),
        )
        assertEquals(3, items.size)
        assertEquals("comfyui", (items[0] as InboxItem.Job).source)
        assertEquals("cli", (items[1] as InboxItem.Job).source)
        assertEquals(InboxItem.Message::class, items[2]::class)
    }
}
