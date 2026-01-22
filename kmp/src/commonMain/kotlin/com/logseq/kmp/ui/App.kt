package com.logseq.kmp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.logseq.kmp.db.GraphWriter
import com.logseq.kmp.logging.Logger
import com.logseq.kmp.model.Page
import com.logseq.kmp.platform.*
import com.logseq.kmp.repository.DatascriptBlockRepository
import com.logseq.kmp.repository.InMemorySimplePageRepository
import com.logseq.kmp.repository.InMemorySearchRepository
import com.logseq.kmp.ui.components.*
import com.logseq.kmp.ui.i18n.I18n
import com.logseq.kmp.ui.i18n.LocalI18n
import com.logseq.kmp.ui.i18n.t
import com.logseq.kmp.ui.onboarding.Onboarding
import com.logseq.kmp.ui.screens.PageView
import com.logseq.kmp.ui.screens.JournalsView
import com.logseq.kmp.ui.theme.LogseqTheme

@Composable
fun LogseqApp(
    fileSystem: PlatformFileSystem,
    graphPath: String,
    pluginHost: PluginHost = remember { PluginHost() },
    encryptionManager: EncryptionManager = remember { DefaultEncryptionManager() }
) {
    val platformSettings = remember { PlatformSettings() }
    val pageRepository = remember { InMemorySimplePageRepository() }
    val blockRepository = remember { DatascriptBlockRepository() }
    val searchRepository = remember { InMemorySearchRepository(pageRepository, blockRepository) }
    val graphWriter = remember { GraphWriter(fileSystem) }
    val scope = rememberCoroutineScope()
    val notificationManager = remember { NotificationManager(scope) }
    
    val viewModel = remember {
        LogseqViewModel(
            fileSystem,
            pageRepository,
            blockRepository,
            graphWriter,
            platformSettings,
            scope
        ).also {
            it.startAutoSave()
        }
    }
    
    // Create EditorViewModel
    val editorViewModel = remember {
        com.logseq.kmp.ui.editor.EditorViewModel(
            blockRepository,
            scope
        )
    }

    // Create JournalsViewModel
    val journalsViewModel = remember {
        com.logseq.kmp.ui.screens.JournalsViewModel(
            pageRepository,
            scope
        )
    }

    // Create SearchViewModel
    val searchViewModel = remember {
        com.logseq.kmp.ui.screens.SearchViewModel(
            searchRepository,
            scope
        )
    }

    val appState by viewModel.uiState.collectAsState()
    
    // Graph Path and Page lists are now managed by ViewModel and AppState

    LogseqTheme(themeMode = appState.themeMode) {
        CompositionLocalProvider(LocalI18n provides I18n(appState.language)) {
            if (!appState.onboardingCompleted) {
                Onboarding(
                    fileSystem = fileSystem,
                    onComplete = {
                        viewModel.setOnboardingCompleted(true)
                    },
                    onGraphSelected = { path ->
                        viewModel.setGraphPath(path)
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
                                        viewModel.setCommandPaletteVisible(true)
                                        true
                                    }
                                    isMod && keyEvent.key == Key.K -> {
                                        viewModel.setSearchDialogVisible(true)
                                        true
                                    }
                                    isMod && keyEvent.key == Key.B -> {
                                        if (isShift) {
                                            viewModel.toggleRightSidebar()
                                        } else {
                                            viewModel.toggleSidebar()
                                        }
                                        true
                                    }
                                    isMod && keyEvent.key == Key.Comma -> {
                                        viewModel.setSettingsVisible(true)
                                        true
                                    }
                                    // Navigation: Alt+Left or Cmd+[ for back
                                    (keyEvent.isAltPressed && keyEvent.key == Key.DirectionLeft) ||
                                    (isMod && keyEvent.key == Key.LeftBracket) -> {
                                        viewModel.goBack()
                                        true
                                    }
                                    // Navigation: Alt+Right or Cmd+] for forward
                                    (keyEvent.isAltPressed && keyEvent.key == Key.DirectionRight) ||
                                    (isMod && keyEvent.key == Key.RightBracket) -> {
                                        viewModel.goForward()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    MainLayout(
                        topBar = {
                            TopBar(
                                appState = appState,
                                platformSettings = platformSettings,
                                onSettingsClick = { viewModel.setSettingsVisible(true) },
                                onNavigate = { viewModel.navigateTo(it) },
                                onThemeChange = { viewModel.setThemeMode(it) },
                                onLanguageChange = { language -> viewModel.setLanguage(language) },
                                onResetOnboarding = { viewModel.setOnboardingCompleted(false) }
                            )
                        },
                        leftSidebar = {
                            LeftSidebar(
                                expanded = appState.sidebarExpanded,
                                isLoading = appState.isLoading,
                                favoritePages = appState.favoritePages,
                                recentPages = appState.recentPages,
                                currentScreen = appState.currentScreen,
                                onPageClick = { page ->
                                    viewModel.navigateTo(Screen.PageView(page))
                                },
                                onNavigate = { destination ->
                                    viewModel.navigateTo(destination)
                                },
                                onToggleFavorite = { page ->
                                    viewModel.toggleFavorite(page)
                                }
                            )
                        },
                        rightSidebar = {
                            RightSidebar(
                                expanded = appState.rightSidebarExpanded,
                                onClose = { viewModel.toggleRightSidebar() }
                            )
                        },
                        content = {
                            Crossfade(targetState = appState.currentScreen) { screen ->
                                when (screen) {
                                    is Screen.PageView -> {
                                        PageView(
                                            page = screen.page,
                                            blockRepository = blockRepository,
                                            pageRepository = pageRepository,
                                            graphWriter = graphWriter,
                                            currentGraphPath = appState.currentGraphPath,
                                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                                            onRefresh = { viewModel.refreshCurrentPage() },
                                            viewModel = viewModel,
                                            editorViewModel = editorViewModel
                                        )
                                    }
                                    is Screen.Journals -> {
                                        JournalsView(
                                            viewModel = journalsViewModel,
                                            blockRepository = blockRepository,
                                            onLinkClick = { pageName ->
                                                viewModel.navigateToPageByName(pageName)
                                            },
                                            onContentChange = { blockId, newContent, page ->
                                                viewModel.saveBlockContent(blockId, newContent, page)
                                            }
                                        )
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
                                            
                                            LazyColumn {
                                                items(
                                                    items = appState.regularPages,
                                                    key = { page: Page -> page.id }
                                                ) { page: Page ->
                                                    ListItem(
                                                        headlineContent = { Text(page.name) },
                                                        modifier = Modifier.clickable {
                                                            viewModel.navigateTo(Screen.PageView(page))
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    is Screen.Notifications -> {
                                        NotificationHistory(notificationManager)
                                    }
                                    is Screen.Logs -> {
                                        LogDashboard()
                                    }
                                    is Screen.Performance -> {
                                        PerformanceDashboard()
                                    }
                                }
                            }
                        },
                        statusBar = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isEncrypted = encryptionManager.isEncryptionEnabled(appState.currentGraphPath)
                                Icon(
                                    imageVector = if (isEncrypted) Icons.Default.Lock else Icons.Default.Info, // Fallback icon
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
                    )

                    CommandPalette(
                        visible = appState.commandPaletteVisible,
                        commands = appState.commands,
                        onDismiss = { viewModel.setCommandPaletteVisible(false) }
                    )
                    
                    SearchDialog(
                        visible = appState.searchDialogVisible,
                        viewModel = searchViewModel,
                        onDismiss = { viewModel.setSearchDialogVisible(false) },
                        onNavigateToPage = { uuid ->
                            viewModel.navigateToPageByUuid(uuid)
                        },
                        onNavigateToBlock = { uuid ->
                            viewModel.navigateToBlock(uuid)
                        }
                    )

                    SettingsDialog(
                        visible = appState.settingsVisible,
                        onDismiss = { viewModel.setSettingsVisible(false) },
                        currentTheme = appState.themeMode,
                        onThemeChange = { viewModel.setThemeMode(it) },
                        currentLanguage = appState.language,
                        onLanguageChange = { viewModel.setLanguage(it) }
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
