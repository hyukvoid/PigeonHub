package com.pigeonhub.app.push

import java.net.URI

/**
 * Validates the raw FCM data map before anything is rendered.
 *
 * Policy:
 *  - Required fields (message_id, title, message) must be present and non-blank,
 *    otherwise the payload is rejected entirely.
 *  - priority is a routing hint, not a guarantee: an unknown value degrades to
 *    NORMAL with a warning rather than dropping the message.
 *  - Only https urls are accepted (night-001 rule). An http or malformed url is
 *    dropped with a warning; the notification itself still renders.
 *  - schema_version greater than the supported version rejects the payload so a
 *    newer sender never renders as garbage on an old client.
 */
object PushPayloadValidator {

    sealed interface ParseResult {
        data class Valid(val payload: PushPayload, val warnings: List<String>) : ParseResult
        data class Invalid(val reason: String) : ParseResult
    }

    fun validate(raw: Map<String, String?>): ParseResult {
        val warnings = mutableListOf<String>()

        val messageId = raw[PushPayload.KEY_MESSAGE_ID]?.trim().orEmpty()
        if (messageId.isEmpty()) return ParseResult.Invalid("message_id is required")
        if (messageId.length > MAX_ID_LENGTH) return ParseResult.Invalid("message_id too long")

        val rawTitle = raw[PushPayload.KEY_TITLE]?.trim().orEmpty()
        if (rawTitle.isEmpty()) return ParseResult.Invalid("title is required")
        val title = rawTitle.truncatedOrWarn(MAX_TITLE_LENGTH, "title", warnings)

        val rawMessage = raw[PushPayload.KEY_MESSAGE]?.trim().orEmpty()
        if (rawMessage.isEmpty()) return ParseResult.Invalid("message is required")
        val message = rawMessage.truncatedOrWarn(MAX_MESSAGE_LENGTH, "message", warnings)

        val priority = when (raw[PushPayload.KEY_PRIORITY]?.trim()?.lowercase()) {
            null, "", PushPayload.PRIORITY_NORMAL -> PushPayload.Priority.NORMAL
            PushPayload.PRIORITY_HIGH -> PushPayload.Priority.HIGH
            else -> {
                warnings += "unknown priority '${raw[PushPayload.KEY_PRIORITY]}', using normal"
                PushPayload.Priority.NORMAL
            }
        }

        val rawSchema = raw[PushPayload.KEY_SCHEMA_VERSION]?.trim().orEmpty()
        val schemaVersion = if (rawSchema.isEmpty()) {
            PushPayload.SUPPORTED_SCHEMA_VERSION // tolerate senders from before the field existed
        } else {
            val parsed = rawSchema.toIntOrNull()
                ?: return ParseResult.Invalid("schema_version is not a number: $rawSchema")
            if (parsed > PushPayload.SUPPORTED_SCHEMA_VERSION) {
                return ParseResult.Invalid(
                    "unsupported schema_version $parsed (client supports ${PushPayload.SUPPORTED_SCHEMA_VERSION})"
                )
            }
            if (parsed < 1) return ParseResult.Invalid("schema_version must be >= 1")
            parsed
        }

        val url = sanitizeUrl(raw[PushPayload.KEY_URL], warnings)

        val sentAt = raw[PushPayload.KEY_SENT_AT]?.trim()?.take(MAX_SENT_AT_LENGTH)

        // Canonical coordinates (MVP-001D). Optional: legacy senders omit them,
        // in which case the row is stored without a seq coordinate.
        val channelId = raw[PushPayload.KEY_CHANNEL_ID]?.trim()?.take(MAX_CHANNEL_ID_LENGTH)
            ?.ifEmpty { null }
        val seq = raw[PushPayload.KEY_SEQ]?.trim()?.take(MAX_SEQ_LENGTH)?.let {
            it.toIntOrNull() ?: run {
                warnings += "seq ignored: not a number"
                null
            }
        }

        return ParseResult.Valid(
            payload = PushPayload(
                messageId = messageId,
                channelId = channelId,
                seq = seq,
                title = title,
                message = message,
                priority = priority,
                url = url,
                sentAt = sentAt?.ifEmpty { null },
                schemaVersion = schemaVersion,
            ),
            warnings = warnings,
        )
    }

    /**
     * Returns an https-only url, or null when the input is missing/invalid.
     * Never returns an http url: we drop it instead of opening an insecure page.
     */
    private fun sanitizeUrl(raw: String?, warnings: MutableList<String>): String? {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        return try {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            when {
                scheme == "https" && !host.isNullOrBlank() -> candidate
                scheme == "http" -> {
                    warnings += "url dropped: only https is allowed"
                    null
                }
                else -> {
                    warnings += "url dropped: must be an absolute https url"
                    null
                }
            }
        } catch (_: Exception) {
            warnings += "url dropped: malformed"
            null
        }
    }

    private fun String.truncatedOrWarn(max: Int, field: String, warnings: MutableList<String>): String =
        if (length > max) {
            warnings += "$field truncated to $max characters"
            take(max)
        } else {
            this
        }

    private const val MAX_ID_LENGTH = 256
    private const val MAX_CHANNEL_ID_LENGTH = 64
    private const val MAX_SEQ_LENGTH = 12
    private const val MAX_TITLE_LENGTH = 500
    private const val MAX_MESSAGE_LENGTH = 4000
    private const val MAX_SENT_AT_LENGTH = 64
}
