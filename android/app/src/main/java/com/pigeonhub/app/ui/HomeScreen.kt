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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    // MVP-011.5A: ticks every 60s while resumed; also jumps on fg return.
    val now = rememberTickingNow()

    // MVP-011.5B/C: durable delete. Server tombstone first, local rows after.
    val scope = rememberCoroutineScope()
    var deleteMenuOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val deletedOneMsg = stringResource(R.string.inbox_deleted_one)
    val deletedAllMsg = stringResource(R.string.inbox_deleted_all)
    val deleteFailedMsg = stringResource(R.string.inbox_delete_failed)
    val displayItems = remember(entries) { entries?.let { collapseInboxItems(it) } }
    var swipeResetEpoch by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    fun performDelete(messageIds: List<String>, all: Boolean = false, onFailureRestore: (() -> Unit)? = null) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                InstallationRepository.deleteMessages(messageIds)
            }
            if (ok) {
                withContext(Dispatchers.IO) {
                    InboxDatabase.get(context).inboxDao().deleteByMessageIds(messageIds)
                }
                // A single Job Card carries several events — label by intent,
                // not by row count.
                showSnackbar(if (all) deletedAllMsg else deletedOneMsg)
            } else {
                onFailureRestore?.invoke()
                showSnackbar(deleteFailedMsg)
            }
        }
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
            IconButton(
                onClick = { deleteMenuOpen = true },
                enabled = !entries.isNullOrEmpty(),
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.inbox_delete_all_menu))
            }
            DropdownMenu(expanded = deleteMenuOpen, onDismissRequest = { deleteMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.inbox_delete_all_menu)) },
                    onClick = {
                        deleteMenuOpen = false
                        confirmDeleteAll = true
                    },
                )
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

        if (confirmDeleteAll) {
            AlertDialog(
                onDismissRequest = { confirmDeleteAll = false },
                title = { Text(stringResource(R.string.inbox_delete_all_title)) },
                text = { Text(stringResource(R.string.inbox_delete_all_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDeleteAll = false
                        val ids = displayItems.orEmpty().flatMap { item ->
                            when (item) {
                                is InboxItem.Message -> listOf(item.entry.message_id)
                                is InboxItem.Job -> item.events.map { it.message_id }
                            }
                        }
                        performDelete(ids, all = true)
                    }) { Text(stringResource(R.string.inbox_delete_all_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteAll = false }) {
                        Text(stringResource(R.string.common_close))
                    }
                },
            )
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(displayItems.orEmpty(), key = { item ->
                        when (item) {
                            is InboxItem.Message -> "m:" + item.entry.message_id
                            is InboxItem.Job -> "j:" + item.jobKey
                        }
                    }) { item ->
                        val ids = when (item) {
                            is InboxItem.Message -> listOf(item.entry.message_id)
                            is InboxItem.Job -> item.events.map { it.message_id }
                        }
                        val currentIds by rememberUpdatedState(ids)
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    performDelete(currentIds) { swipeResetEpoch++ }
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        // Server refused the delete: snap the swiped row back
                        // so a failed delete never leaves an invisible gap.
                        LaunchedEffect(swipeResetEpoch) {
                            if (swipeResetEpoch > 0) {
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Row(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.inbox_delete_action),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            },
                        ) {
                            when (item) {
                                is InboxItem.Message -> EntryCard(
                                    entry = item.entry,
                                    highlighted = tap?.messageId == item.entry.message_id,
                                    showSnackbar = showSnackbar,
                                    now = now,
                                )
                                is InboxItem.Job -> JobCard(
                                    item = item,
                                    showSnackbar = showSnackbar,
                                    now = now,
                                )
                            }
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
    now: Long,
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
                    relativeLabel(entry.local_received_at, now),
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
private fun JobCard(item: InboxItem.Job, showSnackbar: (String) -> Unit, now: Long) {
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
                    relativeLabel(item.entry.local_received_at, now),
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
            val elapsedInfo = if (state == JobPayload.State.RUNNING && started != null) {
                runCatching {
                    val startMs = java.time.OffsetDateTime.parse(started).toInstant().toEpochMilli()
                    RelativeTime.elapsedRes(startMs, now)
                }.getOrNull()
            } else {
                null
            }
            if (elapsedInfo != null) {
                val (res, args) = elapsedInfo
                Text(
                    stringResource(R.string.job_elapsed, stringResource(res, *args.toTypedArray())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/**
 * MVP-011.5A: the current wall clock, refreshed every [intervalMs] while this
 * screen is resumed and immediately on background->foreground return. Relative
 * labels are derived from THIS value + the stored message timestamp — the
 * formatted string is never persisted, so it can never go stale.
 */
@Composable
private fun rememberTickingNow(intervalMs: Long = 60_000L): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now = System.currentTimeMillis()
            while (true) {
                delay(intervalMs)
                now = System.currentTimeMillis()
            }
        }
    }
    return now
}

@Composable
private fun relativeLabel(timestampMs: Long, nowMs: Long): String {
    val time = RelativeTime.compute(timestampMs, nowMs)
    val res = RelativeTime.labelRes(time)
    return if (RelativeTime.hasCountArg(time)) {
        stringResource(res, RelativeTime.countArg(time))
    } else {
        stringResource(res)
    }
}
