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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.pigeonhub.app.BuildConfig
import com.pigeonhub.app.R
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
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_notifications_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        if (enabled) R.string.settings_notifications_granted
                        else R.string.settings_notifications_denied
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                FilledTonalButton(onClick = { Permissions.openAppNotificationSettings(context) }) {
                    Text(stringResource(R.string.settings_open_app_notification_settings))
                }
                FilledTonalButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) {
                    Text(stringResource(R.string.settings_open_system_settings))
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_channels_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(onClick = {
                    Permissions.openChannelSettings(context, NotificationChannels.CHANNEL_NORMAL)
                }) {
                    Text(stringResource(R.string.channel_normal_name))
                }
                FilledTonalButton(onClick = {
                    Permissions.openChannelSettings(context, NotificationChannels.CHANNEL_HIGH)
                }) {
                    Text(stringResource(R.string.channel_high_name))
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_appearance_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                val current by AppearancePrefs.mode.collectAsState()
                val options = listOf(
                    Appearance.SYSTEM to R.string.settings_appearance_system,
                    Appearance.LIGHT to R.string.settings_appearance_light,
                    Appearance.DARK to R.string.settings_appearance_dark,
                )
                options.forEach { (value, labelRes) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .selectable(
                                selected = current == value,
                                role = Role.RadioButton,
                                onClick = { AppearancePrefs.set(context, value) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = { AppearancePrefs.set(context, value) },
                        )
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
