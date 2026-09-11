package com.pigeonhub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.pigeonhub.app.BuildConfig
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.InstallationRepository
import kotlinx.coroutines.launch

enum class Section(val label: String, val icon: ImageVector) {
    Inbox("Inbox", Icons.Filled.Inbox),
    MyPush("My Push", Icons.Outlined.Notifications),
    Device("Device", Icons.Outlined.PhoneAndroid),
    Settings("Settings", Icons.Outlined.Settings),
}

// Debug-only section: kept out of the release navigation entirely.
private val DEBUG_SECTIONS = listOf(
    Section.Inbox,
    Section.MyPush,
    Section.Device,
    Section.Settings,
)

private val RELEASE_SECTIONS = listOf(Section.Inbox, Section.MyPush, Section.Settings)

@Composable
fun PigeonHubApp(tap: TapInfo?) {
    val context = LocalContext.current
    val installState by InstallationRepository.state.collectAsState()
    val registered = installState.status == BootstrapStatus.REGISTERED
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    if (!registered) {
        // First-use onboarding owns the whole screen until the installation is READY.
        OnboardingScreen(showMessage = showMessage)
        return
    }

    var sectionName by rememberSaveable { mutableStateOf(Section.Inbox.name) }
    val visibleSections: List<Section> =
        if (BuildConfig.DEBUG) DEBUG_SECTIONS else RELEASE_SECTIONS

    val section = runCatching { Section.valueOf(sectionName) }.getOrDefault(Section.Inbox)
        .let { chosen -> if (visibleSections.contains(chosen)) chosen else visibleSections.first() }

    // A notification tap always lands the user on the inbox.
    LaunchedEffect(tap) {
        if (tap != null) sectionName = Section.Inbox.name
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                visibleSections.forEach { candidate ->
                    NavigationBarItem(
                        selected = candidate == section,
                        onClick = { sectionName = candidate.name },
                        icon = { Icon(candidate.icon, contentDescription = candidate.label) },
                        label = { Text(candidate.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (section) {
                Section.Inbox -> HomeScreen(
                    tap = tap,
                    onOpenMyPush = { sectionName = Section.MyPush.name },
                    showSnackbar = showMessage,
                )
                Section.MyPush -> MyPushScreen(showSnackbar = showMessage)
                Section.Settings -> SettingsScreen(showSnackbar = showMessage)
                Section.Device -> if (BuildConfig.DEBUG) DeviceScreen(showSnackbar = showMessage)
            }
        }
    }
    // context kept for future use in this scope
    @Suppress("UNUSED_EXPRESSION")
    context
}
