package com.pigeonhub.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.BuildConfig
import com.pigeonhub.app.push.NotificationChannels

@Composable
fun SettingsScreen(showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val enabled = rememberNotificationsEnabled()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "Permission granted" else "Permission not granted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                FilledTonalButton(onClick = { Permissions.openAppNotificationSettings(context) }) {
                    Text("App notification settings")
                }
                FilledTonalButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) {
                    Text("Android system settings")
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Channels", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = {
                    Permissions.openChannelSettings(context, NotificationChannels.CHANNEL_NORMAL)
                }) {
                    Text("PigeonHub Normal")
                }
                FilledTonalButton(onClick = {
                    Permissions.openChannelSettings(context, NotificationChannels.CHANNEL_HIGH)
                }) {
                    Text("PigeonHub High")
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text(
                    "PigeonHub — push notifications for developers, straight from a " +
                        "curl request to your pocket.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "applicationId: ${BuildConfig.APPLICATION_ID}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "version: ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "FCM integration: BLOCKED_PENDING_FIREBASE_SETUP (docs/FIREBASE_SETUP.md)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
