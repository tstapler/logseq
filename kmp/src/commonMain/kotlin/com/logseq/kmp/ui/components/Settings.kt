package com.logseq.kmp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logseq.kmp.ui.theme.LogseqThemeMode
import com.logseq.kmp.ui.i18n.Language

@Composable
fun SettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    currentTheme: LogseqThemeMode,
    onThemeChange: (LogseqThemeMode) -> Unit,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
    onReindex: () -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.8f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }

                Row(modifier = Modifier.fillMaxSize()) {
                    // Sidebar
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        SettingsCategory.entries.forEach { category ->
                            CategoryItem(
                                category = category,
                                isSelected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }

                    // Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCategory.label,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (selectedCategory) {
                                SettingsCategory.GENERAL -> GeneralSettings(
                                    currentTheme = currentTheme,
                                    onThemeChange = onThemeChange,
                                    currentLanguage = currentLanguage,
                                    onLanguageChange = onLanguageChange
                                )
                                SettingsCategory.EDITOR -> EditorSettings()
                                SettingsCategory.PLUGINS -> PluginsSettings()
                                SettingsCategory.ADVANCED -> AdvancedSettings(onReindex)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GeneralSettings(
    currentTheme: LogseqThemeMode,
    onThemeChange: (LogseqThemeMode) -> Unit,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit
) {
    SettingsSection("Appearance") {
        SettingsRow("Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogseqThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = currentTheme == mode,
                        onClick = { onThemeChange(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
    }
    
    SettingsSection("Localization") {
        SettingsRow("Language") {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(currentLanguage.name.lowercase().replaceFirstChar { it.uppercase() })
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    Language.entries.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                onLanguageChange(language)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditorSettings() {
    SettingsSection("Editor Behavior") {
        SettingsToggleRow("Logical Outdenting", true) {}
        SettingsToggleRow("Wide Mode", false) {}
        SettingsToggleRow("Show Brackets", true) {}
    }
}

@Composable
fun PluginsSettings() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No plugins installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AdvancedSettings(onReindex: () -> Unit) {
    SettingsSection("Danger Zone") {
        Text(
            "If your graph data seems inconsistent or missing, you can force a full re-index of your files into the database.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = onReindex,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Re-index Graph")
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsRow(label) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

enum class SettingsCategory(val label: String, val icon: ImageVector) {
    GENERAL("General", Icons.Default.Settings),
    EDITOR("Editor", Icons.Default.Edit),
    PLUGINS("Plugins", Icons.Default.Extension),
    ADVANCED("Advanced", Icons.Default.Build)
}
