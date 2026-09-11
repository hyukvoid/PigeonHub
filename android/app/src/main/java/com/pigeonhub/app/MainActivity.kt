package com.pigeonhub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.pigeonhub.app.push.NotificationChannels
import com.pigeonhub.app.push.NotificationRenderer
import com.pigeonhub.app.data.InboxDatabase
import com.pigeonhub.app.push.installation.InstallationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.pigeonhub.app.ui.PigeonHubApp
import com.pigeonhub.app.ui.TapInfo
import com.pigeonhub.app.ui.theme.PigeonHubTheme

class MainActivity : ComponentActivity() {

    private val tap = mutableStateOf<TapInfo?>(null)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationChannels.ensureCreated(this)
        InstallationRepository.start(this)
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
        // The message is already durable (PushPipeline persists before notifying);
        // a tap only flips the LOCAL unread bit.
        appScope.launch {
            InboxDatabase.get(applicationContext).inboxDao().markRead(messageId, System.currentTimeMillis())
        }
        tap.value = TapInfo(
            messageId = messageId,
            url = intent.getStringExtra(NotificationRenderer.EXTRA_URL),
        )
    }
}
