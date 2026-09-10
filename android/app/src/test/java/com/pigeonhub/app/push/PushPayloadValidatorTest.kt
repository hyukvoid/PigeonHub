package com.pigeonhub.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPayloadValidatorTest {

    private fun validData(): MutableMap<String, String?> = mutableMapOf(
        "message_id" to "msg-123",
        "title" to "Build Complete",
        "message" to "Deployment succeeded",
        "priority" to "normal",
        "url" to null,
        "sent_at" to "2026-09-11T01:00:00Z",
        "schema_version" to "1",
    )

    @Test
    fun `valid payload parses`() {
        val result = PushPayloadValidator.validate(validData())
        assertTrue(result is PushPayloadValidator.ParseResult.Valid)
        val payload = (result as PushPayloadValidator.ParseResult.Valid).payload
        assertEquals("msg-123", payload.messageId)
        assertEquals("Build Complete", payload.title)
        assertEquals(PushPayload.Priority.NORMAL, payload.priority)
        assertNull(payload.url)
        assertEquals(1, payload.schemaVersion)
    }

    @Test
    fun `high priority parses case-insensitively`() {
        val data = validData().apply { put("priority", "HIGH") }
        val payload = (PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid).payload
        assertEquals(PushPayload.Priority.HIGH, payload.priority)
    }

    @Test
    fun `missing message_id is rejected`() {
        val data = validData().apply { put("message_id", " ") }
        val result = PushPayloadValidator.validate(data)
        assertTrue(result is PushPayloadValidator.ParseResult.Invalid)
    }

    @Test
    fun `missing title is rejected`() {
        val data = validData().apply { remove("title") }
        assertTrue(PushPayloadValidator.validate(data) is PushPayloadValidator.ParseResult.Invalid)
    }

    @Test
    fun `blank message is rejected`() {
        val data = validData().apply { put("message", "") }
        assertTrue(PushPayloadValidator.validate(data) is PushPayloadValidator.ParseResult.Invalid)
    }

    @Test
    fun `unknown priority degrades to normal with warning`() {
        val data = validData().apply { put("priority", "urgent") }
        val result = PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid
        assertEquals(PushPayload.Priority.NORMAL, result.payload.priority)
        assertTrue(result.warnings.any { it.contains("priority") })
    }

    @Test
    fun `missing schema_version defaults to supported`() {
        val data = validData().apply { remove("schema_version") }
        val result = PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid
        assertEquals(1, result.payload.schemaVersion)
    }

    @Test
    fun `future schema_version is rejected`() {
        val data = validData().apply { put("schema_version", "2") }
        val result = PushPayloadValidator.validate(data)
        assertTrue(result is PushPayloadValidator.ParseResult.Invalid)
        assertTrue((result as PushPayloadValidator.ParseResult.Invalid).reason.contains("schema"))
    }

    @Test
    fun `non numeric schema_version is rejected`() {
        val data = validData().apply { put("schema_version", "one") }
        assertTrue(PushPayloadValidator.validate(data) is PushPayloadValidator.ParseResult.Invalid)
    }

    @Test
    fun `https url is accepted`() {
        val data = validData().apply { put("url", "https://example.com/deploy/42") }
        val result = PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid
        assertEquals("https://example.com/deploy/42", result.payload.url)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `http url is dropped with warning`() {
        val data = validData().apply { put("url", "http://example.com") }
        val result = PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid
        assertNull(result.payload.url)
        assertTrue(result.warnings.any { it.contains("https") })
    }

    @Test
    fun `javascript url is dropped`() {
        val data = validData().apply { put("url", "javascript:alert(1)") }
        val result = PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid
        assertNull(result.payload.url)
    }

    @Test
    fun `malformed url is dropped`() {
        val data = validData().apply { put("url", "not a url at all") }
        val result = PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid
        assertNull(result.payload.url)
        assertNotNull(result)
    }

    @Test
    fun `oversized title is truncated with warning`() {
        val data = validData().apply { put("title", "x".repeat(600)) }
        val result = PushPayloadValidator.validate(data) as PushPayloadValidator.ParseResult.Valid
        assertEquals(500, result.payload.title.length)
        assertTrue(result.warnings.any { it.contains("truncated") })
    }

    @Test
    fun `empty map is rejected`() {
        assertTrue(PushPayloadValidator.validate(emptyMap()) is PushPayloadValidator.ParseResult.Invalid)
    }

    @Test
    fun `in memory deduper marks and caps`() {
        val deduper = InMemoryMessageDeduper(maxSize = 3)
        assertFalse(deduper.isDuplicate("a"))
        deduper.record("a")
        assertTrue(deduper.isDuplicate("a"))
        deduper.record("b")
        deduper.record("c")
        deduper.record("d")
        // Oldest entry (a) was evicted when maxSize=3 was exceeded.
        assertFalse(deduper.isDuplicate("a"))
        assertTrue(deduper.isDuplicate("d"))
    }
}
