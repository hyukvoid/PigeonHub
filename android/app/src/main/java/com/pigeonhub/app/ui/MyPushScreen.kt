package com.pigeonhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.push.FirebaseGate
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.InstallationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * My Push: bootstrap a fresh installation (invite code) and manage the private
 * push channel. FCM tokens are never shown; the write token only reaches the
 * clipboard through the explicit Copy cURL action.
 */
@Composable
fun MyPushScreen(showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val state by InstallationRepository.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var inviteCode by remember { mutableStateOf("") }

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

        when (state.status) {
            BootstrapStatus.UNINITIALIZED, BootstrapStatus.LOCAL_CREDENTIALS_READY -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Install PigeonHub", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Enter the invite code you received, then this device creates its " +
                                "own private push channel. Credentials are generated and stored " +
                                "on this device only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { inviteCode = it.trim() },
                            label = { Text("Invite code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val fcmToken = withContext(Dispatchers.IO) {
                                        val gate = FirebaseGate.fetchTokenBlocking(context)
                                        (gate as? FirebaseGate.FcmState.Ready)?.token
                                    }
                                    if (fcmToken === null) {
                                        showSnackbar("FCM not ready — check Firebase setup")
                                    } else {
                                        val result = InstallationRepository.bootstrap(context, inviteCode, fcmToken)
                                        result.lastError?.let { showSnackbar("bootstrap failed: $it") }
                                    }
                                }
                            },
                            enabled = inviteCode.length >= 6 && !state.busy,
                        ) {
                            Text("Install")
                        }
                    }
                }
            }

            BootstrapStatus.REGISTERING -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Setting up PigeonHub...")
                    }
                }
            }

            BootstrapStatus.REGISTERED -> {
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
                                state.endpoint ?: "(unknown)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                clipboard.setText(AnnotatedString(state.endpoint ?: ""))
                                showSnackbar("Endpoint copied")
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Copy endpoint")
                            }
                            FilledTonalButton(onClick = {
                                val curl = InstallationRepository.buildCurl()
                                if (curl !== null) {
                                    clipboard.setText(AnnotatedString(curl))
                                    showSnackbar("cURL with your write token copied — paste it into a terminal")
                                }
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Copy cURL")
                            }
                        }
                        Button(onClick = {
                            scope.launch {
                                val (ok, detail) = InstallationRepository.sendTestNotification(
                                    title = "My first PigeonHub push",
                                    message = "Sent from this device through the Worker, D1 and FCM.",
                                )
                                showSnackbar(if (ok) "accepted ($detail) — notification incoming" else "publish failed ($detail)")
                            }
                        }) {
                            Text("Send test notification")
                        }
                    }
                }

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Write credential", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "••••••••••  (v${state.writeTokenVersion})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        FilledTonalButton(onClick = {
                            scope.launch {
                                val next = InstallationRepository.rotateWriteToken(context)
                                next.lastError?.let { showSnackbar("rotation failed: $it") }
                                    ?: showSnackbar("Write token rotated to v${next.writeTokenVersion}")
                            }
                        }) {
                            Text("Regenerate")
                        }
                    }
                }
            }

            BootstrapStatus.RECOVERY_REQUIRED -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Recovery required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "The encryption key for your stored credentials is no longer " +
                                "available on this device, so they cannot be recovered. " +
                                "PigeonHub will not overwrite the existing installation; " +
                                "reinstall the app and use a new invite code.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
