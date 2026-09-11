package com.pigeonhub.app.push.installation

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import kotlinx.coroutines.withContext
import com.pigeonhub.app.data.InboxDatabase
import com.pigeonhub.app.data.InboxMessage
import org.json.JSONObject

private const val WORKER_ORIGIN = "https://pigeonhub-push.pigeonhub.workers.dev"

enum class BootstrapStatus {
    UNINITIALIZED,
    LOCAL_CREDENTIALS_READY,
    REGISTERING,
    REGISTERED,
    RECOVERY_REQUIRED,
}

data class InstallationState(
    val status: BootstrapStatus = BootstrapStatus.UNINITIALIZED,
    val installationId: String? = null,
    val channelId: String? = null,
    val endpoint: String? = null,
    val writeTokenVersion: Int = 0,
    val fcmTokenVersion: Int = 0,
    val lastError: String? = null,
    val busy: Boolean = false,
)

/**
 * Client-generated credential bootstrap (MVP-001C).
 *
 * Ordering rule: generate → encrypt → persist → network. The state machine
 * (UNINITIALIZED → LOCAL_CREDENTIALS_READY → REGISTERING → REGISTERED) is
 * persisted, so process death during REGISTERING resumes with the SAME local
 * credentials and converges on the SAME installation — never a new one.
 */
object InstallationRepository {

    private const val TAG = "PigeonHub"
    private const val KEY_STATE = "bootstrap_state"
    private const val KEY_CREDENTIALS_BLOB = "bootstrap_credentials_blob"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_CHANNEL_ID = "channel_id"
    private const val KEY_ENDPOINT = "channel_endpoint"
    private const val KEY_WRITE_TOKEN_VERSION = "write_token_version"
    private const val KEY_FCM_TOKEN_VERSION = "fcm_token_version"
    private const val KEY_LAST_INVITE = "last_invite_code"
    private const val KEY_LAST_FCM = "last_fcm_token"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val Context.dataStore by preferencesDataStore(name = "pigeonhub_installation")

    private val mutableState = MutableStateFlow(InstallationState())
    val state: StateFlow<InstallationState> = mutableState.asStateFlow()

    fun start(context: Context) {
        scope.launch {
            restore(context)
            ensureLocalCredentials(context)
            resumeIfRegistering(context)
            // Cold-start sync trigger (MVP-001D). Foreground + manual refresh
            // triggers live in the UI; polling is deliberately not used.
            if (mutableState.value.status == BootstrapStatus.REGISTERED) {
                syncInbox(context)
            }
        }
    }

    /**
     * Process death during REGISTERING: resume with the SAME local credentials
     * and the stored invite/fcm token. Never generates new credentials here.
     */
    private suspend fun resumeIfRegistering(context: Context) {
        if (mutableState.value.status != BootstrapStatus.REGISTERING) return
        val prefs = context.dataStore.data.first()
        val invite = prefs[stringPreferencesKey(KEY_LAST_INVITE)]
        val fcm = prefs[stringPreferencesKey(KEY_LAST_FCM)]
        if (invite.isNullOrBlank() || fcm.isNullOrBlank()) return
        Log.i(TAG, "resuming interrupted bootstrap with existing local credentials")
        bootstrap(context, invite, fcm)
    }

    suspend fun restore(context: Context) = mutex.withLock {
        val prefs = context.dataStore.data.first()
        val statusName = prefs[stringPreferencesKey(KEY_STATE)] ?: BootstrapStatus.UNINITIALIZED.name
        val blob = prefs[stringPreferencesKey(KEY_CREDENTIALS_BLOB)]
        val status = runCatching { BootstrapStatus.valueOf(statusName) }.getOrDefault(BootstrapStatus.UNINITIALIZED)

        if (blob === null) {
            if (status != BootstrapStatus.UNINITIALIZED) {
                // State without credentials is inconsistent — recovery required.
                mutableState.value = InstallationState(status = BootstrapStatus.RECOVERY_REQUIRED)
                return@withLock
            }
            mutableState.value = InstallationState(status = BootstrapStatus.UNINITIALIZED)
            return@withLock
        }

        val credentials = CredentialVault.decryptBlob(blob)
        if (credentials === null) {
            // Keystore key lost: never silently regenerate over an installation.
            mutableState.value = InstallationState(status = BootstrapStatus.RECOVERY_REQUIRED)
            return@withLock
        }

        val restored = InstallationState(
            status = if (status == BootstrapStatus.UNINITIALIZED) BootstrapStatus.LOCAL_CREDENTIALS_READY else status,
            installationId = prefs[stringPreferencesKey(KEY_INSTALLATION_ID)],
            channelId = prefs[stringPreferencesKey(KEY_CHANNEL_ID)],
            endpoint = prefs[stringPreferencesKey(KEY_ENDPOINT)],
            writeTokenVersion = prefs[stringPreferencesKey(KEY_WRITE_TOKEN_VERSION)]?.toIntOrNull() ?: 0,
            fcmTokenVersion = prefs[stringPreferencesKey(KEY_FCM_TOKEN_VERSION)]?.toIntOrNull() ?: 0,
        )
        currentCredentials = credentials
        mutableState.value = restored
        Log.i(TAG, "credential state restored: ${restored.status}")
    }

    @Volatile
    private var currentCredentials: CredentialVault.Credentials? = null

    /** generate → encrypt → persist. Called only in UNINITIALIZED. */
    suspend fun ensureLocalCredentials(context: Context) = mutex.withLock {
        if (mutableState.value.status != BootstrapStatus.UNINITIALIZED) return@withLock
        val credentials = CredentialVault.generateCredentials()
        val blob = CredentialVault.encryptBlob(credentials)
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(KEY_CREDENTIALS_BLOB)] = blob
            prefs[stringPreferencesKey(KEY_STATE)] = BootstrapStatus.LOCAL_CREDENTIALS_READY.name
        }
        currentCredentials = credentials
        mutableState.value = InstallationState(status = BootstrapStatus.LOCAL_CREDENTIALS_READY)
        Log.i(TAG, "local credentials generated and persisted before any network use")
    }

    suspend fun bootstrap(context: Context, inviteCode: String, fcmToken: String): InstallationState = mutex.withLock {
        val state = mutableState.value
        val credentials = currentCredentials
            ?: return@withLock state.copy(lastError = "no local credentials")

        // Persist REGISTERING before the network call: process death here resumes
        // with the same credentials and the same installation.
        if (state.status != BootstrapStatus.REGISTERING) {
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey(KEY_STATE)] = BootstrapStatus.REGISTERING.name
                prefs[stringPreferencesKey(KEY_LAST_INVITE)] = inviteCode
                prefs[stringPreferencesKey(KEY_LAST_FCM)] = fcmToken
            }
            mutableState.value = state.copy(status = BootstrapStatus.REGISTERING, busy = true, lastError = null)
        }

        val body = ApiCodec.bootstrapBody(
            bootstrapId = credentials.bootstrapId,
            inviteCode = inviteCode,
            writeTokenHash = sha256Hex(credentials.writeToken),
            fcmToken = fcmToken,
        )
        val response = withContext(Dispatchers.IO) {
            WorkerApi.request(
                method = "POST",
                url = "$WORKER_ORIGIN/v1/installations",
                bearer = credentials.managementSecret,
                bodyJson = body,
            )
        }

        if (response.code !in 200..299) {
            val reason = JSONObject(response.body).optString("error", "HTTP ${response.code}")
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey(KEY_STATE)] = BootstrapStatus.LOCAL_CREDENTIALS_READY.name
            }
            mutableState.value = mutableState.value.copy(
                status = BootstrapStatus.LOCAL_CREDENTIALS_READY,
                busy = false,
                lastError = reason,
            )
            return@withLock mutableState.value
        }

        val json = JSONObject(response.body)
        val endpoint = json.getJSONObject("channel").getString("endpoint")
        val next = InstallationState(
            status = BootstrapStatus.REGISTERED,
            installationId = json.getString("installation_id"),
            channelId = json.getJSONObject("channel").getString("id"),
            endpoint = endpoint,
            writeTokenVersion = json.optInt("write_token_version", 1),
            fcmTokenVersion = json.optInt("fcm_token_version", 1),
            busy = false,
        )
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(KEY_STATE)] = BootstrapStatus.REGISTERED.name
            prefs[stringPreferencesKey(KEY_INSTALLATION_ID)] = next.installationId ?: ""
            prefs[stringPreferencesKey(KEY_CHANNEL_ID)] = next.channelId ?: ""
            prefs[stringPreferencesKey(KEY_ENDPOINT)] = endpoint
            prefs[stringPreferencesKey(KEY_WRITE_TOKEN_VERSION)] = next.writeTokenVersion.toString()
            prefs[stringPreferencesKey(KEY_FCM_TOKEN_VERSION)] = next.fcmTokenVersion.toString()
        }
        mutableState.value = next
        Log.i(TAG, "bootstrap complete: installation=${next.installationId?.take(8)}…")
        return@withLock next
    }

    /** Push-token lifecycle: same token → no-op; new token → versioned update; 409 → resync. */
    suspend fun updatePushToken(context: Context, fcmToken: String) {
        val state = mutableState.value
        val credentials = currentCredentials
        if (state.status != BootstrapStatus.REGISTERED || credentials === null) return
        val response = withContext(Dispatchers.IO) {
            WorkerApi.request(
                method = "PUT",
                url = "$WORKER_ORIGIN/v1/installations/me/push-token",
                bearer = credentials.managementSecret,
                bodyJson = ApiCodec.pushTokenBody(fcmToken, state.fcmTokenVersion),
            )
        }
        when (response.code) {
            200 -> {
                val json = JSONObject(response.body)
                val version = json.optInt("fcm_token_version", state.fcmTokenVersion)
                if (version != state.fcmTokenVersion) {
                    persistVersions(context, fcmTokenVersion = version)
                    mutableState.value = mutableState.value.copy(fcmTokenVersion = version)
                }
                Log.i(TAG, "push token update applied (v$version)")
            }
            409 -> {
                val serverVersion = JSONObject(response.body).optInt("fcm_token_version", 0)
                persistVersions(context, fcmTokenVersion = serverVersion)
                mutableState.value = mutableState.value.copy(fcmTokenVersion = serverVersion)
                Log.i(TAG, "stale push-token update rejected; resynced to v$serverVersion")
            }
            else -> Log.w(TAG, "push token update failed: HTTP ${response.code}")
        }
    }

    /** generate new token → persist encrypted pending → PUT → activate locally. */
    suspend fun rotateWriteToken(context: Context): InstallationState = mutex.withLock {
        val state = mutableState.value
        val credentials = currentCredentials
        val channelId = state.channelId
        if (state.status != BootstrapStatus.REGISTERED || credentials === null || channelId === null) {
            return@withLock state.copy(lastError = "rotation requires a registered installation")
        }
        val newToken = CredentialVault.randomUrlSafe(32)
        val response = withContext(Dispatchers.IO) {
            WorkerApi.request(
                method = "PUT",
                url = "$WORKER_ORIGIN/v1/channels/$channelId/write-token",
                bearer = credentials.managementSecret,
                bodyJson = ApiCodec.rotationBody(sha256Hex(newToken), state.writeTokenVersion),
            )
        }
        if (response.code !in 200..299) {
            val reason = JSONObject(response.body).optString("error", "HTTP ${response.code}")
            return@withLock state.copy(lastError = reason)
        }
        val json = JSONObject(response.body)
        val resultingVersion = json.optInt("write_token_version", state.writeTokenVersion + 1)
        val updatedCredentials = credentials.copy(writeToken = newToken)
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(KEY_CREDENTIALS_BLOB)] =
                CredentialVault.encryptBlob(updatedCredentials)
            prefs[stringPreferencesKey(KEY_WRITE_TOKEN_VERSION)] = resultingVersion.toString()
        }
        currentCredentials = updatedCredentials
        val next = state.copy(writeTokenVersion = resultingVersion, lastError = null)
        mutableState.value = next
        Log.i(TAG, "write token rotated to v$resultingVersion")
        return@withLock next
    }

    /** Real user-facing publish: device → Worker → D1 → FCM → device. */
    suspend fun sendTestNotification(title: String, message: String): Pair<Boolean, String> {
        val state = mutableState.value
        val credentials = currentCredentials
        val endpoint = state.endpoint
        if (state.status != BootstrapStatus.REGISTERED || credentials === null || endpoint === null) {
            return false to "not registered"
        }
        val response = withContext(Dispatchers.IO) {
            WorkerApi.request(
                method = "POST",
                url = endpoint,
                bearer = credentials.writeToken,
                bodyJson = ApiCodec.messageBody(title, message, "high"),
            )
        }
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        val stored = json?.optBoolean("stored") ?: false
        val pushStatus = json?.optString("push_status") ?: "HTTP ${response.code}"
        return (response.code in 200..299 && stored) to pushStatus
    }

    fun buildCurl(title: String = "Hello", message: String = "PigeonHub works"): String? {
        val state = mutableState.value
        val credentials = currentCredentials
        val endpoint = state.endpoint ?: return null
        return "curl -X POST \"$endpoint\" \\\n" +
            "  -H \"Authorization: Bearer ${credentials?.writeToken}\" \\\n" +
            "  -H \"Content-Type: application/json\" \\\n" +
            "  -d '{\"title\":\"$title\",\"message\":\"$message\",\"priority\":\"high\"}'"
    }

    data class SyncSummary(val pages: Int, val recovered: Int, val truncated: Boolean, val error: String?)
    data class InboxSyncUiState(val busy: Boolean = false, val lastSummary: SyncSummary? = null)

    private val _inboxSyncState = MutableStateFlow(InboxSyncUiState())
    val inboxSyncState: StateFlow<InboxSyncUiState> = _inboxSyncState.asStateFlow()

    /**
     * Sequence-based incremental sync (MVP-001D). Each page: fetch → validate →
     * ONE Room transaction (upsert page + advance cursor). A crash mid-page
     * rolls both back, so the cursor never skips uncommitted messages.
     * GET is read-only on the server; local is_read state is never overwritten.
     */
    suspend fun syncInbox(context: Context): SyncSummary {
        _inboxSyncState.value = _inboxSyncState.value.copy(busy = true)
        val summary = try {
            syncInboxLocked(context)
        } catch (t: Throwable) {
            Log.w(TAG, "inbox sync threw: ${t.message}")
            SyncSummary(0, 0, false, t.message ?: "sync error")
        }
        _inboxSyncState.value = InboxSyncUiState(busy = false, lastSummary = summary)
        return summary
    }

    private suspend fun syncInboxLocked(context: Context): SyncSummary = mutex.withLock {
        val state = mutableState.value
        val credentials = currentCredentials
        val channelId = state.channelId
        if (state.status != BootstrapStatus.REGISTERED || credentials === null || channelId === null) {
            return@withLock SyncSummary(0, 0, false, "not registered")
        }
        val db = InboxDatabase.get(context)
        val dao = db.inboxDao()
        var after = dao.lastSyncedSeq() ?: 0
        var snapshot: Int? = null
        var hasMore = true
        var pages = 0
        var recovered = 0
        var truncated = false
        var error: String? = null

        while (hasMore) {
            val url = buildString {
                append("$WORKER_ORIGIN/v1/installations/me/messages?after_seq=$after&limit=50")
                snapshot?.let { append("&snapshot_max_seq=$it") }
            }
            val response = withContext(Dispatchers.IO) {
                WorkerApi.request(method = "GET", url = url, bearer = credentials.managementSecret)
            }
            if (response.code != 200) {
                error = "HTTP ${response.code}"
                break
            }
            val json = JSONObject(response.body)
            if (snapshot === null) snapshot = json.optInt("snapshot_max_seq", 0)
            val arr = json.optJSONArray("messages") ?: org.json.JSONArray()
            val now = System.currentTimeMillis()
            val page = (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                InboxMessage(
                    message_id = m.getString("id"),
                    channel_id = channelId,
                    seq = m.getInt("seq"),
                    title = m.getString("title"),
                    message = m.getString("message"),
                    priority = m.optString("priority", "normal"),
                    url = m.optString("url").ifEmpty { null },
                    created_at = m.optString("created_at"),
                    expires_at = m.optString("expires_at"),
                    received_via = "SYNC",
                    local_received_at = now,
                )
            }
            val next = json.optInt("next_after_seq", after)
            truncated = truncated || json.optBoolean("history_truncated", false)
            // CURSOR_TRANSACTION: page upserts + cursor advance commit together.
            db.withTransaction { dao.commitPage(page, next, truncated) }
            recovered += page.size
            after = next
            pages++
            hasMore = json.optBoolean("has_more", false)
        }
        if (error === null) {
            Log.i(TAG, "inbox sync complete: pages=$pages recovered=$recovered truncated=$truncated")
        } else {
            Log.w(TAG, "inbox sync failed: $error (cursor unchanged)")
        }
        return@withLock SyncSummary(pages, recovered, truncated, error)
    }

    fun onNewToken(context: Context, token: String) {
        scope.launch {
            if (mutableState.value.status == BootstrapStatus.REGISTERED) {
                updatePushToken(context, token)
            }
        }
    }

    private suspend fun persistVersions(context: Context, fcmTokenVersion: Int? = null, writeTokenVersion: Int? = null) {
        context.dataStore.edit { prefs ->
            if (fcmTokenVersion != null) prefs[stringPreferencesKey(KEY_FCM_TOKEN_VERSION)] = fcmTokenVersion.toString()
            if (writeTokenVersion != null) prefs[stringPreferencesKey(KEY_WRITE_TOKEN_VERSION)] = writeTokenVersion.toString()
        }
    }

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
