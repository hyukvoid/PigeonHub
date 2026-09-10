package com.pigeonhub.app.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory inbox of pushes received during this process lifetime.
 * No persistence by design (night-001 has no database); Room persistence is a
 * follow-up milestone. UI observes [entries].
 */
object InboxStore {

    data class Entry(
        val payload: PushPayload,
        val receivedAtMillis: Long,
        val source: String,
        val tappedAtMillis: Long? = null,
    )

    private val mutableEntries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = mutableEntries.asStateFlow()

    fun add(payload: PushPayload, source: String) = synchronized(this) {
        // Newest first; replace an existing entry with the same id (re-delivery).
        mutableEntries.value = listOfNotNull(
            Entry(payload, System.currentTimeMillis(), source)
        ) + mutableEntries.value.filterNot { it.payload.messageId == payload.messageId }
    }

    fun markTapped(messageId: String) = synchronized(this) {
        val now = System.currentTimeMillis()
        mutableEntries.value = mutableEntries.value.map { entry ->
            if (entry.payload.messageId == messageId) {
                entry.copy(tappedAtMillis = entry.tappedAtMillis ?: now)
            } else {
                entry
            }
        }
    }

    fun clear() = synchronized(this) {
        mutableEntries.value = emptyList()
    }
}
