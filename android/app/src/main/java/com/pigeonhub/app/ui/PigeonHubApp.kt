package com.pigeonhub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.Cable
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
import androidx.compose.ui.res.stringResource
import com.pigeonhub.app.BuildConfig
import com.pigeonhub.app.R
import com.pigeonhub.app.push.installation.BootstrapStatus
import com.pigeonhub.app.push.installation.InstallationRepository
import kotlinx.coroutines.launch

enum class Section(val labelRes: Int, val icon: ImageVector) {
    Inbox(R.string.nav_inbox, Icons.Filled.Inbox),
    Connections(R.string.nav_connections, Icons.Outlined.Cable),
    Device(R.string.nav_connections, Icons.Outlined.PhoneAndroid),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
}

private val RELEASE_SECTIONS = listOf(Section.Inbox, Section.Connections, Section.Settings)
private val DEBUG_SECTIONS = listOf(Section.Inbox, Section.Connections, Section.Device, Section.Settings)

@Composable
fun PigeonHubApp(tap: TapInfo?) {
    val installState by InstallationRepository.state.collectAsState()
    val registered = installState.status == BootstrapStatus.REGISTERED
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    if (!registered) {
        OnboardingScreen(showMessage = showMessage)
        return
    }

    var sectionName by rememberSaveable { mutableStateOf(Section.Inbox.name) }
    val visibleSections: List<Section> =
        if (BuildConfig.DEBUG) DEBUG_SECTIONS else RELEASE_SECTIONS

    val section = runCatching { Section.valueOf(sectionName) }.getOrDefault(Section.Inbox)
        .let { chosen -> if (visibleSections.contains(chosen)) chosen else visibleSections.first() }

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
                        icon = { Icon(candidate.icon, contentDescription = stringResource(candidate.labelRes)) },
                        label = { Text(stringResource(candidate.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (section) {
                Section.Inbox -> HomeScreen(
                    tap = tap,
                    onOpenMyPush = { sectionName = Section.Connections.name },
                    showSnackbar = showMessage,
                )
                Section.Connections -> ConnectionsScreen(
                    onOpenMyPush = { sectionName = Section.Connections.name },
                    showSnackbar = showMessage,
                )
                Section.Settings -> SettingsScreen(showSnackbar = showMessage)
                Section.Device -> if (BuildConfig.DEBUG) DeviceScreen(showSnackbar = showMessage)
                else -> {}
            }
        }
    }
}
