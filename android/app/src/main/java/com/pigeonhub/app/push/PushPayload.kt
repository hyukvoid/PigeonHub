package com.pigeonhub.app.push

/**
 * Wire format of a PigeonHub push. The FCM message is data-only; every value
 * arrives as a String. Field names must stay in sync with the server sender
 * (server/src/fcm.ts) and docs/PAYLOAD.md.
 *
 * schema_version lets the client reject (instead of mis-render) payloads from
 * a newer sender. Today only version 1 exists.
 */
data class PushPayload(
    val messageId: String,
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
