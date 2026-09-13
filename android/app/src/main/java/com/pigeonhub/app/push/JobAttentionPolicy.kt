package com.pigeonhub.app.push

import android.content.Context

/**
 * MVP-010 Attention Policy v1 (base implemented with MVP-005 so job updates
 * can never spam the shade).
 *
 * Defaults (good defaults over configuration):
 *   RUNNING / PROGRESS  → Inbox update only, never a notification
 *   DONE                → notification (normal channel), once per (job, DONE)
 *   FAILED              → notification (high channel), once per (job, FAILED)
 *   NEEDS_ACTION        → notification (high channel), once per (job, NEEDS_ACTION)
 *
 * "Once per (job, state)" is remembered persistently, so a connector retrying
 * a FAILED event with a fresh message_id does not double-alert. A later
 * RUNNING/PROGRESS for the same job re-arms the terminal alerts (re-run).
 *
 * The decision core is a pure function over a mutable state map (unit-tested
 * without Android); the Context wrapper persists that map in SharedPreferences.
 */
object JobAttentionPolicy {

    data class Decision(val notify: Boolean, val reason: String)

    /**
     * Pure policy core. [store] maps "seen:<jobKey>:<STATE>" → epoch millis.
     * Mutates [store]; the caller decides how to persist it.
     */
    fun shouldNotify(job: JobPayload, store: MutableMap<String, Long>, now: Long = System.currentTimeMillis()): Decision {
        val jobKey = job.jobKey

        // A fresh RUNNING/PROGRESS re-arms terminal alerts for a re-run.
        if (job.isInFlight) {
            terminalStates().forEach { store.remove("seen:$jobKey:${it.name}") }
            return Decision(notify = false, reason = "job in flight")
        }

        val stateKey = "seen:$jobKey:${job.state.name}"
        if (store.containsKey(stateKey)) {
            return Decision(notify = false, reason = "job state already delivered")
        }
        store[stateKey] = now

        // Only the latest terminal state stays remembered for this job.
        terminalStates().filter { it != job.state }
            .forEach { store.remove("seen:$jobKey:${it.name}") }

        return Decision(notify = true, reason = "terminal job state ${job.state}")
    }

    /** Context entry point backed by SharedPreferences. */
    fun shouldNotify(context: Context, job: JobPayload): Decision {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val store = prefs.all
            .filterKeys { it.startsWith("seen:") }
            .filterValues { it is Long }
            .mapValues { it.value as Long }
            .toMutableMap()

        val decision = shouldNotify(job, store)

        prefs.edit().apply {
            store.forEach { (key, value) -> putLong(key, value) }
            // Drop remembered states that the pure core removed.
            prefs.all.keys.filter { it.startsWith("seen:") && !store.containsKey(it) }
                .forEach { remove(it) }
        }.apply()
        return decision
    }

    private fun terminalStates() =
        listOf(JobPayload.State.DONE, JobPayload.State.FAILED, JobPayload.State.NEEDS_ACTION)

    private const val PREFS_NAME = "pigeonhub_job_policy"
}
