package com.pigeonhub.app.push

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.pigeonhub.app.data.InboxDatabase

/**
 * Realtime push entry point (FCM service; adb debug receiver and debug-only
 * in-app test buttons feed the same path). MVP-002A: the durable Room inbox IS
 * the inbox state, and the FCM callback marks delivery evidence.
 *
 * Order per spec: payload validation → dedupe/upsert → Room → notification.
 * Notification policy: posted only when the message is NEW to Room — a delayed
 * or duplicated FCM after a sync never re-notifies and never creates a second
 * row (DEVICE_PUSH_RECEIVED evidence is upgraded on the existing row instead).
 *
 * MVP-005/010 Job attention policy: structured Job events are INBOX UPDATES,
 * not notifications, while the job is in flight (RUNNING/PROGRESS). Terminal
 * states notify once per (job, state) — DONE on the normal channel,
 * FAILED/NEEDS_ACTION on the high channel — so progress spam can never reach
 * the notification shade. Redelivered state events are deduped by a
 * persistent job-state key, not by message_id (connectors may retry with a
 * fresh message_id).
 */
object PushPipeline {

    private const val TAG = "PigeonHub"

    sealed interface HandleResult {
        data class Delivered(val notificationId: Int, val warnings: List<String>) : HandleResult
        data object Duplicate : HandleResult
        data class Invalid(val reason: String) : HandleResult
        data class NotRendered(val reason: String) : HandleResult
    }

    fun handle(context: Context, data: Map<String, String?>, source: String): HandleResult {
        val appContext = context.applicationContext
        NotificationChannels.ensureCreated(appContext)

        val parsed = PushPayloadValidator.validate(data)
        val payload = when (parsed) {
            is PushPayloadValidator.ParseResult.Invalid -> {
                Log.w(TAG, "[$source] rejected push: ${parsed.reason}")
                return HandleResult.Invalid(parsed.reason)
            }
            is PushPayloadValidator.ParseResult.Valid -> parsed.payload
        }

        val db = InboxDatabase.get(appContext)
        val dao = db.inboxDao()
        val existing = dao.byId(payload.messageId)
        val now = System.currentTimeMillis()

        // Realtime delivery: records device_received_at (first callback wins;
        // a sync-first row is upgraded with delivery evidence, never duplicated).
        db.runInTransaction {
            dao.insertFromFcm(
                messageId = payload.messageId,
                channelId = payload.channelId ?: "dev",
                seq = payload.seq,
                title = payload.title,
                message = payload.message,
                priority = payload.priority.name.lowercase(),
                url = payload.url,
                createdAt = payload.sentAt ?: "",
                expiresAt = "",
                receivedVia = "FCM",
                localReceivedAt = now,
                deviceReceivedAt = now,
                eventType = data["event_type"],
                provider = data["provider"],
                runId = data["run_id"],
                attentionReason = data["attention_reason"],
                factsJson = data["facts"],
                jobSource = payload.job?.source,
                jobId = payload.job?.jobId,
                jobName = payload.job?.jobName,
                jobState = payload.job?.state?.name,
                jobStartedAt = payload.job?.startedAt,
                jobFinishedAt = payload.job?.finishedAt,
                jobProgressCurrent = payload.job?.progressCurrent,
                jobProgressTotal = payload.job?.progressTotal,
                jobAttentionReason = payload.job?.attentionReason,
                jobResultSummary = payload.job?.resultSummary,
                jobDeepLink = payload.job?.deepLink,
            )
        }

        if (existing !== null) {
            Log.i(TAG, "[$source] duplicate delivery absorbed by Room: ${payload.messageId}")
            return HandleResult.Duplicate
        }

        parsed.warnings.forEach { Log.w(TAG, "[$source] payload warning: $it") }

        val job = payload.job
        if (job != null) {
            val decision = JobAttentionPolicy.shouldNotify(appContext, job)
            if (!decision.notify) {
                Log.i(
                    TAG,
                    "[$source] job ${job.jobKey} ${job.state} → inbox only (${decision.reason})",
                )
                return HandleResult.NotRendered(decision.reason)
            }
        }

        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Log.w(TAG, "[$source] notifications disabled; message kept in Room only")
            return HandleResult.NotRendered("notification permission not granted")
        }

        val notificationId = NotificationRenderer.render(appContext, payload)
        Log.i(TAG, "[$source] delivered push ${payload.messageId} (priority=${payload.priority.name})")
        return HandleResult.Delivered(notificationId, parsed.warnings)
    }
}
