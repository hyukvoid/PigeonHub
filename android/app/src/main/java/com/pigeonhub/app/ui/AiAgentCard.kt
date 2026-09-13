package com.pigeonhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.pigeonhub.app.R

/**
 * MVP-009: AI Agents connection. Shows the vendor-neutral hook commands to
 * paste into a CLI agent's hook config; NEEDS_ACTION is the headline state
 * (the agent waiting on the user becomes a high-priority alert).
 */
@Composable
fun AiAgentCard(showSnackbar: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.common_copied)

    val startCmd = "python connectors/agent_hook.py agent-start"
    val attentionCmd = "python connectors/agent_hook.py agent-attention"
    val doneCmd = "python connectors/agent_hook.py agent-done"

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(
                icon = {
                    Icon(
                        Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = stringResource(R.string.ai_agents_title),
                tagline = stringResource(R.string.ai_agents_tagline),
            )
            Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connections_howto))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.connections_howto)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.agents_howto_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HookCommandRow(
                        label = stringResource(R.string.agents_copy_start),
                        command = startCmd,
                        onCopy = { clipboard.setText(AnnotatedString(startCmd)); showSnackbar(copiedMsg) },
                    )
                    HookCommandRow(
                        label = stringResource(R.string.agents_copy_attention),
                        command = attentionCmd,
                        onCopy = { clipboard.setText(AnnotatedString(attentionCmd)); showSnackbar(copiedMsg) },
                    )
                    HookCommandRow(
                        label = stringResource(R.string.agents_copy_done),
                        command = doneCmd,
                        onCopy = { clipboard.setText(AnnotatedString(doneCmd)); showSnackbar(copiedMsg) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
}

@Composable
private fun HookCommandRow(label: String, command: String, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            command,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        )
        FilledTonalButton(onClick = onCopy, modifier = Modifier.widthIn(min = 140.dp)) {
            Text(stringResource(R.string.common_copy))
        }
    }
}
