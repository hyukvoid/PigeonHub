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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.R
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.InstallationRepository
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.size

@Composable
fun ConnectionsScreen(
    onOpenMyPush: () -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val installState by InstallationRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val registered = installState.status == BootstrapStatus.REGISTERED

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.connections_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        // ---- GitHub card ----
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.github_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.github_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.github_connected),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val connectLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { onOpenMyPush() }
                Button(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(com.pigeonhub.app.push.installation.GitHubConnect.installUrl()),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    connectLauncher.launch(intent)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.github_connect))
                }
            }
        }

        // ---- My Push (existing push channel management) ----
        if (registered) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.connections_mypush_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.connections_status_connected),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(R.string.connections_endpoint_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        installState.endpoint ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onOpenMyPush) {
                            Text(stringResource(R.string.connections_copy_curl))
                        }
                    }

                    // Real Worker → D1 → FCM → Android test notification
                    val context = LocalContext.current
                    var testBusy by remember { mutableStateOf(false) }
                    var testResult by remember { mutableStateOf<String?>(null) }
                    Button(
                        onClick = {
                            testBusy = true
                            testResult = null
                            scope.launch {
                                val (ok, detail) = InstallationRepository.sendTestNotification(
                                    title = "PigeonHub",
                                    message = "Test notification from your device.",
                                )
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    testBusy = false
                                    testResult = if (ok) "전송 완료 — 알림을 확인하세요" else "전송 실패 ($detail)"
                                }
                            }
                        },
                        enabled = !testBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (testBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Test 나에게 보내기")
                    }
                    testResult?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---- AI Agents ----
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.ai_agents_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.ai_agents_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onOpenMyPush) {
                    Text(stringResource(R.string.ai_agents_setup))
                }
            }
        }

        // ---- Custom ----
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.custom_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.custom_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onOpenMyPush) {
                    Text(stringResource(R.string.custom_setup))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
