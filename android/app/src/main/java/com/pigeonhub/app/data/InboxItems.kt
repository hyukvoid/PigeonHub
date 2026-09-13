package com.pigeonhub.app.data

import com.pigeonhub.app.push.JobPayload

/**
 * MVP-006: inbox rendering model. Structured Job events with the same
 * (job_source, job_id) collapse into ONE [InboxItem.Job] card — the latest
 * event's state wins and earlier progress events become history — while
 * unstructured messages stay individual [InboxItem.Message] rows exactly as
 * before. Pure function, unit-tested without Android.
 */
sealed interface InboxItem {

    val sortKey: Long

    data class Message(val entry: InboxMessage) : InboxItem {
        override val sortKey: Long = entry.local_received_at
    }

    data class Job(
        val events: List<InboxMessage>,
    ) : InboxItem {
        private val latest: InboxMessage get() = events.first()

        /** Source's latest event (events[0] is the newest — input is seq-desc). */
        val entry: InboxMessage get() = latest
        override val sortKey: Long get() = latest.local_received_at

        val jobKey: String = (latest.job_source ?: "") + ":" + (latest.job_id ?: "")

        val state: JobPayload.State? get() = JobPayload.State.from(latest.job_state)

        val source: String get() = latest.job_source ?: ""

        val displayName: String
            get() = latest.job_name ?: latest.title.ifBlank { latest.job_id ?: "" }

        val updateCount: Int get() = events.size

        val progressCurrent: Int? get() = latest.job_progress_current
        val progressTotal: Int? get() = latest.job_progress_total

        val attentionReason: String? get() = latest.job_attention_reason
        val resultSummary: String? get() = latest.job_result_summary
        val deepLink: String? get() = latest.job_deep_link ?: latest.url

        val startedAt: String? get() =
            latest.job_started_at ?: events.lastOrNull()?.job_started_at
    }
}

/**
 * Collapses a seq-desc message list into display items. Job events merge into
 * the card of their (source, job_id); everything else passes through.
 */
fun collapseInboxItems(entries: List<InboxMessage>): List<InboxItem> {
    val jobs = LinkedHashMap<String, MutableList<InboxMessage>>()
    val items = ArrayList<InboxItem>(entries.size)
    for (entry in entries) {
        val source = entry.job_source
        val jobId = entry.job_id
        if (source != null && jobId != null) {
            jobs.getOrPut("$source:$jobId") { ArrayList(4) }.add(entry)
        } else {
            items.add(InboxItem.Message(entry))
        }
    }
    for ((_, events) in jobs) {
        // Input is seq-desc, so events[0] is the newest event of the job.
        items.add(InboxItem.Job(events))
    }
    return items.sortedByDescending { it.sortKey }
}
