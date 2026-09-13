package com.pigeonhub.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ForwardToInbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.R
import androidx.compose.ui.res.stringResource
import com.pigeonhub.app.push.FirebaseGate
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.InstallationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-use onboarding (MVP-002A). Driven by the installation state machine —
 * never by a "first launch" flag — so process death at any step resumes with
 * the same client-generated credentials and converges on the same installation.
 *
 * Steps: Purpose → Invite (private beta explained) → Permission (explain first,
 * deny-safe) → Bootstrapping (retry-safe contract) → hands over to the app.
 */
@Composable
fun OnboardingScreen(showMessage: (String) -> Unit) {
    val context = LocalContext.current
    val state by InstallationRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(OnboardingStep.PURPOSE) }
    var inviteCode by remember { mutableStateOf("") }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var fcmToken by remember { mutableStateOf<String?>(null) }
    var fcmChecked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        step = OnboardingStep.BOOTSTRAPPING
        scope.launch {
            val token = fcmToken ?: withContext(Dispatchers.IO) {
                val gate = FirebaseGate.fetchTokenBlocking(context)
                (gate as? FirebaseGate.FcmState.Ready)?.token
            }
            fcmToken = token
            if (token === null) {
                step = OnboardingStep.INVITE
                showMessage(context.getString(R.string.onboarding_needs_play_services))
            } else {
                InstallationRepository.bootstrap(context, inviteCode, token)
            }
        }
    }

    val fetching = state.status == BootstrapStatus.REGISTERING

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            stringResource(R.string.onboarding_app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        when {
            state.status == BootstrapStatus.RECOVERY_REQUIRED -> {
                OnboardingCard {
                    Text(
                        stringResource(R.string.onboarding_recovery_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.onboarding_recovery_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            step == OnboardingStep.PURPOSE -> {
                OnboardingCard {
                    Text(
                        stringResource(R.string.onboarding_purpose_headline),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.onboarding_purpose_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.onboarding_purpose_inbox_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { step = OnboardingStep.INVITE }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_get_started))
                    }
                }
            }

            step == OnboardingStep.INVITE -> {
                OnboardingCard {
                    Text(stringResource(R.string.onboarding_invite_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.onboarding_invite_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.trim() },
                        label = { Text(stringResource(R.string.onboarding_invite_field_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    inviteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (inviteCode.length < 6) {
                                inviteError = context.getString(R.string.onboarding_invite_invalid)
                            } else {
                                inviteError = null
                                step = OnboardingStep.PERMISSION
                            }
                        },
                        enabled = inviteCode.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.onboarding_invite_continue))
                    }
                    Text(
                        stringResource(R.string.onboarding_invite_why_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.onboarding_invite_why_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.onboarding_invite_where_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.onboarding_invite_where_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            step == OnboardingStep.PERMISSION -> {
                OnboardingCard {
                    Text(stringResource(R.string.onboarding_permission_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.onboarding_permission_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                permissionDenied = false
                                step = OnboardingStep.BOOTSTRAPPING
                                scope.launch {
                                    val token = withContext(Dispatchers.IO) {
                                        val gate = FirebaseGate.fetchTokenBlocking(context)
                                        (gate as? FirebaseGate.FcmState.Ready)?.token
                                    }
                                    fcmToken = token
                                    token?.let {
                                        InstallationRepository.bootstrap(context, inviteCode, it)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.onboarding_permission_allow))
                    }
                    TextButton(onClick = {
                        permissionDenied = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            step = OnboardingStep.BOOTSTRAPPING
                            scope.launch {
                                val token = withContext(Dispatchers.IO) {
                                    val gate = FirebaseGate.fetchTokenBlocking(context)
                                    (gate as? FirebaseGate.FcmState.Ready)?.token
                                }
                                fcmToken = token
                                token?.let {
                                    InstallationRepository.bootstrap(context, inviteCode, it)
                                }
                            }
                        }
                    }) {
                        Text(stringResource(R.string.onboarding_permission_skip))
                    }
                    Text(
                        stringResource(R.string.onboarding_permission_skip_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            step == OnboardingStep.BOOTSTRAPPING || fetching -> {
                OnboardingCard {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text(
                            stringResource(
                                if (permissionDenied) {
                                    R.string.onboarding_notifications_off_msg
                                } else {
                                    R.string.onboarding_setting_up
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        state.lastError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { step = OnboardingStep.INVITE }) {
                                Text(stringResource(R.string.common_back))
                            }
                        }
                    }
                }
            }

            else -> {
                // REGISTERED or local-legacy states fall through; the app shell
                // takes over once the status becomes REGISTERED.
                CircularProgressIndicator()
            }
        }
    }
    }
}

private enum class OnboardingStep { PURPOSE, INVITE, PERMISSION, BOOTSTRAPPING }

@Composable
private fun OnboardingCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
