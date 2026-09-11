package com.pigeonhub.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Durable Android inbox (MVP-001D/002A).
 *
 * D1 is the canonical message store; this table is the local replica.
 * message_id is the canonical dedupe identity: FCM and SYNC may deliver the
 * same message independently, and (channel_id, seq) stays unique either way.
 */
@Entity(
    tableName = "inbox_messages",
    indices = [Index(value = ["channel_id", "seq"], unique = true)],
)
data class InboxMessage(
    @PrimaryKey val message_id: String,
    val channel_id: String,
    val seq: Int,
    val title: String,
    val message: String,
    val priority: String,
    val url: String?,
    val created_at: String,
    val expires_at: String,
    /** "FCM" (realtime wake-up) or "SYNC" (recovered from D1). */
    val received_via: String,
    val local_received_at: Long,
    /** Set when the app's own FCM callback received this message (MVP-002A). */
    val device_received_at: Long? = null,
    /** Local-only unread state; server sync never overwrites it. */
    val is_read: Boolean = false,
    val read_at: Long? = null,
)

@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val id: Int = 1,
    val last_synced_seq: Int = 0,
    val last_sync_at: Long = 0,
    val history_truncated: Boolean = false,
)

@Dao
abstract class InboxDao {

    /**
     * SYNC upsert: content refresh that PRESERVES local metadata and any FCM
     * delivery evidence (device_received_at) — sync never overwrites them.
     */
    @Query(
        "INSERT INTO inbox_messages " +
            "(message_id, channel_id, seq, title, message, priority, url, " +
            "created_at, expires_at, received_via, local_received_at, device_received_at, is_read, read_at) " +
            "VALUES (:messageId, :channelId, :seq, :title, :message, :priority, :url, " +
            ":createdAt, :expiresAt, :receivedVia, :localReceivedAt, NULL, 0, NULL) " +
            "ON CONFLICT(message_id) DO UPDATE SET " +
            "title = excluded.title, message = excluded.message, priority = excluded.priority, " +
            "url = excluded.url, expires_at = excluded.expires_at",
    )
    abstract fun insertFromSync(
        messageId: String,
        channelId: String,
        seq: Int,
        title: String,
        message: String,
        priority: String,
        url: String?,
        createdAt: String,
        expiresAt: String,
        receivedVia: String,
        localReceivedAt: Long,
    )

    /**
     * FCM upsert: realtime delivery. Marks device_received_at (first FCM
     * callback wins; a sync-first row is upgraded, never duplicated). seq is
     * nullable so legacy payloads without coordinates still land.
     */
    @Query(
        "INSERT INTO inbox_messages " +
            "(message_id, channel_id, seq, title, message, priority, url, " +
            "created_at, expires_at, received_via, local_received_at, device_received_at, is_read, read_at) " +
            "VALUES (:messageId, :channelId, :seq, :title, :message, :priority, :url, " +
            ":createdAt, :expiresAt, :receivedVia, :localReceivedAt, :deviceReceivedAt, 0, NULL) " +
            "ON CONFLICT(message_id) DO UPDATE SET " +
            "title = excluded.title, message = excluded.message, priority = excluded.priority, " +
            "url = excluded.url, expires_at = excluded.expires_at, " +
            "device_received_at = COALESCE(inbox_messages.device_received_at, excluded.device_received_at)",
    )
    abstract fun insertFromFcm(
        messageId: String,
        channelId: String,
        seq: Int?,
        title: String,
        message: String,
        priority: String,
        url: String?,
        createdAt: String,
        expiresAt: String,
        receivedVia: String,
        localReceivedAt: Long,
        deviceReceivedAt: Long,
    )

    @Query("SELECT * FROM inbox_messages WHERE message_id = :messageId")
    abstract fun byId(messageId: String): InboxMessage?

    @Query("SELECT * FROM inbox_messages ORDER BY seq DESC")
    abstract fun flowAll(): Flow<List<InboxMessage>>

    @Query("SELECT COUNT(*) FROM inbox_messages")
    abstract fun count(): Int

    @Query(
        "UPDATE inbox_messages SET is_read = 1, read_at = :at " +
            "WHERE message_id = :messageId AND is_read = 0",
    )
    abstract fun markRead(messageId: String, at: Long)

    @Query("SELECT COALESCE(last_synced_seq, 0) FROM sync_state WHERE id = 1")
    abstract fun lastSyncedSeq(): Int?

    @Upsert
    abstract fun setSyncState(state: SyncState)

    @Query("SELECT * FROM sync_state WHERE id = 1")
    abstract fun syncState(): SyncState?

    @Query("UPDATE sync_state SET history_truncated = :truncated WHERE id = 1")
    abstract fun setHistoryTruncated(truncated: Boolean)

    /**
     * One transaction = one page of upserts + cursor advance. A crash mid-page
     * rolls the whole page AND the cursor back together (CURSOR_TRANSACTION).
     */
    @Transaction
    open fun commitPage(messages: List<InboxMessage>, cursor: Int, truncated: Boolean) {
        val now = System.currentTimeMillis()
        messages.forEach { m ->
            insertFromSync(
                m.message_id, m.channel_id, m.seq, m.title, m.message, m.priority,
                m.url, m.created_at, m.expires_at, "SYNC", now,
            )
        }
        setSyncState(SyncState(id = 1, last_synced_seq = cursor, last_sync_at = now))
        setHistoryTruncated(truncated)
    }
}

@Database(
    entities = [InboxMessage::class, SyncState::class],
    version = 3,
    exportSchema = false,
)
abstract class InboxDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao

    companion object {
        @Volatile
        private var instance: InboxDatabase? = null

        /** MVP-001D (v1) → MVP-002A (v2): delivery evidence column. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inbox_messages ADD COLUMN device_received_at INTEGER")
            }
        }

        /**
         * v2 → v3: re-baselines the Room identity hash after a transient dev
         * build wrote the database at version 2 with a drifted schema shape
         * (same entities). No data changes; the user's inbox is preserved.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op: identity re-baseline only
            }
        }

        fun get(context: Context): InboxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InboxDatabase::class.java,
                    "pigeonhub_inbox.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
