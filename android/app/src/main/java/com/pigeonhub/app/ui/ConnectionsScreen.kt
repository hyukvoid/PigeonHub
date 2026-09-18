package com.pigeonhub.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.R
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.HealthApi
import com.pigeonhub.app.push.installation.InstallationRepository
import com.pigeonhub.app.push.installation.PairingApi
import kotlinx.coroutines.launch

/**
 * Connections is a tool directory first. Cards describe what PigeonHub does
 * for a tool; the shared PC job wrapper remains the implementation boundary.
 * No card implies a dedicated connector or remote execution capability.
 */
@Composable
fun ConnectionsScreen(
    onOpenMyPush: () -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val installState by InstallationRepository.state.collectAsState()
    val registered = installState.status == BootstrapStatus.REGISTERED
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val now = rememberTickingNow()
    var health by remember { mutableStateOf<Map<String, HealthApi.ConnectorHealth>>(emptyMap()) }
    var githubConnected by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<ToolSpec?>(null) }
    var pendingPcLogin by remember { mutableStateOf<PcLoginRequest?>(null) }
    var approvingPcLogin by remember { mutableStateOf(false) }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val payload = result.data?.getStringExtra(QrScannerActivity.EXTRA_PAYLOAD)
        val request = payload?.let(::parsePcLoginPayload)
        if (result.resultCode == android.app.Activity.RESULT_OK && request != null) {
            pendingPcLogin = request
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            showSnackbar("This is not a PigeonHub login QR")
        }
    }

    LaunchedEffect(registered) {
        if (registered) {
            health = HealthApi.fetch()
            githubConnected = InstallationRepository.fetchGitHubConnectionStatus().first
        }
    }

    if (pendingPcLogin != null) {
        PcPairingApprovalDialog(
            request = pendingPcLogin!!,
            busy = approvingPcLogin,
            onDismiss = { if (!approvingPcLogin) pendingPcLogin = null },
            onApprove = {
                approvingPcLogin = true
                scope.launch {
                    val request = pendingPcLogin
                    val approved = request != null && PairingApi.approveLoginRequest(request.requestId, request.challenge)
                    approvingPcLogin = false
                    pendingPcLogin = null
                    showSnackbar(if (approved) "PC connected" else "Unable to connect this PC")
                }
            },
        )
    }

    if (selectedTool != null) {
        BackHandler { selectedTool = null }
        ToolDetailScreen(
            tool = selectedTool!!,
            showSnackbar = showSnackbar,
            onBack = { selectedTool = null },
            onAction = {
                if (selectedTool?.id == "comfyui") {
                    scannerLauncher.launch(Intent(context, QrScannerActivity::class.java))
                }
            },
            actionBusy = false,
        )
        return
    }

    val scopeForJump = rememberCoroutineScope()
    val agentsTop = remember { BringIntoViewRequester() }
    val creativeTop = remember { BringIntoViewRequester() }
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
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.connections_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { scopeForJump.launch { agentsTop.bringIntoView() } },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.connections_jump_agents)) }
            FilledTonalButton(
                onClick = { scopeForJump.launch { creativeTop.bringIntoView() } },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.connections_jump_creative)) }
        }

        SectionLabel(stringResource(R.string.connections_my_pc), stringResource(R.string.connections_my_pc_subtitle))
        PcConnectionCard(
            enabled = registered,
            onConnect = { scannerLauncher.launch(Intent(context, QrScannerActivity::class.java)) },
        )
        GitHubConnectionCard(
            connected = githubConnected,
            health = HealthApi.merge(health, "github"),
            now = now,
            onRefresh = { scope.launch { githubConnected = InstallationRepository.fetchGitHubConnectionStatus().first } },
        )
        if (registered) {
            MyPushCard(
                endpoint = installState.endpoint,
                health = HealthApi.merge(health, "device", "push"),
                now = now,
                showSnackbar = showSnackbar,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(agentsTop),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(stringResource(R.string.connections_ai_agents), stringResource(R.string.connections_ai_agents_subtitle))
            AiAgentCards(health = health, now = now) { selectedTool = it }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(creativeTop),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(stringResource(R.string.connections_creative_video), stringResource(R.string.connections_creative_video_subtitle))
            CreativeToolCards(health = health, now = now) { selectedTool = it }
        }

        SectionLabel(stringResource(R.string.connections_advanced), stringResource(R.string.connections_advanced_subtitle))
        val customTool = ToolSpec(
            id = "custom",
            title = stringResource(R.string.custom_title),
            description = stringResource(R.string.custom_tagline),
            status = stringResource(R.string.tool_status_cli_supported),
            action = stringResource(R.string.tool_action_howto),
            icon = Icons.Outlined.Api,
            details = stringResource(R.string.custom_detail),
            command = "pigeonhub run --name \"My task\" -- <your command>",
        )
        ToolCard(customTool, HealthApi.merge(health, "cli"), now) { selectedTool = customTool }
        Spacer(Modifier.height(16.dp))
    }
}

private data class ToolSpec(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val action: String,
    val icon: ImageVector,
    val details: String,
    val command: String? = null,
    val brandIconRes: Int? = null,
)

private data class PcLoginRequest(val requestId: String, val challenge: String)

private fun parsePcLoginPayload(raw: String): PcLoginRequest? {
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    if (uri.scheme != "pigeonhub" || uri.host != "login") return null
    val requestId = uri.getQueryParameter("request_id") ?: return null
    val challenge = uri.getQueryParameter("challenge") ?: return null
    if (requestId.isBlank() || challenge.isBlank()) return null
    return PcLoginRequest(requestId, challenge)
}

@Composable
private fun AiAgentCards(
    health: Map<String, HealthApi.ConnectorHealth>,
    now: Long,
    onSelect: (ToolSpec) -> Unit,
) {
    val specs = listOf(
        ToolSpec("codex", "OpenAI Codex", stringResource(R.string.tool_codex_description), stringResource(R.string.tool_status_supported), stringResource(R.string.tool_action_setup), Icons.Outlined.Code, stringResource(R.string.tool_codex_detail), "pigeonhub setup codex", brandIconRes = R.drawable.ic_agent_codex),
        ToolSpec("claude", "Claude Code", stringResource(R.string.tool_claude_description), stringResource(R.string.tool_status_supported), stringResource(R.string.tool_action_setup), Icons.Outlined.Psychology, stringResource(R.string.tool_claude_detail), "pigeonhub setup claude", brandIconRes = R.drawable.ic_agent_claude),
        ToolSpec("grok", "Grok Build", stringResource(R.string.tool_grok_description), stringResource(R.string.tool_status_supported), stringResource(R.string.tool_action_setup), Icons.Outlined.AutoAwesome, stringResource(R.string.tool_grok_detail), "pigeonhub setup grok", brandIconRes = R.drawable.ic_agent_grok),
        ToolSpec("zcode", "ZCode · GLM", stringResource(R.string.tool_zcode_description), stringResource(R.string.tool_status_supported), stringResource(R.string.tool_action_setup), Icons.Outlined.SmartToy, stringResource(R.string.tool_zcode_detail), "pigeonhub setup zcode", brandIconRes = R.drawable.ic_agent_zcode),
    )
    // MVP-019 copy audit: each agent card reports its OWN source's health.
    // A card whose agent never emitted an event shows no health line at all —
    // never another agent's (or the CLI's) activity.
    specs.forEach { tool -> ToolCard(tool, HealthApi.merge(health, tool.id, "agent"), now) { onSelect(tool) } }
}

@Composable
private fun CreativeToolCards(
    health: Map<String, HealthApi.ConnectorHealth>,
    now: Long,
    onSelect: (ToolSpec) -> Unit,
) {
    val specs = listOf(
        ToolSpec("comfyui", "ComfyUI", stringResource(R.string.tool_comfyui_description), comfyStatus(health), comfyAction(health), Icons.Outlined.AutoAwesome, stringResource(R.string.tool_comfyui_detail)),
        ToolSpec("framepack", "FramePack", stringResource(R.string.tool_framepack_description), stringResource(R.string.tool_status_cli_supported), stringResource(R.string.tool_action_howto), Icons.Outlined.Movie, stringResource(R.string.tool_framepack_detail), "pigeonhub run --name \"FramePack generation\" -- <your command>"),
        ToolSpec("topaz", "Topaz Video AI", stringResource(R.string.tool_topaz_description), stringResource(R.string.tool_status_cli_supported), stringResource(R.string.tool_action_howto), Icons.Outlined.Build, stringResource(R.string.tool_topaz_detail), "pigeonhub run --name \"Topaz Video AI\" -- <your command>"),
        ToolSpec("blender", "Blender", stringResource(R.string.tool_blender_description), stringResource(R.string.tool_status_cli_supported), stringResource(R.string.tool_action_howto), Icons.Outlined.Extension, stringResource(R.string.tool_blender_detail), "pigeonhub run --name \"Blender render\" -- blender -b project.blend -a"),
    )
    specs.forEach { tool -> ToolCard(tool, HealthApi.merge(health, if (tool.id == "comfyui") "comfyui" else "cli"), now) { onSelect(tool) } }
}

@Composable
private fun comfyStatus(health: Map<String, HealthApi.ConnectorHealth>): String = when (HealthApi.merge(health, "comfyui")?.state) {
    HealthApi.State.CONNECTED -> stringResource(R.string.tool_status_connected)
    HealthApi.State.DEGRADED -> stringResource(R.string.health_degraded)
    HealthApi.State.DISCONNECTED -> stringResource(R.string.health_disconnected)
    else -> stringResource(R.string.tool_status_setup_available)
}

@Composable
private fun comfyAction(health: Map<String, HealthApi.ConnectorHealth>): String = when (HealthApi.merge(health, "comfyui")?.state) {
    HealthApi.State.CONNECTED -> stringResource(R.string.tool_action_manage)
    else -> stringResource(R.string.tool_action_connect)
}

@Composable
private fun ToolCard(tool: ToolSpec, health: HealthApi.ConnectorHealth?, now: Long, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(
                icon = {
                    val brand = tool.brandIconRes
                    if (brand != null) {
                        // MVP-019: official brand tile fills the shared rounded
                        // container at its own colors — recognizable at a glance.
                        Image(
                            painterResource(brand),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                },
                title = tool.title,
                tagline = tool.description,
            )
            if (tool.id == "comfyui" || tool.id in setOf("codex", "claude", "grok", "zcode")) HealthLine(health, now)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusPill(tool.status, tool.status == stringResource(R.string.tool_status_connected))
                Spacer(Modifier.weight(1f))
                Text(tool.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (positive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (positive) Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolDetailScreen(tool: ToolSpec, showSnackbar: (String) -> Unit, onBack: () -> Unit, onAction: () -> Unit, actionBusy: Boolean) {
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.common_copied)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButtonLike(stringResource(R.string.common_back), onBack)
        CardHeader(
            icon = {
                val brand = tool.brandIconRes
                if (brand != null) {
                    Image(
                        painterResource(brand),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
            },
            title = tool.title,
            tagline = tool.description,
        )
        StatusPill(tool.status, tool.status == stringResource(R.string.tool_status_connected))
        Text(tool.details, style = MaterialTheme.typography.bodyLarge)
        if (tool.id == "comfyui") {
            Text(stringResource(R.string.comfyui_setup_steps), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction, enabled = !actionBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (actionBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (tool.status == stringResource(R.string.tool_status_connected)) stringResource(R.string.tool_action_manage) else stringResource(R.string.tool_action_connect))
            }
        } else if (tool.command != null) {
            Text(stringResource(R.string.tool_usage_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (tool.id in setOf("codex", "claude", "grok", "zcode")) R.string.tool_agent_usage_steps else R.string.tool_usage_steps),
                style = MaterialTheme.typography.bodyMedium,
            )
            SelectionContainer { Text(tool.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(tool.command)); showSnackbar(copiedMessage) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.tool_copy_command)) }
        }
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹  $text") }
}

@Composable
private fun PcConnectionCard(enabled: Boolean, onConnect: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(
                icon = { Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp)) },
                title = stringResource(R.string.cli_connection_title),
                tagline = stringResource(R.string.cli_connection_tagline),
            )
            Text(stringResource(R.string.cli_connection_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onConnect, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cli_connection_action))
            }
        }
    }
}

@Composable
private fun PcPairingApprovalDialog(
    request: PcLoginRequest,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pairing_scan_title)) },
        text = { Text(stringResource(R.string.pairing_scan_body)) },
        confirmButton = {
            Button(onClick = onApprove, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.pairing_scan_approve))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.pairing_scan_cancel))
            }
        },
    )
}

@Composable
private fun ComfyPairingDialog(code: String, onClose: () -> Unit, showSnackbar: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.common_copied)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.comfyui_pairing_title)) },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { QrCode(payload = code); Text(stringResource(R.string.comfyui_pairing_hint), style = MaterialTheme.typography.bodyMedium); Text(code, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold) } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { clipboard.setText(AnnotatedString(code)); showSnackbar(copiedMessage) }) { Text(stringResource(R.string.common_copy)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun GitHubConnectionCard(connected: Boolean, health: HealthApi.ConnectorHealth?, now: Long, onRefresh: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onRefresh() }
    val open = { launcher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(com.pigeonhub.app.push.installation.GitHubConnect.installUrl()))); Unit }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(icon = { Image(painterResource(R.drawable.ic_github_mark), null, colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer), modifier = Modifier.size(24.dp)) }, title = stringResource(R.string.github_title), tagline = stringResource(R.string.github_tagline))
            StatusPill(if (connected) stringResource(R.string.github_connected) else stringResource(R.string.github_not_connected), connected)
            HealthLine(health, now)
            if (connected) OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.github_manage)) }
            else Button(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.github_connect)) }
        }
    }
}

@Composable
private fun MyPushCard(endpoint: String?, health: HealthApi.ConnectorHealth?, now: Long, showSnackbar: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    var testBusy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val copiedMsg = stringResource(R.string.connections_curl_copied)
    val testDone = stringResource(R.string.connections_test_done)
    val testMessage = stringResource(R.string.connections_test_message)
    val testFailedFmt = stringResource(R.string.connections_test_failed)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(icon = { Icon(Icons.Outlined.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp)) }, title = stringResource(R.string.connections_mypush_title), tagline = stringResource(R.string.connections_mypush_tagline))
            StatusPill(stringResource(R.string.connections_status_connected), true)
            HealthLine(health, now)
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (expanded) stringResource(R.string.connections_hide_advanced) else stringResource(R.string.connections_manage)) }
            if (expanded) {
                Text(stringResource(R.string.connections_advanced_automation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.connections_automation_explanation), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.connections_endpoint_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer { Text(endpoint.orEmpty(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                FilledTonalButton(onClick = { InstallationRepository.buildCurl()?.let { curl -> clipboard.setText(AnnotatedString(curl)); showSnackbar(copiedMsg) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.connections_copy_curl)) }
                Button(onClick = { testBusy = true; testResult = null; scope.launch { val (ok, detail) = InstallationRepository.sendTestNotification("PigeonHub", testMessage); testBusy = false; testResult = if (ok) testDone else String.format(testFailedFmt, detail) } }, enabled = !testBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (testBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.connections_send_test))
                }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Shared icon container: all tool glyphs are optically 24dp inside 40dp. */
@Composable
internal fun CardHeader(icon: @Composable () -> Unit, title: String, tagline: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.clip(RoundedCornerShape(10.dp)).size(40.dp).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (tagline != null) Text(tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
