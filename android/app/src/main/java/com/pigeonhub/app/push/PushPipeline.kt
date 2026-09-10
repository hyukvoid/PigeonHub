package com.pigeonhub.app.push

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Single entry point for an incoming push, whichever transport delivered it
 * (FCM service today; adb debug receiver and in-app test buttons for local
 * testing). Order is fixed by the night-001 spec:
 *
 *   payload validation -> message_id dedupe -> inbox record -> notification
 *
 * The function never throws and never performs network I/O, so it is safe to
 * call from FirebaseMessagingService.onMessageReceived.
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

        // Swap point for Room-based dedupe in MVP-001: everything downstream
        // only knows the MessageDeduper interface.
        val deduper: MessageDeduper = SharedPreferencesMessageDeduper(appContext)
        if (deduper.isDuplicate(payload.messageId)) {
            Log.i(TAG, "[$source] duplicate push suppressed: ${payload.messageId}")
            return HandleResult.Duplicate
        }

        deduper.record(payload.messageId)
        InboxStore.add(payload, source)

        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Log.w(TAG, "[$source] notifications disabled; kept in inbox only")
            return HandleResult.NotRendered("notification permission not granted")
        }

        parsed.warnings.forEach { Log.w(TAG, "[$source] payload warning: $it") }
        val notificationId = NotificationRenderer.render(appContext, payload)
        Log.i(TAG, "[$source] delivered push ${payload.messageId} (priority=${payload.priority.name})")
        return HandleResult.Delivered(notificationId, parsed.warnings)
    }
}
