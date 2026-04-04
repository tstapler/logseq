package com.logseq.kmp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.logseq.kmp.db.GraphManager
import com.logseq.kmp.db.GraphWriter
import com.logseq.kmp.logging.Logger
import com.logseq.kmp.model.Page
import com.logseq.kmp.platform.*
import com.logseq.kmp.db.DriverFactory
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.repository.*
import com.logseq.kmp.ui.components.*
import com.logseq.kmp.ui.i18n.I18n
import com.logseq.kmp.ui.i18n.LocalI18n
import com.logseq.kmp.ui.i18n.t
import com.logseq.kmp.ui.onboarding.Onboarding
import com.logseq.kmp.ui.screens.PageView
import com.logseq.kmp.ui.screens.JournalsView
import com.logseq.kmp.ui.theme.LogseqTheme
import com.logseq.kmp.ui.theme.LogseqThemeMode

/**
 * Root Composable for the Logseq application.
 * Updated to use multi-graph support with GraphManager.
 */
@Composable
fun LogseqApp(
    fileSystem: PlatformFileSystem,
    graphPath: String,
    pluginHost: PluginHost = remember { PluginHost() },
    encryptionManager: EncryptionManager = remember { DefaultEncryptionManager() },
    onViewModelCreated: (LogseqViewModel) -> Unit = {}
) {
    val platformSettings = remember { PlatformSettings() }
    val scope = rememberCoroutineScope()
    
    // Create GraphManager - this owns all graph lifecycle
    val graphManager = remember { 
        GraphManager(platformSettings, DriverFactory(), fileSystem, scope) 
    }
    
    // Observe the active repository set
    val activeRepoSet by graphManager.activeRepositorySet.collectAsState()
    val graphRegistry by graphManager.graphRegistry.collectAsState()
    val activeGraphId = graphRegistry.activeGraphId
    val activeGraphInfo = graphManager.getActiveGraphInfo()
    
    // Initialize with graph path if provided and no active graph
    LaunchedEffect(graphPath) {
        if (graphPath.isNotEmpty() && activeGraphId == null) {
            val graphId = graphManager.addGraph(graphPath)
            graphManager.switchGraph(graphId)
        } else if (activeGraphId != null) {
            // Re-switch to ensure repositories are initialized
            graphManager.switchGraph(activeGraphId)
        }
    }
    
    val notificationManager = remember { NotificationManager(scope) }
    
    // Main content - use when() to handle loading state
    val repos = activeRepoSet
    
    if (repos == null) {
        // Show loading state while repositories are being initialized
        LogseqTheme(themeMode = LogseqThemeMode.SYSTEM) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }
    
    // Use key(graphId) to recreate ViewModels when graph changes
    key(activeGraphId) {
        GraphContent(
            repos = repos,
            fileSystem = fileSystem,
            platformSettings = platformSettings,
            pluginHost = pluginHost,
            encryptionManager = encryptionManager,
            graphManager = graphManager,
            notificationManager = notificationManager,
            onViewModelCreated = onViewModelCreated
        )
    }
}

/**
 * Inner composable for graph-specific content.
 * Recreated when the active graph changes.
 */
@Composable
private fun GraphContent(
    repos: RepositorySet,
    fileSystem: PlatformFileSystem,
    platformSettings: PlatformSettings,
    pluginHost: PluginHost,
    encryptionManager: EncryptionManager,
    graphManager: GraphManager,
    notificationManager: NotificationManager,
    onViewModelCreated: (LogseqViewModel) -> Unit
) {
    val scope = rememberCoroutineScope()
    val graphWriter = remember { GraphWriter(fileSystem, repos.pageRepository) }
    val graphLoader = remember { com.logseq.kmp.db.GraphLoader(fileSystem, repos.pageRepository, repos.blockRepository) }
    
    val viewModel = remember {
        LogseqViewModel(
            fileSystem,
            repos.pageRepository,
            repos.blockRepository,
            repos.searchRepository,
            graphLoader,
            graphWriter,
            platformSettings,
            scope
        ).also {
            it.startAutoSave()
            onViewModelCreated(it)
        }
    }
    
    // Lifecycle observer for Android/Mobile to force save on pause
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                scope.launch { viewModel.savePendingChanges() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.launch { viewModel.savePendingChanges() }
        }
    }
    
    // Create JournalsViewModel
    val journalsViewModel = remember {
        com.logseq.kmp.ui.screens.JournalsViewModel(
            repos.pageRepository,
            repos.blockRepository,
            graphLoader,
            scope
        )
    }

    // Create SearchViewModel
    val searchViewModel = remember {
        com.logseq.kmp.ui.screens.SearchViewModel(
            repos.searchRepository,
            scope
        )
    }

    val appState by viewModel.uiState.collectAsState()
    
    // Get graph info
    val graphRegistry by graphManager.graphRegistry.collectAsState()
    val activeGraphInfo = graphManager.getActiveGraphInfo()
    
    LogseqTheme(themeMode = appState.themeMode) {
        CompositionLocalProvider(LocalI18n provides I18n(appState.language)) {
            if (!appState.onboardingCompleted) {
                Onboarding(
                    fileSystem = fileSystem,
                    onComplete = {
                        viewModel.setOnboardingCompleted(true)
                    },
                    onGraphSelected = { path ->
                        val graphId = graphManager.addGraph(path)
                        graphManager.switchGraph(graphId)
                        viewModel.setGraphPath(path)
                    }
                )
            } else {
                val focusManager = LocalFocusManager.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                            })
                        }
                        .platformNavigationInput(
                            onBack = { viewModel.goBack() },
                            onForward = { viewModel.goForward() }
                        )
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
                                    isMod && !isShift && keyEvent.key == Key.Z -> {
                                        journalsViewModel.undo()
                                        true
                                    }
                                    (isMod && isShift && keyEvent.key == Key.Z) ||
                                    (isMod && keyEvent.key == Key.Y) -> {
                                        journalsViewModel.redo()
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
                                onNewPageClick = { viewModel.setSearchDialogVisible(true) },
                                onNavigate = { viewModel.navigateTo(it) },
                                onThemeChange = { viewModel.setThemeMode(it) },
                                onLanguageChange = { language -> viewModel.setLanguage(language) },
                                onResetOnboarding = { viewModel.setOnboardingCompleted(false) },
                                onToggleDebug = { viewModel.toggleDebugMode() },
                                onGoBack = { viewModel.goBack() },
                                onGoForward = { viewModel.goForward() }
                            )
                        },
                        leftSidebar = {
                            LeftSidebar(
                                expanded = appState.sidebarExpanded,
                                isLoading = appState.isLoading,
                                favoritePages = appState.favoritePages,
                                recentPages = appState.recentPages,
                                currentScreen = appState.currentScreen,
                                currentGraphName = activeGraphInfo?.displayName ?: "",
                                availableGraphs = graphRegistry.graphs,
                                onPageClick = { page ->
                                    viewModel.navigateTo(Screen.PageView(page))
                                },
                                onNavigate = { destination ->
                                    viewModel.navigateTo(destination)
                                },
                                onToggleFavorite = { page ->
                                    viewModel.toggleFavorite(page)
                                },
                                onGraphSelected = { graphId ->
                                    graphManager.switchGraph(graphId)
                                },
                                onAddGraph = {
                                    val selectedPath = fileSystem.pickDirectory()
                                    if (selectedPath != null) {
                                        val newGraphId = graphManager.addGraph(selectedPath)
                                        graphManager.switchGraph(newGraphId)
                                    }
                                },
                                onRemoveGraph = { graphId ->
                                    graphManager.removeGraph(graphId)
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
                                            blockRepository = repos.blockRepository,
                                            pageRepository = repos.pageRepository,
                                            graphWriter = graphWriter,
                                            graphLoader = graphLoader,
                                            currentGraphPath = appState.currentGraphPath,
                                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                                            onRefresh = { viewModel.refreshCurrentPage() },
                                            onLinkClick = { pageName -> viewModel.navigateToPageByName(pageName) },
                                            viewModel = viewModel,
                                            isDebugMode = appState.isDebugMode
                                        )
                                    }
                                    is Screen.Journals -> {
                                        JournalsView(
                                            viewModel = journalsViewModel,
                                            blockRepository = repos.blockRepository,
                                            isDebugMode = appState.isDebugMode,
                                            onLinkClick = { pageName ->
                                                viewModel.navigateToPageByName(pageName)
                                            },
                                            onContentChange = { blockUuid, newContent, version, page ->
                                                viewModel.saveBlockContent(blockUuid, newContent, version, page)
                                            },
                                            onSearchPages = { query -> viewModel.searchPages(query) }
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
                                                    key = { page: Page -> page.uuid }
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
                                    imageVector = if (isEncrypted) Icons.Default.Lock else Icons.Default.Info,
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
                                
                                // Active Graph Name
                                Text(
                                    text = activeGraphInfo?.displayName ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                
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
                        },
                        onCreatePage = { name ->
                            viewModel.navigateToPageByName(name)
                        }
                    )

                    SettingsDialog(
                        visible = appState.settingsVisible,
                        onDismiss = { viewModel.setSettingsVisible(false) },
                        currentTheme = appState.themeMode,
                        onThemeChange = { viewModel.setThemeMode(it) },
                        currentLanguage = appState.language,
                        onLanguageChange = { language -> viewModel.setLanguage(language) },
                        onReindex = {
                            viewModel.triggerReindex()
                            viewModel.setSettingsVisible(false)
                        }
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
