package com.pigeonhub.app.ui

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.core.app.NotificationManagerCompat
import com.pigeonhub.app.BuildConfig
import com.pigeonhub.app.data.InboxDatabase
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.InstallationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Honest delivery states for the user-facing test notification (MVP-002A). */
enum class DeliveryState(val userText: String) {
    SERVER_ACCEPTED(
        "Saved on the PigeonHub server. Delivery to this device hasn't been confirmed yet.",
    ),
    DEVICE_PUSH_RECEIVED(
        "This device received the push.",
    ),
    SYNC_RECOVERED_ONLY(
        "The push wasn't confirmed, but the message was recovered into your Inbox.",
    ),
    OS_NOTIFICATION_DISABLED(
        "Android notifications are off. The message still appears in your Inbox.",
    ),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyPushScreen(showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val installState by InstallationRepository.state.collectAsState()
    val notificationsEnabled = rememberNotificationsEnabled()
    var deliveryState by remember { mutableStateOf<DeliveryState?>(null) }
    var testing by remember { mutableStateOf(false) }
    var connectDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "My Push",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("My Push", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Connected",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "Endpoint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        installState.endpoint ?: "(unknown)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        clipboard(context, installState.endpoint ?: "")
                        showSnackbar("Endpoint copied")
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy endpoint")
                    }
                    FilledTonalButton(onClick = {
                        val curl = InstallationRepository.buildCurl()
                        if (curl !== null) {
                            clipboard(context, curl)
                            showSnackbar("cURL with your send key copied — paste it into a terminal")
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy cURL")
                    }
                }
                Button(onClick = {
                    testing = true
                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                    scope.launch {
                        // OS gate first: an off switch must never be reported as success.
                        val nm = NotificationManagerCompat.from(context)
                        val osAllowed = nm.areNotificationsEnabled()
                        if (!osAllowed) {
                            deliveryState = DeliveryState.OS_NOTIFICATION_DISABLED
                            testing = false
                            return@launch
                        }
                        val (ok, detail) = InstallationRepository.sendTestNotification(
                            title = "Test notification",
                            message = "If you can read this, your phone is connected to PigeonHub.",
                        )
                        if (!ok) {
                            deliveryState = null
                            withContext(Dispatchers.Main) { showSnackbar("send failed ($detail)") }
                            testing = false
                            return@launch
                        }
                        // SERVER_ACCEPTED is now true (stored in D1). Wait for the
                        // device hop: the FCM callback stamps device_received_at.
                        val messageId = InstallationRepository.lastTestMessageId ?: ""
                        var received = false
                        var waited = 0L
                        while (waited < 15_000L) {
                            delay(500)
                            waited += 500
                            val row = InboxDatabase.get(context).inboxDao().byId(messageId)
                            if (row?.device_received_at != null) {
                                received = true
                                break
                            }
                        }
                        deliveryState = if (received) {
                            DeliveryState.DEVICE_PUSH_RECEIVED
                        } else {
                            DeliveryState.SYNC_RECOVERED_ONLY
                        }
                        testing = false
                    }
                }) {
                    Text("Send test notification")
                }
                deliveryState?.let { state ->
                    Text(
                        state.userText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (state) {
                            DeliveryState.DEVICE_PUSH_RECEIVED -> MaterialTheme.colorScheme.primary
                            DeliveryState.OS_NOTIFICATION_DISABLED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (testing) {
                    Text(
                        "Sending…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Send key", style = MaterialTheme.typography.titleMedium)
                Text(
                    "••••••••••  (v${installState.writeTokenVersion})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Anyone with this key can send pushes to your phone. " +
                        "Regenerate it if it ever leaves your control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = {
                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                    scope.launch {
                        val next = InstallationRepository.rotateWriteToken(context)
                        next.lastError?.let { showSnackbar("rotation failed: $it") }
                            ?: showSnackbar("Send key rotated to v${next.writeTokenVersion}")
                    }
                }) {
                    Text("Regenerate send key")
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect an automation", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Send a message from any script, CI job, or terminal: copy your " +
                        "endpoint and send key, then make a POST request. Or start " +
                        "from the GitHub Actions example below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val curl = InstallationRepository.buildCurl()
                        if (curl !== null) {
                            clipboard(context, curl)
                            showSnackbar("cURL copied")
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy cURL")
                    }
                    FilledTonalButton(onClick = { connectDialog = true }) {
                        Text("GitHub Actions example")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (connectDialog) {
        AlertDialog(
            onDismissRequest = { connectDialog = false },
            confirmButton = {
                TextButton(onClick = { connectDialog = false }) { Text("Close") }
            },
            title = { Text("GitHub Actions example") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1. In your GitHub repo, open Settings → Secrets and variables " +
                            "→ Actions and add two repository secrets using the copy " +
                            "buttons below:\n" +
                            "   PIGEONHUB_ENDPOINT\n   PIGEONHUB_TOKEN",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "PIGEONHUB_ENDPOINT",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            clipboard(context, installState.endpoint ?: "")
                            showSnackbar("endpoint copied")
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "copy endpoint")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "PIGEONHUB_TOKEN",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            val curl = InstallationRepository.buildCurl()
                            val tokenPart = curl?.substringAfter("Bearer ")?.substringBefore("\"")
                            if (tokenPart !== null) clipboard(context, tokenPart)
                            showSnackbar("send key copied")
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "copy send key")
                        }
                    }
                    Text(
                        "2. Add this workflow to your repo (it never fails your CI if " +
                            "the notification can't be sent):",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SelectionContainer {
                        Text(
                            GITHUB_WORKFLOW_EXAMPLE,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    FilledTonalButton(onClick = {
                        clipboard(context, GITHUB_WORKFLOW_EXAMPLE)
                        showSnackbar("workflow copied")
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy workflow")
                    }
                }
            },
        )
    }
}

private val GITHUB_WORKFLOW_EXAMPLE = """name: PigeonHub notify
on:
  workflow_dispatch:
  push:
    branches: [main]
jobs:
  notify:
    runs-on: ubuntu-latest
    steps:
      - name: Send PigeonHub notification
        env:
          PIGEONHUB_ENDPOINT: ${'$'}{{ secrets.PIGEONHUB_ENDPOINT }}
          PIGEONHUB_TOKEN: ${'$'}{{ secrets.PIGEONHUB_TOKEN }}
        run: |
          curl -sS --max-time 20 -X POST "${'$'}PIGEONHUB_ENDPOINT" \
            -H "Authorization: Bearer ${'$'}PIGEONHUB_TOKEN" \
            -H "Content-Type: application/json" \
            -d '{"title":"Build finished","message":"Your job on main completed.","priority":"normal"}' \
            || echo "PigeonHub notification failed (non-fatal)"
"""

private fun clipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    manager.setPrimaryClip(
        android.content.ClipData.newPlainText("PigeonHub", text),
    )
}
