package com.pigeonhub.app.push

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MVP-010: user-facing job alert toggles. Three switches, good defaults
 * (all on). No rule builder — RUNNING/progress never notify regardless.
 */
data class JobAlertToggles(
    val done: Boolean = true,
    val failed: Boolean = true,
    val attention: Boolean = true,
)

private val Context.jobAlertStore by preferencesDataStore(name = "pigeonhub_job_alerts")

object JobAlertPrefs {

    private val KEY_DONE = booleanPreferencesKey("alert_done")
    private val KEY_FAILED = booleanPreferencesKey("alert_failed")
    private val KEY_ATTENTION = booleanPreferencesKey("alert_attention")

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _toggles = MutableStateFlow(JobAlertToggles())
    val toggles: StateFlow<JobAlertToggles> = _toggles.asStateFlow()

    fun load(context: Context) {
        appScope.launch {
            context.jobAlertStore.data.collect { prefs ->
                _toggles.value = JobAlertToggles(
                    done = prefs[KEY_DONE] ?: true,
                    failed = prefs[KEY_FAILED] ?: true,
                    attention = prefs[KEY_ATTENTION] ?: true,
                )
            }
        }
    }

    fun set(context: Context, update: (JobAlertToggles) -> JobAlertToggles) {
        appScope.launch {
            context.jobAlertStore.edit { prefs ->
                val next = update(
                    JobAlertToggles(
                        done = prefs[KEY_DONE] ?: true,
                        failed = prefs[KEY_FAILED] ?: true,
                        attention = prefs[KEY_ATTENTION] ?: true,
                    ),
                )
                prefs[KEY_DONE] = next.done
                prefs[KEY_FAILED] = next.failed
                prefs[KEY_ATTENTION] = next.attention
            }
        }
    }
}
