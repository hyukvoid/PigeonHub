package com.pigeonhub.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.R
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.InstallationRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Connections hub (MVP-004 polish): every card answers three questions at a
 * glance — what is it, is it connected, what do I do next. Icons share one
 * optical system (24dp glyph in a 40dp primary-container circle).
 */
@Composable
fun ConnectionsScreen(
    onOpenMyPush: () -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val installState by InstallationRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val registered = installState.status == BootstrapStatus.REGISTERED

    // Real GitHub connection state. The status read doubles as the
    // owner-scoped auto-join: a reinstalled or brand-new device inherits
    // the GitHub connection here, so fan-out grows without any re-connect.
    var githubConnected by remember { mutableStateOf(false) }
    LaunchedEffect(registered) {
        if (registered) {
            githubConnected = InstallationRepository.fetchGitHubConnectionStatus().first
        }
    }

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
        val connectLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Returning from the GitHub page: re-poll; the status read
            // completes the owner-scoped join for this device.
            scope.launch {
                githubConnected = InstallationRepository.fetchGitHubConnectionStatus().first
            }
        }
        val openGitHubPage = {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(com.pigeonhub.app.push.installation.GitHubConnect.installUrl()),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            connectLauncher.launch(intent)
            Unit
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardHeader(
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_github_mark),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    title = stringResource(R.string.github_title),
                    tagline = stringResource(R.string.github_tagline),
                )
                StatusChip(
                    connected = githubConnected,
                    connectedText = stringResource(R.string.github_connected),
                    notConnectedText = stringResource(R.string.github_not_connected),
                )
                if (githubConnected) {
                    // Already connected: the GitHub page is for managing watched
                    // repos — offered as a secondary action, not a confusing
                    // full-width primary "Connect".
                    OutlinedButton(onClick = openGitHubPage, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.github_manage))
                    }
                } else {
                    Button(onClick = openGitHubPage, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.github_connect))
                    }
                }
            }
        }

        // ---- My Push (existing push channel management) ----
        if (registered) {
            MyPushCard(
                endpoint = installState.endpoint,
                showSnackbar = showSnackbar,
            )
        }

        // ---- AI Agents ----
        HowToCard(
            icon = Icons.Outlined.SmartToy,
            title = stringResource(R.string.ai_agents_title),
            tagline = stringResource(R.string.ai_agents_tagline),
            howtoTitle = stringResource(R.string.connections_howto),
            howtoBody = stringResource(R.string.connections_connect_automation_body),
            curlLabel = stringResource(R.string.connections_copy_curl),
            copiedMessage = stringResource(R.string.connections_curl_copied),
            showSnackbar = showSnackbar,
        )

        // ---- ComfyUI (MVP-007 POC) ----
        ComfyUiCard(showSnackbar = showSnackbar)

        // ---- Custom ----
        HowToCard(
            icon = Icons.Outlined.Api,
            title = stringResource(R.string.custom_title),
            tagline = stringResource(R.string.custom_tagline),
            howtoTitle = stringResource(R.string.connections_howto),
            howtoBody = stringResource(R.string.connections_connect_automation_body),
            curlLabel = stringResource(R.string.connections_copy_curl),
            copiedMessage = stringResource(R.string.connections_curl_copied),
            showSnackbar = showSnackbar,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MyPushCard(endpoint: String?, showSnackbar: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.connections_curl_copied)
    val testDone = stringResource(R.string.connections_test_done)
    val testMessage = stringResource(R.string.connections_test_message)
    val testFailedFmt = stringResource(R.string.connections_test_failed)

    var testBusy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(
                icon = {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = stringResource(R.string.connections_mypush_title),
                tagline = null,
            )
            StatusChip(
                connected = true,
                connectedText = stringResource(R.string.connections_status_connected),
                notConnectedText = null,
            )
            Text(
                stringResource(R.string.connections_endpoint_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    endpoint ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val curl = InstallationRepository.buildCurl()
                    if (curl != null) {
                        clipboard.setText(AnnotatedString(curl))
                        showSnackbar(copiedMsg)
                    }
                }) {
                    Text(stringResource(R.string.connections_copy_curl))
                }
            }

            // Real Worker → D1 → FCM → Android test notification
            Button(
                onClick = {
                    testBusy = true
                    testResult = null
                    scope.launch {
                        val (ok, detail) = InstallationRepository.sendTestNotification(
                            title = "PigeonHub",
                            message = testMessage,
                        )
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            testBusy = false
                            testResult = if (ok) testDone else String.format(testFailedFmt, detail)
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
                Text(stringResource(R.string.connections_send_test))
            }
            testResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Card header: 24dp glyph in a 40dp primary-container circle + title (+ tagline). */
@Composable
internal fun CardHeader(
    icon: @Composable () -> Unit,
    title: String,
    tagline: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (tagline != null) {
                Text(
                    tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    connected: Boolean,
    connectedText: String,
    notConnectedText: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            if (connected) connectedText else notConnectedText.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Informational card whose "How to connect" section expands in place —
 * the old design had a "Set up" button that only navigated to this same
 * tab (a dead end).
 */
@Composable
private fun HowToCard(
    icon: ImageVector,
    title: String,
    tagline: String,
    howtoTitle: String,
    howtoBody: String,
    curlLabel: String,
    copiedMessage: String,
    showSnackbar: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(
                icon = {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = title,
                tagline = tagline,
            )
            FilledTonalButton(onClick = { expanded = !expanded }) {
                Text(howtoTitle)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        howtoBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = {
                        val curl = InstallationRepository.buildCurl()
                        if (curl != null) {
                            clipboard.setText(AnnotatedString(curl))
                            showSnackbar(copiedMessage)
                        }
                    }) {
                        Text(curlLabel)
                    }
                }
            }
        }
    }
}
