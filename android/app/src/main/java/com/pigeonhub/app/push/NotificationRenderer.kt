package com.pigeonhub.app.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pigeonhub.app.MainActivity
import com.pigeonhub.app.R

/**
 * Turns a validated [PushPayload] into a posted system notification.
 * Pure rendering: validation and dedupe happen earlier in [PushPipeline].
 */
object NotificationRenderer {

    const val EXTRA_MESSAGE_ID = "pigeonhub_message_id"
    const val EXTRA_URL = "pigeonhub_url"

    fun render(context: Context, payload: PushPayload): Int {
        NotificationChannels.ensureCreated(context)

        // Stable id per message_id: a re-post of the same message REPLACES the
        // previous notification instead of stacking a second copy.
        val notificationId = payload.messageId.hashCode()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_MESSAGE_ID, payload.messageId)
            payload.url?.let { putExtra(EXTRA_URL, it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, NotificationChannels.channelFor(payload.priority))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setWhen(System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        // NOTE: no setPriority() here. Priority is expressed via the channel
        // (see NotificationChannels) — that separation is deliberate.
        NotificationManagerCompat.from(context).notify(payload.messageId.hashCode(), builder.build())

        return notificationId
    }
}
