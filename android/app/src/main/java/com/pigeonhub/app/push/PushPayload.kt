package com.pigeonhub.app.push

/**
 * Wire format of a PigeonHub push. The FCM message is data-only; every value
 * arrives as a String. Field names must stay in sync with the server sender
 * (worker/src/fcm.ts) and docs/PAYLOAD.md.
 *
 * MVP-001D: channel_id + seq are included by the Worker so a realtime FCM
 * message carries its full canonical coordinates (channel seq) and lands in
 * the durable Room inbox without a follow-up fetch.
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
) {
    enum class Priority { NORMAL, HIGH }

    companion object {
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_SEQ = "seq"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_PRIORITY = "priority"
        const val KEY_URL = "url"
        const val KEY_SENT_AT = "sent_at"
        const val KEY_SCHEMA_VERSION = "schema_version"

        const val SUPPORTED_SCHEMA_VERSION = 1

        const val PRIORITY_NORMAL = "normal"
        const val PRIORITY_HIGH = "high"
    }
}
