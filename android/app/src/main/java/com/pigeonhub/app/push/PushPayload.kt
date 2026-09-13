package com.pigeonhub.app.push

/**
 * Wire format of a PigeonHub push. The FCM message is data-only; every value
 * arrives as a String. Field names must stay in sync with the server sender
 * (worker/src/fcm.ts) and docs/PAYLOAD.md.
 *
 * MVP-001D: channel_id + seq are included by the Worker so a realtime FCM
 * message carries its full canonical coordinates (channel seq) and lands in
 * the durable Room inbox without a follow-up fetch.
 *
 * MVP-005: optional structured Job layer ([job]). Unstructured pushes leave
 * it null and behave exactly as before.
 */
data class PushPayload(
    val messageId: String,
    val channelId: String?,
    val seq: Int?,
    val title: String,
    val message: String,
    val priority: Priority,
    val url: String?,
    val sentAt: String?,
    val schemaVersion: Int,
    val job: JobPayload? = null,
) {
    enum class Priority { NORMAL, HIGH }

    companion object {
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_SEQ = "seq"
        const val KEY_EVENT_TYPE = "event_type"
        const val KEY_PROVIDER = "provider"
        const val KEY_RUN_ID = "run_id"
        const val KEY_ATTENTION_REASON = "attention_reason"
        const val KEY_FACTS = "facts"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_PRIORITY = "priority"
        const val KEY_URL = "url"
        const val KEY_SENT_AT = "sent_at"
        const val KEY_SCHEMA_VERSION = "schema_version"

        const val KEY_JOB_SOURCE = "job_source"
        const val KEY_JOB_ID = "job_id"
        const val KEY_JOB_NAME = "job_name"
        const val KEY_JOB_STATE = "job_state"
        const val KEY_JOB_STARTED_AT = "job_started_at"
        const val KEY_JOB_FINISHED_AT = "job_finished_at"
        const val KEY_JOB_PROGRESS_CURRENT = "job_progress_current"
        const val KEY_JOB_PROGRESS_TOTAL = "job_progress_total"
        const val KEY_JOB_ATTENTION_REASON = "job_attention_reason"
        const val KEY_JOB_RESULT_SUMMARY = "job_result_summary"
        const val KEY_JOB_DEEP_LINK = "job_deep_link"

        const val SUPPORTED_SCHEMA_VERSION = 1

        const val PRIORITY_NORMAL = "normal"
        const val PRIORITY_HIGH = "high"
    }
}

/** Normalized job event riding on a push (MVP-005). Null state parts are tolerated. */
data class JobPayload(
    val source: String,
    val jobId: String,
    val jobName: String?,
    val state: State,
    val startedAt: String?,
    val finishedAt: String?,
    val progressCurrent: Int?,
    val progressTotal: Int?,
    val attentionReason: String?,
    val resultSummary: String?,
    val deepLink: String?,
) {
    /** RUNNING / PROGRESS / DONE / FAILED / NEEDS_ACTION (PROGRESS is an event state). */
    enum class State { RUNNING, PROGRESS, DONE, FAILED, NEEDS_ACTION;

        companion object {
            fun from(raw: String?): State? = when (raw?.trim()?.uppercase()) {
                "RUNNING" -> RUNNING
                "PROGRESS" -> PROGRESS
                "DONE" -> DONE
                "FAILED" -> FAILED
                "NEEDS_ACTION" -> NEEDS_ACTION
                else -> null
            }
        }
    }

    /** Stable per-job identity within a channel: source + job_id. */
    val jobKey: String get() = "$source:$jobId"

    /** True when the job is still in flight (event state RUNNING or PROGRESS). */
    val isInFlight: Boolean get() = state == State.RUNNING || state == State.PROGRESS
}
