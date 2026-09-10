package com.pigeonhub.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pigeonhub.app.BuildConfig
import com.pigeonhub.app.push.PushPipeline
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Debug-only test push injector driven from adb. Compiled into debug builds only
 * (see app/src/debug/AndroidManifest.xml). It feeds the same PushPipeline the
 * FCM service uses, so notification behaviour can be tested without Firebase:
 *
 *   adb shell am broadcast -a app.pigeonhub.debug.TEST_PUSH \
 *     -n com.pigeonhub.app/.debug.TestPushReceiver \
 *     --es title "Build Complete" --es message "Deployment succeeded" \
 *     --es priority high --es url https://example.com \
 *     --es message_id "fixed-id-1"
 */
class TestPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (intent.action != ACTION_TEST_PUSH) return

        val data = mapOf(
            "message_id" to (intent.getStringExtra("message_id") ?: "adb-${UUID.randomUUID()}"),
            "title" to (intent.getStringExtra("title") ?: "PigeonHub test push"),
            "message" to (intent.getStringExtra("message") ?: "Hello from adb"),
            "priority" to (intent.getStringExtra("priority") ?: "normal"),
            "url" to intent.getStringExtra("url"),
            "sent_at" to OffsetDateTime.now().toString(),
            "schema_version" to (intent.getStringExtra("schema_version") ?: "1"),
        )
        PushPipeline.handle(context, data, source = "adb")
    }

    companion object {
        const val ACTION_TEST_PUSH = "app.pigeonhub.debug.TEST_PUSH"
    }
}
