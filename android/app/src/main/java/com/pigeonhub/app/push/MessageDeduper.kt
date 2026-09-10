package com.pigeonhub.app.push

import android.content.Context

/**
 * Answers "was this message_id already handled?" so a repeated FCM delivery
 * does not stack identical notifications.
 *
 * Night-001 uses a session map plus a small SharedPreferences LRU. When Room
 * lands (MVP-001) only this interface needs a new implementation; the pipeline,
 * UI and FCM service stay untouched.
 */
interface MessageDeduper {
    /** True when [messageId] has already been handled. */
    fun isDuplicate(messageId: String): Boolean

    /** Marks [messageId] as handled. Call only after a message passed validation. */
    fun record(messageId: String)
}

/** Process-lifetime dedupe. Cheapest layer; survives nothing. */
class InMemoryMessageDeduper(private val maxSize: Int = DEFAULT_MAX_ENTRIES) : MessageDeduper {

    private val seen = LinkedHashMap<String, Long>(INITIAL_CAPACITY, 0.75f, true)

    override fun isDuplicate(messageId: String): Boolean = synchronized(this) {
        seen.containsKey(messageId)
    }

    override fun record(messageId: String): Unit = synchronized(this) {
        seen[messageId] = System.currentTimeMillis()
        while (seen.size > maxSize) {
            val oldest = seen.entries.minByOrNull { it.value }?.key ?: break
            seen.remove(oldest)
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 128
        const val DEFAULT_MAX_ENTRIES = 512
    }
}

/**
 * Small on-disk dedupe so re-delivered FCM messages are also caught after a
 * process restart. Deliberately NOT Room: this is a stop-gap with a bounded
 * entry count and it exists behind [MessageDeduper] so Room can replace it.
 */
class SharedPreferencesMessageDeduper(
    context: Context,
    private val maxSize: Int = DEFAULT_MAX_ENTRIES,
) : MessageDeduper {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isDuplicate(messageId: String): Boolean = prefs.contains(messageId)

    override fun record(messageId: String): Unit = synchronized(this) {
        prefs.edit()
            .putLong(messageId, System.currentTimeMillis())
            .apply()
        trim()
    }

    private fun trim() {
        val entries = prefs.all
        if (entries.size <= maxSize) return
        entries.entries
            .filter { it.value is Long }
            .sortedBy { it.value as Long }
            .take(entries.size - maxSize)
            .forEach { prefs.edit().remove(it.key).apply() }
    }

    private companion object {
        const val PREFS_NAME = "pigeonhub_dedupe"
        const val DEFAULT_MAX_ENTRIES = 512
    }
}
