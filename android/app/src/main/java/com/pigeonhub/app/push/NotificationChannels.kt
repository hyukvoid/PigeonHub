package com.pigeonhub.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.pigeonhub.app.R

/**
 * Android notification channels.
 *
 * IMPORTANT SEPARATION OF CONCERNS:
 *  - `PushPayload.priority` (normal | high) is a *sender hint* about urgency.
 *  - Channel *importance* is an Android-side, user-visible behaviour setting
 *    (importance controls sound/heads-up presentation).
 * The sender priority only decides WHICH channel a notification lands in.
 * Once posted, Android applies the channel importance; the app never upgrades
 * a notification's importance at render time.
 */
object NotificationChannels {

    const val CHANNEL_NORMAL = "pigeonhub_normal"
    const val CHANNEL_HIGH = "pigeonhub_high"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val normal = NotificationChannel(
            CHANNEL_NORMAL,
            context.getString(R.string.channel_normal_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_normal_description)
        }

        // IMPORTANCE_HIGH is what makes heads-up presentation possible on 8.0+.
        val high = NotificationChannel(
            CHANNEL_HIGH,
            context.getString(R.string.channel_high_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_high_description)
        }

        manager.createNotificationChannel(normal)
        manager.createNotificationChannel(high)
    }

    fun channelFor(priority: PushPayload.Priority): String = when (priority) {
        PushPayload.Priority.HIGH -> CHANNEL_HIGH
        PushPayload.Priority.NORMAL -> CHANNEL_NORMAL
    }
}
