package com.logseq.kmp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.logseq.kmp.platform.PlatformSettings
import com.logseq.kmp.ui.AppState
import com.logseq.kmp.ui.Screen
import com.logseq.kmp.ui.i18n.Language
import com.logseq.kmp.ui.i18n.t
import com.logseq.kmp.ui.theme.LogseqThemeMode

@Composable
fun TopBar(
    appState: AppState,
    platformSettings: PlatformSettings,
    onSettingsClick: () -> Unit,
    onNewPageClick: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onThemeChange: (LogseqThemeMode) -> Unit,
    onLanguageChange: (Language) -> Unit,
    onResetOnboarding: () -> Unit,
    onToggleDebug: () -> Unit,
    onGoBack: () -> Unit = {},
    onGoForward: () -> Unit = {}
) {
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var fileMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("top-bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(onClick = { fileMenuExpanded = true }) {
                Text(t("menu.file"), style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(
                expanded = fileMenuExpanded,
                onDismissRequest = { fileMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(t("menu.switch_graph")) },
                    onClick = {
                        fileMenuExpanded = false
                        onResetOnboarding()
                    }
                )
            }
        }
        Text(t("menu.edit"), modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)

        Box {
            TextButton(onClick = { viewMenuExpanded = true }) {
                Text(t("menu.view"), style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(
                expanded = viewMenuExpanded,
                onDismissRequest = { viewMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Performance Dashboard") },
                    onClick = {
                        onNavigate(Screen.Performance)
                        viewMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (appState.isDebugMode) "Hide Debug Info" else "Show Debug Info") },
                    onClick = {
                        onToggleDebug()
                        viewMenuExpanded = false
                    }
                )
                HorizontalDivider()
                Text(
                    t("settings.language"),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Language.entries.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.label) },
                        leadingIcon = {
                            if (appState.language == lang) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onLanguageChange(lang)
                            viewMenuExpanded = false
                        }
                    )
                }
                HorizontalDivider()
                LogseqThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text("${mode.name.lowercase().replaceFirstChar { it.uppercase() }} ${t("common.theme")}") },
                        leadingIcon = {
                            if (appState.themeMode == mode) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onThemeChange(mode)
                            viewMenuExpanded = false
                        }
                    )
                }
            }
        }

        Text(t("menu.help"), modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)

        Spacer(modifier = Modifier.width(16.dp))

        // Navigation buttons
        IconButton(
            onClick = onGoBack,
            enabled = appState.canGoBack
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go Back",
                modifier = Modifier.size(18.dp),
                tint = if (appState.canGoBack)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
        IconButton(
            onClick = onGoForward,
            enabled = appState.canGoForward
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Go Forward",
                modifier = Modifier.size(18.dp),
                tint = if (appState.canGoForward)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onNewPageClick) {
            Icon(Icons.Default.Add, contentDescription = "New Page", modifier = Modifier.size(18.dp))
        }
        
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = t("common.settings"), modifier = Modifier.size(18.dp))
        }
    }
}
