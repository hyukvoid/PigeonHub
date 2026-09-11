package com.pigeonhub.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                showMessage("Setup needs Google Play services — please try again")
            } else {
                InstallationRepository.bootstrap(context, inviteCode, token)
            }
        }
    }

    val fetching = state.status == BootstrapStatus.REGISTERING

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            "PigeonHub",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        when {
            state.status == BootstrapStatus.RECOVERY_REQUIRED -> {
                OnboardingCard {
                    Text(
                        "Recovery required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "The encryption key protecting this device's PigeonHub " +
                            "credentials is no longer available, so they cannot be " +
                            "recovered. PigeonHub will not overwrite the existing " +
                            "installation — reinstall the app and use a new invite code.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            step == OnboardingStep.PURPOSE -> {
                OnboardingCard {
                    Text(
                        "When a task finishes on your PC,\nthis phone tells you.",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Point your scripts, CI, or automations at PigeonHub and the " +
                            "result lands on this phone as a notification.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Important results stay in your Inbox, so a notification " +
                            "you missed is never lost.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { step = OnboardingStep.INVITE }, modifier = Modifier.fillMaxWidth()) {
                        Text("Get started")
                    }
                }
            }

            step == OnboardingStep.INVITE -> {
                OnboardingCard {
                    Text("Private beta", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "PigeonHub is currently available to invited users only.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.trim() },
                        label = { Text("Invite code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    inviteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (inviteCode.length < 6) {
                                inviteError = "That invite code doesn't look right."
                            } else {
                                inviteError = null
                                step = OnboardingStep.PERMISSION
                            }
                        },
                        enabled = inviteCode.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continue")
                    }
                    Text(
                        "Why do I need a code?",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Every device creates its own private push channel. During the " +
                            "private beta, a single-use invite keeps that channel yours " +
                            "and keeps public spam out of the service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Where can I get one?",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Invites are distributed by the PigeonHub maintainer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            step == OnboardingStep.PERMISSION -> {
                OnboardingCard {
                    Text("Allow notifications", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "PigeonHub shows a notification the moment one of your tasks " +
                            "finishes. Android requires your permission for this.",
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
                        Text("Allow notifications")
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
                        Text("Not now")
                    }
                    Text(
                        "You can skip this. Messages still appear in your Inbox; " +
                            "only the popup is affected.",
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
                            if (permissionDenied) {
                                "Notifications are off.\nMessages will still appear in your Inbox."
                            } else {
                                "Setting up PigeonHub..."
                            },
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
                                Text("Back")
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
        Spacer(Modifier.height(24.dp))
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
