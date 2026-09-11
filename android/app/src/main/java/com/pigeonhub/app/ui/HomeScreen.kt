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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pigeonhub.app.data.InboxDatabase
import com.pigeonhub.app.data.InboxMessage
import com.pigeonhub.app.push.PushPayload
import com.pigeonhub.app.push.installation.InstallationRepository

/**
 * Durable inbox (MVP-001D): the UI renders the Room table as a Flow — the
 * network response is never the screen's source of truth. Offline, the last
 * synced Room contents stay on screen.
 */
@Composable
fun HomeScreen(tap: TapInfo?, showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val dao = InboxDatabase.get(context).inboxDao()
    val entries by dao.flowAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val notificationsEnabled = rememberNotificationsEnabled()
    val syncState by InstallationRepository.inboxSyncState.collectAsState()
    val scope = rememberCoroutineScope()

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
            if (syncState.busy) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    val summary = InstallationRepository.syncInbox(context)
                    showSnackbar(
                        when {
                            summary.error !== null -> "sync failed: ${summary.error}"
                            summary.truncated -> "synced: ${summary.recovered} recovered (older messages expired on server)"
                            else -> "synced: ${summary.recovered} recovered"
                        }
                    )
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh inbox")
            }
        }

        if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications are off", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Allow notifications so pushes from PigeonHub can reach you. " +
                            "Missed pushes are still recovered into this inbox by sync.",
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
                        "Pushes you receive will appear here and stay available in this inbox.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val result = TestPushes.send(context, priority = "normal")
                            showSnackbar(result)
                        }
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
                items(entries, key = { it.message_id }) { entry ->
                    EntryCard(
                        entry = entry,
                        highlighted = tap?.messageId == entry.message_id,
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
    entry: InboxMessage,
    highlighted: Boolean,
    showSnackbar: (String) -> Unit,
) {
    val context = LocalContext.current
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
                        Text(if (entry.priority == PushPayload.Priority.HIGH.name) "HIGH" else "NORMAL")
                    },
                )
                if (!entry.is_read) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "new",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (entry.received_via == "SYNC") {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "recovered",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.local_received_at).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                "seq ${entry.seq} · via ${entry.received_via}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.url?.let { url ->
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
