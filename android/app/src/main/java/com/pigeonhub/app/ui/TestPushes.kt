package com.pigeonhub.app.ui

import android.content.Context
import com.pigeonhub.app.push.PushPipeline
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Local test pushes. They run through the EXACT production pipeline
 * (validate -> dedupe -> render), so notification behaviour can be verified
 * end-to-end without Firebase. Real FCM delivery plugs into the same
 * PushPipeline via PigeonMessagingService.
 */
object TestPushes {

    fun send(
        context: Context,
        priority: String,
        url: String? = null,
        messageId: String = "test-${UUID.randomUUID()}",
        title: String = "Build Complete",
        message: String = "Deployment succeeded",
    ): String {
        val result = PushPipeline.handle(
            context = context,
            data = mapOf(
                PushPayloadCompat.KEY_MESSAGE_ID to messageId,
                PushPayloadCompat.KEY_TITLE to title,
                PushPayloadCompat.KEY_MESSAGE to message,
                PushPayloadCompat.KEY_PRIORITY to priority,
                PushPayloadCompat.KEY_URL to url,
                PushPayloadCompat.KEY_SENT_AT to OffsetDateTime.now().toString(),
                PushPayloadCompat.KEY_SCHEMA_VERSION to "1",
            ),
            source = "test-ui",
        )
        return describe(result)
    }

    /** Sends the same message_id twice and reports both outcomes. */
    fun sendDuplicate(context: Context, priority: String): String {
        val id = "test-dup-${UUID.randomUUID()}"
        val first = send(context, priority, messageId = id)
        val second = send(context, priority, messageId = id)
        return "first: $first / second: $second"
    }

    fun describe(result: PushPipeline.HandleResult): String = when (result) {
        is PushPipeline.HandleResult.Delivered -> "delivered (warnings: ${result.warnings.size})"
        PushPipeline.HandleResult.Duplicate -> "duplicate suppressed"
        is PushPipeline.HandleResult.Invalid -> "invalid: ${result.reason}"
        is PushPipeline.HandleResult.NotRendered -> "kept in inbox (${result.reason})"
    }

    /** Avoids clashing with the data class name inside this file. */
    private object PushPayloadCompat {
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_PRIORITY = "priority"
        const val KEY_URL = "url"
        const val KEY_SENT_AT = "sent_at"
        const val KEY_SCHEMA_VERSION = "schema_version"
    }
}
