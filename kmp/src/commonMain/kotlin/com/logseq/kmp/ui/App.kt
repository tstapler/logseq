package com.logseq.kmp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import com.logseq.kmp.ui.components.*
import com.logseq.kmp.ui.theme.LogseqTheme
import com.logseq.kmp.ui.theme.LogseqThemeMode
import com.logseq.kmp.ui.i18n.*
import com.logseq.kmp.ui.onboarding.Onboarding
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.platform.*
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.DatascriptBlockRepository
import com.logseq.kmp.repository.SimplePageRepository
import com.logseq.kmp.repository.InMemorySimplePageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.db.GraphWriter
import kotlinx.datetime.Clock

// In-memory placeholder for UUID since java.util.UUID is not in commonMain
private fun generateUuid(): String {
    val chars = "0123456789abcdef"
    fun randomHex(length: Int) = (1..length).map { chars.random() }.joinToString("")
    return "${randomHex(8)}-${randomHex(4)}-${randomHex(4)}-${randomHex(4)}-${randomHex(12)}"
}

sealed class Screen {
    data object Journals : Screen()
    data object Flashcards : Screen()
    data object AllPages : Screen()
    data object Notifications : Screen()
    data class PageView(val page: Page) : Screen()
}

data class AppState(
    val sidebarExpanded: Boolean = true,
    val rightSidebarExpanded: Boolean = false,
    val settingsVisible: Boolean = false,
    val isLoading: Boolean = false,
    val themeMode: LogseqThemeMode = LogseqThemeMode.SYSTEM,
    val language: Language = Language.ENGLISH,
    val onboardingCompleted: Boolean = false,
    val currentScreen: Screen = Screen.Journals,
    val currentPage: Page? = null,
    val commandPaletteVisible: Boolean = false,
    val commands: List<Command> = emptyList(),
    val statusMessage: String = "Ready"
)

data class Command(
    val id: String,
    val label: String,
    val shortcut: String? = null,
    val action: () -> Unit
)

@Composable
fun LogseqApp(
    fileSystem: PlatformFileSystem,
    graphPath: String,
    pluginHost: PluginHost = remember { PluginHost() },
    encryptionManager: EncryptionManager = remember { DefaultEncryptionManager() }
) {
    var appState by remember { mutableStateOf(AppState(
        isLoading = true,
        commands = emptyList()
    )) }
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var fileMenuExpanded by remember { mutableStateOf(false) }
    var graphExists by remember { mutableStateOf(false) }

    val pageRepository = remember { InMemorySimplePageRepository() }
    val blockRepository = remember { DatascriptBlockRepository() }
    val graphWriter = remember { GraphWriter(fileSystem) }
    val scope = rememberCoroutineScope()
    val notificationManager = remember { NotificationManager(scope) }
    val platformSettings = remember { PlatformSettings() }

    // Reactive Data Sources
    val favoritePages by pageRepository.getFavoritePages()
        .map { it.getOrNull() ?: emptyList() }
        .collectAsState(initial = emptyList())
        
    val recentPages by pageRepository.getRecentPages(10)
        .map { it.getOrNull() ?: emptyList() }
        .collectAsState(initial = emptyList())

    val lastGraphPath = remember {
        platformSettings.getString("lastGraphPath", graphPath)
    }
    var currentGraphPath by remember { mutableStateOf(lastGraphPath) }

    // Update commands with actual actions
    val commands = remember(appState.sidebarExpanded, appState.rightSidebarExpanded, appState.themeMode) {
        listOf(
            Command("toggle-sidebar", "Toggle Left Sidebar", "Ctrl+B") {
                appState = appState.copy(sidebarExpanded = !appState.sidebarExpanded)
            },
            Command("toggle-right-sidebar", "Toggle Right Sidebar", "Ctrl+Shift+B") {
                appState = appState.copy(rightSidebarExpanded = !appState.rightSidebarExpanded)
            },
            Command("settings", "Settings", "Ctrl+,") {
                appState = appState.copy(settingsVisible = true)
            },
            Command("theme-light", "Switch to Light Theme") {
                appState = appState.copy(themeMode = LogseqThemeMode.LIGHT)
            },
            Command("theme-dark", "Switch to Dark Theme") {
                appState = appState.copy(themeMode = LogseqThemeMode.DARK)
            },
            Command("theme-system", "Switch to System Theme") {
                appState = appState.copy(themeMode = LogseqThemeMode.SYSTEM)
            }
        )
    }

    LaunchedEffect(commands) {
        appState = appState.copy(commands = commands)
    }

    LaunchedEffect(currentGraphPath, fileSystem) {
        try {
            appState = appState.copy(isLoading = true, statusMessage = "Loading graph from $currentGraphPath...")
            val onboardingCompleted = platformSettings.getBoolean("onboardingCompleted", false)
            graphExists = fileSystem.directoryExists(currentGraphPath)

            if (!graphExists) {
                appState = appState.copy(statusMessage = "Creating directory $currentGraphPath...")
                val created = fileSystem.createDirectory(currentGraphPath)
                if (created) graphExists = true
            }

            if (graphExists) {
                appState = appState.copy(statusMessage = "Loading pages...")
                pageRepository.clear() // Clear existing to prevent duplicates
                
                val loader = GraphLoader(fileSystem, pageRepository, blockRepository)
                loader.loadGraph(currentGraphPath)
                
                appState = appState.copy(statusMessage = "Graph loaded successfully.")
            } else {
                 appState = appState.copy(statusMessage = "Failed to load graph.")
            }

            appState = appState.copy(
                isLoading = false,
                onboardingCompleted = onboardingCompleted
            )
        } catch (e: Exception) {
            e.printStackTrace()
            appState = appState.copy(
                isLoading = false, 
                statusMessage = "Error: ${e.message}"
            )
        }
    }

    fun refreshCurrentPage() {
        val currentScreen = appState.currentScreen
        if (currentScreen is Screen.PageView) {
            scope.launch {
                pageRepository.getPageByUuid(currentScreen.page.uuid).first().getOrNull()?.let { updatedPage ->
                    appState = appState.copy(currentScreen = Screen.PageView(updatedPage))
                }
            }
        }
    }

    LogseqTheme(themeMode = appState.themeMode) {
        CompositionLocalProvider(LocalI18n provides I18n(appState.language)) {
            if (appState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (!appState.onboardingCompleted) {
                Onboarding(
                    fileSystem = fileSystem,
                    onComplete = {
                        platformSettings.putBoolean("onboardingCompleted", true)
                        appState = appState.copy(onboardingCompleted = true)
                    },
                    onGraphSelected = { path ->
                        currentGraphPath = path
                        platformSettings.putString("lastGraphPath", path)
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                val isMod = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                val isShift = keyEvent.isShiftPressed
                                
                                when {
                                    isMod && isShift && keyEvent.key == Key.P -> {
                                        appState = appState.copy(commandPaletteVisible = true)
                                        true
                                    }
                                    isMod && keyEvent.key == Key.B -> {
                                        if (isShift) {
                                            appState = appState.copy(rightSidebarExpanded = !appState.rightSidebarExpanded)
                                        } else {
                                            appState = appState.copy(sidebarExpanded = !appState.sidebarExpanded)
                                        }
                                        true
                                    }
                                    isMod && keyEvent.key == Key.Comma -> {
                                        appState = appState.copy(settingsVisible = true)
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        // Top Menu Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
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
                                            platformSettings.putBoolean("onboardingCompleted", false)
                                            appState = appState.copy(onboardingCompleted = false)
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
                                                appState = appState.copy(language = lang)
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
                                                appState = appState.copy(themeMode = mode)
                                                viewMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Text(t("menu.help"), modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            
                            IconButton(onClick = { appState = appState.copy(settingsVisible = true) }) {
                                Icon(Icons.Default.Settings, contentDescription = t("common.settings"), modifier = Modifier.size(18.dp))
                            }
                        }

                        // Main Content Area
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LeftSidebar(
                                expanded = appState.sidebarExpanded,
                                isLoading = appState.isLoading,
                                favoritePages = favoritePages,
                                recentPages = recentPages,
                                currentScreen = appState.currentScreen,
                                onPageClick = { page ->
                                    appState = appState.copy(
                                        currentPage = page, 
                                        currentScreen = Screen.PageView(page),
                                        statusMessage = "Opened page: ${page.name}"
                                    )
                                    refreshCurrentPage()
                                },
                                onNavigate = { destination ->
                                    val newScreen = when (destination) {
                                        "journals" -> Screen.Journals
                                        "flashcards" -> Screen.Flashcards
                                        "all-pages" -> Screen.AllPages
                                        "notifications" -> Screen.Notifications
                                        else -> Screen.Journals
                                    }
                                    appState = appState.copy(
                                        currentScreen = newScreen,
                                        currentPage = null,
                                        statusMessage = "Navigating to ${destination.replaceFirstChar { it.uppercase() }}"
                                    )
                                },
                                onToggleFavorite = { page ->
                                    scope.launch {
                                        pageRepository.toggleFavorite(page.uuid)
                                        appState = appState.copy(statusMessage = "Toggled favorite: ${page.name}")
                                    }
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Crossfade(targetState = appState.currentScreen) { screen ->
                                    when (screen) {
                                        is Screen.PageView -> {
                                            val currentPage = screen.page
                                            // Observe blocks for this page
                                            val pageBlocks by blockRepository.getBlocksForPage(currentPage.id)
                                                .map { it.getOrNull() ?: emptyList() }
                                                .collectAsState(initial = emptyList())

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(24.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = currentPage.name,
                                                        style = MaterialTheme.typography.headlineMedium,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                    IconButton(onClick = {
                                                        scope.launch {
                                                            pageRepository.toggleFavorite(currentPage.uuid)
                                                            refreshCurrentPage()
                                                        }
                                                    }) {
                                                        Icon(
                                                            imageVector = if (currentPage.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                            contentDescription = if (currentPage.isFavorite) "Unfavorite" else "Favorite",
                                                            tint = if (currentPage.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "UUID: ${currentPage.uuid}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (currentPage.namespace != null) {
                                                    Text(
                                                        text = "${t("common.namespace")}: ${currentPage.namespace}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                                HorizontalDivider()
                                                Spacer(modifier = Modifier.height(16.dp))
                                                
                                                if (pageBlocks.isEmpty()) {
                                                    Text(
                                                        text = "${t("common.content_placeholder")} ${currentPage.name}.",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    // Render Blocks
                                                    LazyColumn {
                                                        items(pageBlocks, key = { it.uuid }) { block ->
                                                            var text by remember(block.uuid) { mutableStateOf(block.content) }

                                                            LaunchedEffect(block.content) {
                                                                text = block.content
                                                            }

                                                            BasicTextField(
                                                                value = text,
                                                                onValueChange = { newText ->
                                                                    text = newText
                                                                    scope.launch {
                                                                        // 1. Save to Repo
                                                                        val updatedBlock = block.copy(content = newText, updatedAt = Clock.System.now())
                                                                        blockRepository.saveBlock(updatedBlock)
                                                                        
                                                                        // 2. Persist to Disk (Write-Through)
                                                                        // Fetch latest blocks state to ensure we have the full picture
                                                                        val latestBlocksResult = blockRepository.getBlocksForPage(currentPage.id).first()
                                                                        latestBlocksResult.onSuccess { blocks ->
                                                                            graphWriter.savePage(currentPage, blocks, currentGraphPath)
                                                                        }
                                                                    }
                                                                },
                                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                                    color = MaterialTheme.colorScheme.onBackground
                                                                ),
                                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 4.dp)
                                                                    .padding(start = (block.level * 16).dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    is Screen.Journals -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp)
                                        ) {
                                            Text(
                                                text = "Journals",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("Journal entries will appear here.")
                                        }
                                    }
                                    is Screen.Flashcards -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp)
                                        ) {
                                            Text(
                                                text = "Flashcards",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("Flashcards review session.")
                                        }
                                    }
                                        is Screen.AllPages -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(24.dp)
                                            ) {
                                                Text(
                                                    text = "All Pages",
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text("List of all pages.")
                                            }
                                        }
                                        is Screen.Notifications -> {
                                            NotificationHistory(notificationManager)
                                        }
                                    }
                                }
                            }

                            RightSidebar(
                                expanded = appState.rightSidebarExpanded,
                                onClose = { appState = appState.copy(rightSidebarExpanded = false) }
                            )
                        }

                        // Status Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isEncrypted = encryptionManager.isEncryptionEnabled(currentGraphPath)
                            Icon(
                                imageVector = if (isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isEncrypted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEncrypted) t("status.encrypted") else t("status.not_encrypted"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Message Bar
                            Text(
                                text = appState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp).weight(2f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "${pluginHost.getAllPlugins().size} ${t("status.plugins_active")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    CommandPalette(
                        visible = appState.commandPaletteVisible,
                        commands = appState.commands,
                        onDismiss = { appState = appState.copy(commandPaletteVisible = false) }
                    )

                    SettingsDialog(
                        visible = appState.settingsVisible,
                        onDismiss = { appState = appState.copy(settingsVisible = false) },
                        currentTheme = appState.themeMode,
                        onThemeChange = { mode -> appState = appState.copy(themeMode = mode) },
                        currentLanguage = appState.language,
                        onLanguageChange = { lang -> appState = appState.copy(language = lang) }
                    )

                    NotificationOverlay(
                        notificationManager = notificationManager,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}
