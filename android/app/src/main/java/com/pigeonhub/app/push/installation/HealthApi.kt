package com.pigeonhub.app.push.installation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * MVP-015: per-connector connection health, as observed by the WORKER (real
 * accepted publishes, webhooks, device syncs) — never guessed on-device.
 * States are deliberately coarse so rarely-eventing connectors don't read as
 * "broken": CONNECTED (seen ≤24h), DEGRADED (seen ≤72h), UNKNOWN (never seen),
 * DISCONNECTED (explicitly revoked).
 */
object HealthApi {

    enum class State { CONNECTED, DEGRADED, DISCONNECTED, UNKNOWN }

    data class ConnectorHealth(
        val source: String,
        val state: State,
        val lastSeenAt: String?,
        val lastEventAt: String?,
        val lastFailureAt: String?,
        val lastFailureReason: String?,
    )

    suspend fun fetch(): Map<String, ConnectorHealth> = withContext(Dispatchers.IO) {
        val credentials = InstallationRepository.currentCredentialsForApi() ?: return@withContext emptyMap()
        val response = WorkerApi.request(
            method = "GET",
            url = "${InstallationRepository.workerOrigin()}/v1/installations/me/health",
            bearer = credentials.managementSecret,
        )
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return@withContext emptyMap()
        if (response.code !in 200..299 || !json.optBoolean("ok")) return@withContext emptyMap()
        val arr = json.optJSONArray("health") ?: return@withContext emptyMap()
        buildMap {
            for (i in 0 until arr.length()) {
                val h = arr.getJSONObject(i)
                val state = when (h.optString("state")) {
                    "CONNECTED" -> State.CONNECTED
                    "DEGRADED" -> State.DEGRADED
                    "DISCONNECTED" -> State.DISCONNECTED
                    else -> State.UNKNOWN
                }
                put(
                    h.optString("source"),
                    ConnectorHealth(
                        source = h.optString("source"),
                        state = state,
                        lastSeenAt = h.optStringOrNull("last_seen_at"),
                        lastEventAt = h.optStringOrNull("last_event_at"),
                        lastFailureAt = h.optStringOrNull("last_failure_at"),
                        lastFailureReason = h.optStringOrNull("last_failure_reason"),
                    ),
                )
            }
        }
    }

    /** Worst-state merge across several sources for one card's health line. */
    fun merge(health: Map<String, ConnectorHealth>, vararg sources: String): ConnectorHealth? {
        val rows = sources.mapNotNull { health[it] }.filter { it.state != State.UNKNOWN }
        if (rows.isEmpty()) return null
        val worst = when {
            rows.any { it.state == State.DISCONNECTED } -> State.DISCONNECTED
            rows.any { it.state == State.DEGRADED } -> State.DEGRADED
            else -> State.CONNECTED
        }
        // Healthy → show the freshest activity; degraded → show the STALE
        // source's age, which is the thing the user needs to act on.
        val lastSeen = rows.mapNotNull { it.lastSeenAt }.maxOrNull()
        val lastEvent = if (worst == State.CONNECTED) {
            rows.mapNotNull { it.lastEventAt ?: it.lastSeenAt }.maxOrNull()
        } else {
            rows.filter { it.state == worst }.mapNotNull { it.lastSeenAt }.minOrNull()
        }
        val lastFailure = rows.mapNotNull { it.lastFailureAt }.maxOrNull()
        return ConnectorHealth(
            source = sources.first(),
            state = worst,
            lastSeenAt = lastSeen,
            lastEventAt = lastEvent,
            lastFailureAt = lastFailure,
            lastFailureReason = rows.firstOrNull { it.lastFailureAt == lastFailure }?.lastFailureReason,
        )
    }
}
