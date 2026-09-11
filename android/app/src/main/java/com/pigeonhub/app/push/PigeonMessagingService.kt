package com.pigeonhub.app.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pigeonhub.app.push.installation.InstallationRepository

/**
 * FCM entry point. Inert until the project owner adds a real google-services.json
 * (see docs/FIREBASE_SETUP.md) — without it FirebaseApp never initializes and
 * this service is simply never invoked.
 *
 * The server sends DATA-ONLY messages; rendering never waits on a network call.
 */
class PigeonMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        if (data.isEmpty()) {
            // Notification-only messages cannot carry our schema; ignore them
            // rather than rendering a notification we cannot validate.
            Log.w(TAG, "received FCM message without data payload; ignored")
            return
        }
        PushPipeline.handle(this, data, source = "fcm")
    }

    override fun onNewToken(token: String) {
        // Persist for the debug screen only. Never log the full token.
        DevicePrefs.saveFcmToken(applicationContext, token)
        Log.i(TAG, "FCM token rotated (length=${token.length})")
        // MVP-001C lifecycle: authenticated, versioned server update.
        InstallationRepository.onNewToken(applicationContext, token)
    }

    private companion object {
        const val TAG = "PigeonHub"
    }
}
