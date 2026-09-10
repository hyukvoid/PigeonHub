package com.pigeonhub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.pigeonhub.app.push.InboxStore
import com.pigeonhub.app.push.NotificationChannels
import com.pigeonhub.app.push.NotificationRenderer
import com.pigeonhub.app.ui.PigeonHubApp
import com.pigeonhub.app.ui.TapInfo
import com.pigeonhub.app.ui.theme.PigeonHubTheme

class MainActivity : ComponentActivity() {

    private val tap = mutableStateOf<TapInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationChannels.ensureCreated(this)
        enableEdgeToEdge()
        setContent {
            PigeonHubTheme {
                PigeonHubApp(tap = tap.value)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val messageId = intent?.getStringExtra(NotificationRenderer.EXTRA_MESSAGE_ID) ?: return
        InboxStore.markTapped(messageId)
        tap.value = TapInfo(
            messageId = messageId,
            url = intent.getStringExtra(NotificationRenderer.EXTRA_URL),
        )
    }
}
