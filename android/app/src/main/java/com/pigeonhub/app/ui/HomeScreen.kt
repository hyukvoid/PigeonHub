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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Circle
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pigeonhub.app.R
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
import com.pigeonhub.app.data.InboxItem
import com.pigeonhub.app.data.collapseInboxItems
import com.pigeonhub.app.push.JobPayload
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
    // null = Room hasn't emitted yet (cold start). Rendering the empty state
    // for this window flashed "no messages" to users with real messages.
    val entries: List<InboxMessage>? by dao.flowAll()
        .collectAsStateWithLifecycle(initialValue = null)
    val notificationsEnabled = rememberNotificationsEnabled()
    val syncUi by InstallationRepository.inboxSyncState.collectAsState()
    val registered = InstallationRepository.state.collectAsState().value.status ==
        BootstrapStatus.REGISTERED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showSnackbar(context.getString(if (granted) R.string.inbox_notifications_enabled else R.string.inbox_permission_denied))
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
                stringResource(R.string.inbox_title),
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
                Text(stringResource(R.string.inbox_refresh))
            }
        }

        // ---- sync / connectivity banners (never merged into empty states) ----
        syncUi.lastSummary?.error?.let { error ->
            OfflineBanner(
                text = stringResource(R.string.inbox_offline_banner),
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
                    Text(stringResource(R.string.inbox_notifications_off_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.inbox_notifications_off_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text(stringResource(R.string.inbox_allow_notifications))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            // (0) Room hasn't emitted yet: draw nothing instead of the empty state.
            entries == null -> Box(Modifier.fillMaxSize())

            // (1) Not configured: onboarding owns the app; this is a defensive state.
            !registered -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.inbox_empty_not_configured_title),
                    textAlign = TextAlign.Center,
                )
            }

            // (2) Configured but no messages yet.
            entries.orEmpty().isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    Text(stringResource(R.string.inbox_empty_configured_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.inbox_empty_configured_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onOpenMyPush) {
                        Text(stringResource(R.string.inbox_empty_cta))
                    }
                }
            }

            // (3)/(4) populated; offline/sync-failure banner already shown above.
            else -> {
                val items = remember(entries) { collapseInboxItems(entries.orEmpty()) }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items, key = { item ->
                        when (item) {
                            is InboxItem.Message -> "m:" + item.entry.message_id
                            is InboxItem.Job -> "j:" + item.jobKey
                        }
                    }) { item ->
                        when (item) {
                            is InboxItem.Message -> EntryCard(
                                entry = item.entry,
                                highlighted = tap?.messageId == item.entry.message_id,
                                showSnackbar = showSnackbar,
                            )
                            is InboxItem.Job -> JobCard(item = item, showSnackbar = showSnackbar)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
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
            TextButton(onClick = onRetry) { Text(stringResource(R.string.inbox_retry)) }
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
                        Text(stringResource(if (entry.priority == "high") R.string.inbox_priority_high else R.string.inbox_priority_normal))
                    },
                )
                if (!entry.is_read) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.inbox_new),
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

            // Push ingestion is https-only; hide legacy rows whose stored url
            // is junk (e.g. the literal "null" written by the old parser).
            val displayUrl = entry.url?.takeIf { it.startsWith("https://") }
            displayUrl?.let { url ->
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
                        if (!UrlOpener.open(context, url)) showSnackbar(context.getString(R.string.inbox_no_browser))
                    }) {
                        Text(stringResource(R.string.inbox_open))
                    }
                }
            }
        }
    }
}

/**
 * MVP-006: one card per structured job (same source+job_id collapses; the
 * newest event's state wins). State hierarchy: NEEDS_ACTION and FAILED are
 * error-colored, RUNNING uses the primary, DONE uses the muted variant.
 */
@Composable
private fun JobCard(item: InboxItem.Job, showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val state = item.state
    val accent = when (state) {
        JobPayload.State.NEEDS_ACTION, JobPayload.State.FAILED -> MaterialTheme.colorScheme.error
        JobPayload.State.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val stateLabel = stringResource(
        when (state) {
            JobPayload.State.DONE -> R.string.job_state_done
            JobPayload.State.FAILED -> R.string.job_state_failed
            JobPayload.State.NEEDS_ACTION -> R.string.job_state_needs_action
            else -> R.string.job_state_running
        },
    )

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    when (state) {
                        JobPayload.State.DONE -> Icons.Filled.CheckCircle
                        JobPayload.State.FAILED -> Icons.Filled.Cancel
                        JobPayload.State.NEEDS_ACTION -> Icons.Filled.Error
                        else -> Icons.Filled.Circle
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
                if (item.updateCount > 1) {
                    Text(
                        stringResource(R.string.job_updates_suffix, item.updateCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    DateUtils.getRelativeTimeSpanString(item.entry.local_received_at).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                item.source.replaceFirstChar { it.uppercase() } + " \u00b7 " + item.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            val cur = item.progressCurrent
            val total = item.progressTotal
            val detail = buildString {
                if (cur != null && total != null && total > 0) {
                    append(String.format("%,d / %,d \u00b7 %d%%", cur, total, cur * 100 / total))
                } else if (cur != null) {
                    append(String.format("%,d", cur))
                } else {
                    append(item.entry.message)
                }
                item.resultSummary?.takeIf { it != item.entry.message }?.let {
                    if (isNotEmpty()) append("\n")
                    append(it)
                }
                item.attentionReason?.takeIf { it != item.entry.message }?.let {
                    if (isNotEmpty()) append("\n")
                    append(it)
                }
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)

            val started = item.startedAt
            if (state == JobPayload.State.RUNNING && started != null) {
                val elapsed = runCatching {
                    android.text.format.DateUtils.getRelativeTimeSpanString(
                        java.time.OffsetDateTime.parse(started).toInstant().toEpochMilli(),
                        item.entry.local_received_at,
                        0L,
                        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
                }.getOrNull()
                if (elapsed != null) {
                    Text(
                        stringResource(R.string.job_elapsed, elapsed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item.deepLink?.let { url ->
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
                        if (!UrlOpener.open(context, url)) showSnackbar(context.getString(R.string.inbox_no_browser))
                    }) {
                        Text(stringResource(R.string.inbox_open))
                    }
                }
            }
        }
    }
}

