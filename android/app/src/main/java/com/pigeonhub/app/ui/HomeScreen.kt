package com.pigeonhub.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import com.pigeonhub.app.push.InboxStore
import com.pigeonhub.app.push.NotificationChannels
import com.pigeonhub.app.push.PushPayload

@Composable
fun HomeScreen(tap: TapInfo?, showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val entries by InboxStore.entries.collectAsState()
    val notificationsEnabled = rememberNotificationsEnabled()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showSnackbar(if (granted) "Notifications enabled" else "Permission denied")
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PigeonHub",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (entries.isNotEmpty()) {
                IconButton(onClick = { InboxStore.clear() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear inbox")
                }
            }
        }

        if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications are off", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Allow notifications so pushes from PigeonHub can reach you.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text("Allow notifications")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("No notifications yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pushes you receive will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = {
                        showSnackbar(TestPushes.send(context, priority = "normal"))
                    }) {
                        Text("Send a test push")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(entries, key = { it.payload.messageId + it.receivedAtMillis }) { entry ->
                    EntryCard(
                        entry = entry,
                        highlighted = tap?.messageId == entry.payload.messageId,
                        showSnackbar = showSnackbar,
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: InboxStore.Entry,
    highlighted: Boolean,
    showSnackbar: (String) -> Unit,
) {
    val context = LocalContext.current
    val payload = entry.payload
    val containerModifier = if (highlighted) {
        Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else {
        Modifier.fillMaxWidth()
    }

    ElevatedCard(containerModifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (payload.priority == PushPayload.Priority.HIGH) "HIGH" else "NORMAL")
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.receivedAtMillis).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                payload.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(payload.message, style = MaterialTheme.typography.bodyMedium)
            payload.sentAt?.let {
                Text(
                    "sent_at: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (highlighted) {
                Text(
                    "Opened from notification",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            payload.url?.let { url ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                    )
                    FilledTonalButton(onClick = {
                        if (!UrlOpener.open(context, url)) showSnackbar("No browser found")
                    }) {
                        Text("Open")
                    }
                }
            }
        }
    }
}
