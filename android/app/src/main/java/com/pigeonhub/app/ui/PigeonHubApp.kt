package com.pigeonhub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch

enum class Section(val label: String, val icon: ImageVector) {
    Inbox("Inbox", Icons.Filled.Inbox),
    Device("Device", Icons.Outlined.PhoneAndroid),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
fun PigeonHubApp(tap: TapInfo?) {
    var sectionName by rememberSaveable { mutableStateOf(Section.Inbox.name) }
    val section = Section.valueOf(sectionName)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnackbar: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // A notification tap always lands the user on the inbox.
    LaunchedEffect(tap) {
        if (tap != null) sectionName = Section.Inbox.name
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Section.entries.forEach { candidate ->
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
                Section.Inbox -> HomeScreen(tap = tap, showSnackbar = showSnackbar)
                Section.Device -> DeviceScreen(showSnackbar = showSnackbar)
                Section.Settings -> SettingsScreen(showSnackbar = showSnackbar)
            }
        }
    }
}
