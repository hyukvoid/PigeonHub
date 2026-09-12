package com.pigeonhub.app.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * In-app appearance override (MVP-004): System / Light / Dark. Default System.
 * Stored in its own tiny DataStore file so the push pipeline's prefs are never
 * touched by UI code.
 */
enum class Appearance { SYSTEM, LIGHT, DARK;

    companion object {
        fun from(name: String?): Appearance =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

private val Context.appearanceStore by preferencesDataStore(name = "pigeonhub_appearance")

object AppearancePrefs {

    private val KEY = stringPreferencesKey("appearance")

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _mode = MutableStateFlow(Appearance.SYSTEM)
    val mode: StateFlow<Appearance> = _mode.asStateFlow()

    fun load(context: Context) {
        appScope.launch {
            context.appearanceStore.data.collect { prefs ->
                _mode.value = Appearance.from(prefs[KEY])
            }
        }
    }

    fun set(context: Context, value: Appearance) {
        appScope.launch {
            context.appearanceStore.edit { prefs -> prefs[KEY] = value.name }
            _mode.value = value
        }
    }
}
