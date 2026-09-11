package com.pigeonhub.app.push

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.pigeonhub.app.data.InboxDatabase
import com.pigeonhub.app.data.InboxMessage

/**
 * Single entry point for a realtime push (FCM service, adb debug receiver,
 * in-app test buttons). MVP-001D: the durable Room inbox IS the inbox state.
 *
 * Order per spec: payload validation → dedupe/upsert → Room → notification.
 * The notification is posted only when the message is NEW to Room; a delayed
 * or duplicated FCM after a sync never re-notifies (Room count stays 1).
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

        // Durable upsert: message_id is the canonical dedupe identity. A
        // repeated or delayed delivery updates content but never creates a
        // second row (Room UNIQUE(message_id) + preserving upsert).
        val db = InboxDatabase.get(appContext)
        val dao = db.inboxDao()
        val existing = dao.byId(payload.messageId)
        val receivedVia = if (source == "fcm") "FCM" else "FCM"

        db.runInTransaction {
            dao.insertPreservingLocal(
                messageId = payload.messageId,
                channelId = payload.channelId ?: "dev",
                seq = payload.seq ?: 0,
                title = payload.title,
                message = payload.message,
                priority = payload.priority.name.lowercase(),
                url = payload.url,
                createdAt = payload.sentAt ?: "",
                expiresAt = "",
                receivedVia = receivedVia,
                localReceivedAt = System.currentTimeMillis(),
            )
        }

        if (existing !== null) {
            Log.i(TAG, "[$source] duplicate delivery absorbed by Room: ${payload.messageId}")
            return HandleResult.Duplicate
        }

        parsed.warnings.forEach { Log.w(TAG, "[$source] payload warning: $it") }

        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Log.w(TAG, "[$source] notifications disabled; message kept in Room only")
            return HandleResult.NotRendered("notification permission not granted")
        }

        val notificationId = NotificationRenderer.render(appContext, payload)
        Log.i(TAG, "[$source] delivered push ${payload.messageId} (priority=${payload.priority.name})")
        return HandleResult.Delivered(notificationId, parsed.warnings)
    }
}
