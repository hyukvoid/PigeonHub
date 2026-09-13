package com.pigeonhub.app.ui

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.BuildConfig
import com.pigeonhub.app.push.DevicePrefs
import com.pigeonhub.app.push.FirebaseGate
import com.pigeonhub.app.push.NotificationChannels
import com.pigeonhub.app.push.installation.InstallationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeviceScreen(showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Device / Debug",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        FcmCard(onCopyToken = { showSnackbar("Token copied to clipboard") })

        PermissionCard()

        ChannelsCard()

        JobEventCard(showSnackbar)
                TestPushCard(showSnackbar = showSnackbar)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("App info", style = MaterialTheme.typography.titleMedium)
                InfoRow("applicationId", BuildConfig.APPLICATION_ID)
                InfoRow("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Send push from adb", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Works without Firebase — exercises the same pipeline (validate → dedupe → render). " +
                        "Add --es message_id \"<id>\" to test duplicate delivery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        ADB_COMMAND,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                FilledTonalButton(onClick = {
                    clipboard.setText(AnnotatedString(ADB_COMMAND))
                    showSnackbar("adb command copied")
                }) {
                    Text("Copy adb command")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FcmCard(onCopyToken: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configured = remember { FirebaseGate.isConfigured(context) }
    var fcmState by remember { mutableStateOf<FirebaseGate.FcmState?>(null) }
    val scope = rememberCoroutineScope()

    fun checkToken() {
        scope.launch {
            fcmState = withContext(Dispatchers.IO) {
                FirebaseGate.fetchTokenBlocking(context)
            }
        }
    }

    // Firebase configured: fetch the real FCM registration token automatically
    // on first entry; the button stays for manual re-checks.
    LaunchedEffect(configured) {
        if (configured) checkToken()
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FCM registration", style = MaterialTheme.typography.titleMedium)
            if (!configured) {
                Text(
                    "BLOCKED_PENDING_FIREBASE_SETUP",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Register com.pigeonhub.app in your Firebase console, download the matching " +
                        "google-services.json into android/app/, apply the google-services plugin and " +
                        "rebuild. Full steps: docs/FIREBASE_SETUP.md",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FilledTonalButton(onClick = { checkToken() }) {
                    Text("Re-check FCM registration")
                }
            }

            val state = fcmState
            when (state) {
                is FirebaseGate.FcmState.Ready -> {
                    Text(
                        "token: ${FirebaseGate.maskToken(state.token)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "Full token is hidden here on purpose. Copy it and set it as " +
                            "FCM_DEVICE_TOKEN for the local sender.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = {
                        clipboard.setText(AnnotatedString(state.token))
                        onCopyToken()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy token")
                    }
                }
                is FirebaseGate.FcmState.Error ->
                    Text(
                        "token fetch failed: ${state.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                FirebaseGate.FcmState.NotConfigured -> Unit
                null -> Unit
            }

            DevicePrefs.loadFcmToken(context)?.let { stored ->
                Text(
                    "last stored token: ${FirebaseGate.maskToken(stored)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    val enabled = rememberNotificationsEnabled()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notification permission", style = MaterialTheme.typography.titleMedium)
            Text(
                if (enabled) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            FilledTonalButton(onClick = { Permissions.openAppNotificationSettings(context) }) {
                Text("Open Android notification settings")
            }
        }
    }
}

@Composable
private fun ChannelsCard() {
    val context = LocalContext.current
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notification channels", style = MaterialTheme.typography.titleMedium)
            listOf(
                NotificationChannels.CHANNEL_NORMAL to "PigeonHub Normal",
                NotificationChannels.CHANNEL_HIGH to "PigeonHub High",
            ).forEach { (id, fallbackName) ->
                val channel = manager.getNotificationChannel(id)
                val importance = channel?.importance ?: NotificationManager.IMPORTANCE_NONE
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(channel?.name?.toString() ?: fallbackName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Permissions.importanceLabel(importance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { Permissions.openChannelSettings(context, id) }) {
                        Text("Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun TestPushCard(showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Test push", style = MaterialTheme.typography.titleMedium)
            Text(
                "Runs the production pipeline locally (validate → dedupe → render → inbox).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    showSnackbar(TestPushes.send(context, priority = "normal"))
                }) { Text("NORMAL") }
                FilledTonalButton(onClick = {
                    showSnackbar(TestPushes.send(context, priority = "high"))
                }) { Text("HIGH") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    showSnackbar(TestPushes.send(context, priority = "high", url = "https://example.com/pigeonhub"))
                }) { Text("HIGH + URL") }
                FilledTonalButton(onClick = {
                    showSnackbar(TestPushes.sendDuplicate(context, priority = "normal"))
                }) { Text("DUPLICATE x2") }
            }
        }
    }
}

@Composable
private fun JobEventCard(showSnackbar: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun send(label: String, jobJson: String?) {
        busy = true
        scope.launch {
            val (ok, detail) = InstallationRepository.sendJobEvent(
                title = "Job event",
                message = "debug publish: " + label,
                jobJson = jobJson,
            )
            busy = false
            showSnackbar((if (ok) "sent: " else "FAILED: ") + label + " (" + detail + ")")
        }
    }

    val runningJob = "{\"source\":\"debug\",\"job_id\":\"e2e-1\",\"state\":\"RUNNING\",\"job_name\":\"Debug crawler\",\"started_at\":\"2026-09-14T01:00:00Z\"}"
    val progressJob = "{\"source\":\"debug\",\"job_id\":\"e2e-1\",\"state\":\"PROGRESS\",\"progress_current\":18431,\"progress_total\":50000}"
    val doneJob = "{\"source\":\"debug\",\"job_id\":\"e2e-1\",\"state\":\"DONE\",\"finished_at\":\"2026-09-14T01:18:00Z\",\"result_summary\":\"50,000 rows in 18m\"}"
    val failedJob = "{\"source\":\"debug\",\"job_id\":\"e2e-2\",\"state\":\"FAILED\",\"job_name\":\"Product crawler\",\"attention_reason\":\"HTTP 429\"}"
    val attentionJob = "{\"source\":\"debug\",\"job_id\":\"e2e-3\",\"state\":\"NEEDS_ACTION\",\"job_name\":\"Migration approval\",\"attention_reason\":\"approve migration\"}"

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Job events (MVP-005 E2E)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Publishes through the REAL worker path (Worker → D1 → FCM → device). RUNNING/PROGRESS are inbox-only; DONE/FAILED/NEEDS_ACTION notify.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { send("RUNNING", runningJob) }, enabled = !busy) { Text("RUNNING") }
                FilledTonalButton(onClick = { send("PROGRESS", progressJob) }, enabled = !busy) { Text("PROGRESS") }
                FilledTonalButton(onClick = { send("DONE", doneJob) }, enabled = !busy) { Text("DONE") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { send("FAILED", failedJob) }, enabled = !busy) { Text("FAILED") }
                FilledTonalButton(onClick = { send("NEEDS_ACTION", attentionJob) }, enabled = !busy) { Text("ATTENTION") }
                FilledTonalButton(onClick = { send("DONE-dup", doneJob) }, enabled = !busy) { Text("DONE dup") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val ADB_COMMAND =
    "adb shell am broadcast -a app.pigeonhub.debug.TEST_PUSH " +
        "-n com.pigeonhub.app/.debug.TestPushReceiver " +
        "--es title \"Build Complete\" --es message \"Deployment succeeded\" " +
        "--es priority high --es url https://example.com"
