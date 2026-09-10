package com.pigeonhub.app.push

import android.content.Context

/**
 * Dev-only local storage for the FCM registration token (a device identifier —
 * must never be committed anywhere or logged in full). Night-001 has no device
 * registry; this exists so the token can be copied from the debug screen into
 * the local sender's environment once Firebase is configured.
 */
object DevicePrefs {

    private const val PREFS_NAME = "pigeonhub_dev"
    private const val KEY_FCM_TOKEN = "fcm_token"

    fun saveFcmToken(context: Context, token: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    fun loadFcmToken(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
}
