package com.pigeonhub.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * The ONLY place in the app allowed to touch Firebase singletons.
 *
 * Without a real google-services.json (BLOCKED_PENDING_FIREBASE_SETUP),
 * FirebaseApp never initializes; every accessor below must therefore guard
 * before use so the app runs normally in the unconfigured state.
 */
object FirebaseGate {

    sealed interface FcmState {
        /** Firebase is not configured — the pending-setup state. */
        data object NotConfigured : FcmState
        data class Ready(val token: String) : FcmState
        data class Error(val message: String) : FcmState
    }

    fun isConfigured(context: Context): Boolean = try {
        FirebaseApp.getApps(context.applicationContext).isNotEmpty()
    } catch (_: Throwable) {
        false
    }

    /**
     * Blocking FCM token fetch. Must be called off the main thread.
     * The token is a device identifier: callers must mask it in UI and never
     * log it in full.
     */
    fun fetchTokenBlocking(context: Context): FcmState {
        if (!isConfigured(context)) return FcmState.NotConfigured
        return try {
            val token = Tasks.await(FirebaseMessaging.getInstance().token, 15, TimeUnit.SECONDS)
            FcmState.Ready(token)
        } catch (e: Exception) {
            FcmState.Error(e.message ?: "token fetch failed")
        } catch (e: Throwable) {
            FcmState.Error(e.message ?: "Firebase not initialized")
        }
    }

    /** Masked form safe for UI display / debug copy: never show the middle. */
    fun maskToken(token: String): String =
        if (token.length <= 12) "${token.take(4)}…" else "${token.take(8)}…${token.takeLast(4)} (${token.length} chars)"
}
