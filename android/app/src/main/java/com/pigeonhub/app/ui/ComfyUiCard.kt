package com.pigeonhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.R
import com.pigeonhub.app.push.installation.HealthApi
import com.pigeonhub.app.push.installation.PairingApi
import kotlinx.coroutines.launch

/**
 * MVP-007 POC: ComfyUI connector card. The phone issues a short-lived
 * one-time pairing code; the PC-side connector redeems it. No endpoint,
 * bearer token, channel id or webhook is ever shown to the user.
 */
@Composable
fun ComfyUiCard(
    showSnackbar: (String) -> Unit,
    health: HealthApi.ConnectorHealth? = null,
    now: Long = 0L,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf(false) }
    var issuedCode by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val failedMsg = stringResource(R.string.comfyui_pairing_failed)
    val revokedMsg = stringResource(R.string.comfyui_pairing_revoked)
    val copiedMsg = stringResource(R.string.common_copied)

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(
                icon = {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = stringResource(R.string.comfyui_title),
                tagline = stringResource(R.string.comfyui_tagline),
            )
            HealthLine(health, now)
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val issued = PairingApi.issueCode()
                        busy = false
                        if (issued == null) {
                            showSnackbar(failedMsg)
                        } else {
                            issuedCode = issued.code
                            showDialog = true
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.comfyui_connect))
                }
            }
        }
    }

    if (showDialog && issuedCode != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.comfyui_pairing_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QrCode(payload = issuedCode!!)
                    Text(
                        stringResource(R.string.comfyui_pairing_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        issuedCode!!,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(issuedCode!!))
                    showSnackbar(copiedMsg)
                }) { Text(stringResource(R.string.common_copy)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showDialog = false
                        scope.launch {
                            PairingApi.revokeCodes()
                            showSnackbar(revokedMsg)
                        }
                    }) { Text(stringResource(R.string.comfyui_pairing_revoke)) }
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            },
        )
    }
}
