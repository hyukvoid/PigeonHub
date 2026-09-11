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
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.pigeonhub.app.BuildConfig
import com.pigeonhub.app.data.InboxDatabase
import com.pigeonhub.app.data.InboxMessage
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.InstallationRepository

/**
 * Durable inbox (MVP-001D/002A): the UI renders the Room table as a Flow - the
 * network response is never the screen's source of truth. Offline, the last
 * synced Room contents stay on screen.
 */
@Composable
fun HomeScreen(
    tap: TapInfo?,
    onOpenMyPush: () -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val context = LocalContext.current
    val dao = InboxDatabase.get(context).inboxDao()
    val entries by dao.flowAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val notificationsEnabled = rememberNotificationsEnabled()
    val syncUi by InstallationRepository.inboxSyncState.collectAsState()
    val registered = InstallationRepository.state.collectAsState().value.status ==
        BootstrapStatus.REGISTERED

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
                "Inbox",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (syncUi.busy) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            FilledTonalButton(onClick = {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    InstallationRepository.syncInbox(context)
                }
            }) {
                Text("Refresh")
            }
        }

        // ---- sync / connectivity banners (never merged into empty states) ----
        syncUi.lastSummary?.error?.let { error ->
            OfflineBanner(
                text = "Couldn't refresh - showing saved messages.",
                onRetry = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        InstallationRepository.syncInbox(context)
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
        }

        if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications are off", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Messages still appear in this inbox - only the popup is affected.",
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

        when {
            // (1) Not configured: onboarding owns the app; this is a defensive state.
            !registered -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Finish setup to start receiving messages.",
                    textAlign = TextAlign.Center,
                )
            }

            // (2) Configured but no messages yet.
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    Text("No messages yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Messages from your scripts and automations will appear here - " +
                            "even ones that arrive while you're away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onOpenMyPush) {
                        Text("Send your first test notification")
                    }
                }
            }

            // (3)/(4) populated; offline/sync-failure banner already shown above.
            else -> LazyColumn(
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
private fun OfflineBanner(text: String, onRetry: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
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
                        Text(if (entry.priority == "high") "HIGH" else "NORMAL")
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

            if (BuildConfig.DEBUG) {
                // Developer-only coordinates; never shown in release builds.
                Text(
                    "seq " + entry.seq + " - via " + entry.received_via,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
