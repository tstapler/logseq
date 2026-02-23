package com.logseq.kmp.ui

import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.db.GraphWriter
import com.logseq.kmp.logging.Logger
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.platform.PlatformFileSystem
import com.logseq.kmp.platform.PlatformSettings
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.SearchRepository
import com.logseq.kmp.repository.SearchRequest
import com.logseq.kmp.repository.SimplePageRepository
import com.logseq.kmp.ui.i18n.Language
import com.logseq.kmp.ui.theme.LogseqThemeMode
import com.logseq.kmp.editor.commands.CommandContext
import com.logseq.kmp.editor.commands.CommandManager
import com.logseq.kmp.editor.commands.EditorCommand
import com.logseq.kmp.editor.commands.CommandResult
import com.logseq.kmp.performance.DebounceManager
import com.logseq.kmp.ui.screens.SearchResultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogseqViewModel(
    private val fileSystem: PlatformFileSystem,
    private val pageRepository: SimplePageRepository,
    private val blockRepository: BlockRepository,
    private val searchRepository: SearchRepository,
    private val graphLoader: GraphLoader,
    private val graphWriter: GraphWriter,
    private val platformSettings: PlatformSettings,
    private val scope: CoroutineScope,
    private val notificationManager: NotificationManager? = null
) {
    private val logger = Logger("LogseqViewModel")
    
    // Track recent pages manually to avoid "recently loaded" issues
    private var recentPageIds: MutableList<String> = platformSettings.getString("recent_pages", "")
        .split(",")
        .filter { it.isNotEmpty() }
        .toMutableList()
        
    private var cachedAllPages: List<Page> = emptyList()

    // Initialize command system
    private val commandManager = CommandManager.create(scope) { message, type, timeout ->
        notificationManager?.show(message, type, timeout)
    }
    
    // Debounce manager for block updates
    private val debounceManager = DebounceManager(scope, 300L)

    private val _uiState = MutableStateFlow(
        AppState(
            isLoading = true,
            onboardingCompleted = platformSettings.getBoolean("onboardingCompleted", false),
            currentGraphPath = platformSettings.getString("lastGraphPath", "")
        )
    )
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    init {
        updateCommands()
        
        // Initialize graph if path exists
        val path = _uiState.value.currentGraphPath
        if (path.isNotEmpty() && _uiState.value.onboardingCompleted) {
            loadGraph(path)
        }
        
        // Observe repository changes to update UI lists
        scope.launch {
            pageRepository.getAllPages().collect { allPages ->
                val result = allPages.getOrNull() ?: emptyList()
                cachedAllPages = result
                updateUiStateWithPages(result)
            }
        }
    }

    private fun updateUiStateWithPages(pages: List<Page>) {
        _uiState.update { state ->
            // Map recent UUIDs to actual Page objects, preserving order
            val recent = recentPageIds.mapNotNull { uuid -> 
                pages.find { it.uuid == uuid } 
            }
            
            state.copy(
                regularPages = pages.filter { !it.isJournal }.sortedBy { it.name },
                journalPages = pages.filter { it.isJournal }.sortedByDescending { it.name },
                favoritePages = pages.filter { it.isFavorite },
                recentPages = recent
            )
        }
    }

    private fun addToRecent(page: Page) {
        // Remove if exists to move to top
        recentPageIds.remove(page.uuid)
        recentPageIds.add(0, page.uuid)
        
        // Keep max 20 items
        if (recentPageIds.size > 20) {
            recentPageIds.removeAt(recentPageIds.lastIndex)
        }
        
        // Save to settings
        platformSettings.putString("recent_pages", recentPageIds.joinToString(","))
        
        // Update UI
        updateUiStateWithPages(cachedAllPages)
    }

    fun setGraphPath(path: String) {
        platformSettings.putString("lastGraphPath", path)
        _uiState.update { it.copy(currentGraphPath = path) }
        loadGraph(path)
    }

    fun loadGraph(path: String) {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, isFullyLoaded = false, statusMessage = "Loading graph from $path...") }

                var graphExists = fileSystem.directoryExists(path)

                if (!graphExists) {
                    _uiState.update { it.copy(statusMessage = "Creating directory $path...") }
                    logger.info("Creating graph directory: $path")
                    val created = fileSystem.createDirectory(path)
                    if (created) graphExists = true
                }

                if (graphExists) {
                    _uiState.update { it.copy(statusMessage = "Loading journals...") }
                    logger.info("Loading graph progressively from: $path")
                    
                    // Persistence Strategy:
                    // Only clear the cache if we are switching to a DIFFERENT graph.
                    // If we are reloading the same graph (e.g. startup), keep the DB to allow GraphLoader
                    // to skip unchanged files (Startup Performance).
                    val cachedPath = platformSettings.getString("cached_graph_path", "")
                    
                    if (path != cachedPath) {
                        logger.info("Switching graph from '$cachedPath' to '$path' - Clearing persistent cache")
                        pageRepository.clear()
                        blockRepository.clear()
                        platformSettings.putString("cached_graph_path", path)
                    } else {
                        logger.info("Loading same graph '$path' - Keeping persistent cache for incremental load")
                    }

                    withContext(Dispatchers.Default) {
                        graphLoader.loadGraphProgressive(
                            graphPath = path,
                            immediateJournalCount = 10,
                            onProgress = { status ->
                                _uiState.update { it.copy(statusMessage = status) }
                            },
                            onPhase1Complete = {
                                // UI becomes interactive after loading immediate journals
                                logger.info("Phase 1 complete - UI is now interactive")
                                _uiState.update { it.copy(isLoading = false, statusMessage = "Ready") }
                            },
                            onFullyLoaded = {
                                // All background loading is complete
                                logger.info("Graph fully loaded")
                                _uiState.update { it.copy(isFullyLoaded = true, statusMessage = "Graph loaded completely.") }
                            }
                        )
                    }

                    logger.info("Graph loaded successfully")
                } else {
                    _uiState.update { it.copy(statusMessage = "Failed to load graph.", isLoading = false, isFullyLoaded = true) }
                    logger.error("Failed to load graph at $path")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logger.error("Error loading graph", e)
                _uiState.update { it.copy(isLoading = false, isFullyLoaded = true, statusMessage = "Error: ${e.message}") }
            }
        }
    }

    fun refreshCurrentPage() {
        val currentScreen = _uiState.value.currentScreen
        if (currentScreen is Screen.PageView) {
            scope.launch {
                pageRepository.getPageByUuid(currentScreen.page.uuid).first().getOrNull()?.let { updatedPage ->
                    _uiState.update { it.copy(currentScreen = Screen.PageView(updatedPage)) }
                }
            }
        }
    }

    fun toggleFavorite(page: Page) {
        scope.launch {
            pageRepository.toggleFavorite(page.uuid)
            _uiState.update { it.copy(statusMessage = "Toggled favorite: ${page.name}") }
            refreshCurrentPage()
        }
    }

    fun indentBlock(blockUuid: String) {
        scope.launch {
            blockRepository.indentBlock(blockUuid)
        }
    }

    fun outdentBlock(blockUuid: String) {
        scope.launch {
            blockRepository.outdentBlock(blockUuid)
        }
    }

    fun moveBlockUp(blockUuid: String) {
        scope.launch {
            blockRepository.moveBlockUp(blockUuid)
        }
    }

    fun moveBlockDown(blockUuid: String) {
        scope.launch {
            blockRepository.moveBlockDown(blockUuid)
        }
    }

    fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int) {
        scope.launch {
            blockRepository.moveBlock(blockUuid, newParentUuid, newPosition)
        }
    }

    fun requestEditBlock(blockUuid: String?, cursorIndex: Int? = null) {
        _uiState.update { it.copy(editingBlockId = blockUuid, editingCursorIndex = cursorIndex) }
    }

    fun addNewBlock(currentBlockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(currentBlockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            val siblingsResult = blockRepository.getBlockSiblings(currentBlockUuid).first()
            val siblings = siblingsResult.getOrNull() ?: emptyList()

            val newPosition = currentBlock.position + 1
            
            // Shift siblings
            val siblingsToShift = siblings.filter { it.position >= newPosition }
            val updatedSiblings = siblingsToShift.map { it.copy(position = it.position + 1) }

            val now = kotlinx.datetime.Clock.System.now()
            val newBlock = Block(
                id = generateBlockId(),
                uuid = generateUuid(),
                pageId = currentBlock.pageId,
                parentId = currentBlock.parentId,
                leftId = currentBlock.id,
                content = "",
                level = currentBlock.level,
                position = newPosition,
                createdAt = now,
                updatedAt = now,
                properties = emptyMap(),
                isLoaded = true
            )

            val blocksToSave = updatedSiblings + newBlock
            blockRepository.saveBlocks(blocksToSave)
            
            requestEditBlock(newBlock.uuid)
        }
    }

    /**
     * Add a new block to the end of a page
     */
    fun addBlockToPage(pageUuid: String) {
        scope.launch {
            val pageResult = pageRepository.getPageByUuid(pageUuid).first()
            val page = pageResult.getOrNull() ?: return@launch

            val blocksResult = blockRepository.getBlocksForPage(page.id).first()
            val blocks = blocksResult.getOrNull() ?: emptyList()
            
            // Filter only top-level blocks (no parent)
            val topLevelBlocks = blocks.filter { it.parentId == null }.sortedBy { it.position }
            val lastBlock = topLevelBlocks.lastOrNull()
            
            val newPosition = (lastBlock?.position ?: 0) + 1
            val now = kotlinx.datetime.Clock.System.now()
            
            val newBlock = Block(
                id = generateBlockId(),
                uuid = generateUuid(),
                pageId = page.id,
                parentId = null,
                leftId = lastBlock?.id,
                content = "",
                level = 0,
                position = newPosition,
                createdAt = now,
                updatedAt = now,
                properties = emptyMap(),
                isLoaded = true
            )

            blockRepository.saveBlock(newBlock)
            requestEditBlock(newBlock.uuid)
        }
    }

    fun splitBlock(blockUuid: String, cursorPosition: Int) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            val fullContent = currentBlock.content
            val safeSplitIndex = cursorPosition.coerceIn(0, fullContent.length)

            val contentForCurrentBlock = fullContent.substring(0, safeSplitIndex)
            val contentForNewBlock = fullContent.substring(safeSplitIndex)

            // Update current block
            val updatedCurrentBlock = currentBlock.copy(content = contentForCurrentBlock)
            blockRepository.saveBlock(updatedCurrentBlock)

            val siblingsResult = blockRepository.getBlockSiblings(blockUuid).first()
            val siblings = siblingsResult.getOrNull() ?: emptyList()

            val newPosition = currentBlock.position + 1

            // Shift siblings
            val siblingsToShift = siblings.filter { it.position >= newPosition }
            val updatedSiblings = siblingsToShift.map { it.copy(position = it.position + 1) }

            val now = kotlinx.datetime.Clock.System.now()
            val newBlock = Block(
                id = generateBlockId(),
                uuid = generateUuid(),
                pageId = currentBlock.pageId,
                parentId = currentBlock.parentId,
                leftId = currentBlock.id,
                content = contentForNewBlock,
                level = currentBlock.level,
                position = newPosition,
                createdAt = now,
                updatedAt = now,
                properties = emptyMap(),
                isLoaded = true
            )

            val blocksToSave = updatedSiblings + newBlock
            blockRepository.saveBlocks(blocksToSave)

            requestEditBlock(newBlock.uuid)
        }
    }

    fun mergeBlock(blockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            // Get ALL siblings including current block
            val pageBlocksResult = blockRepository.getBlocksForPage(currentBlock.pageId).first()
            val allBlocks = pageBlocksResult.getOrNull() ?: return@launch
            val siblings = allBlocks
                .filter { it.parentId == currentBlock.parentId }
                .sortedBy { it.position }

            val currentIndex = siblings.indexOfFirst { it.uuid == currentBlock.uuid }

            if (currentIndex > 0) {
                val prevBlock = siblings[currentIndex - 1]
                val newContent = prevBlock.content + currentBlock.content
                val mergePoint = prevBlock.content.length

                val updatedPrevBlock = prevBlock.copy(content = newContent)
                blockRepository.saveBlock(updatedPrevBlock)

                blockRepository.deleteBlock(blockUuid)

                // Update subsequent siblings
                val subsequentSiblings = siblings.drop(currentIndex + 1)
                if (subsequentSiblings.isNotEmpty()) {
                    val updatedSubsequent = subsequentSiblings.mapIndexed { idx, block ->
                        block.copy(
                            position = currentIndex + idx,
                            leftId = if (idx == 0) prevBlock.id else subsequentSiblings[idx - 1].id
                        )
                    }
                    blockRepository.saveBlocks(updatedSubsequent)
                }

                requestEditBlock(prevBlock.uuid, mergePoint)
            }
        }
    }

    fun handleBackspace(blockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            val pageBlocksResult = blockRepository.getBlocksForPage(currentBlock.pageId).first()
            val allBlocks = pageBlocksResult.getOrNull() ?: return@launch
            val siblings = allBlocks
                .filter { it.parentId == currentBlock.parentId }
                .sortedBy { it.position }

            val currentIndex = siblings.indexOfFirst { it.uuid == currentBlock.uuid }

            if (currentIndex > 0) {
                val previousBlock = siblings[currentIndex - 1]
                blockRepository.deleteBlock(blockUuid)

                val subsequentSiblings = siblings.drop(currentIndex + 1)
                if (subsequentSiblings.isNotEmpty()) {
                    val updatedSubsequent = subsequentSiblings.mapIndexed { idx, block ->
                        block.copy(
                            position = currentIndex + idx,
                            leftId = if (idx == 0) previousBlock.id else subsequentSiblings[idx - 1].id
                        )
                    }
                    blockRepository.saveBlocks(updatedSubsequent)
                }

                requestEditBlock(previousBlock.uuid, previousBlock.content.length)

            } else if (currentBlock.parentId != null) {
                val parent = allBlocks.find { it.id == currentBlock.parentId }
                blockRepository.deleteBlock(blockUuid)

                val remainingSiblings = siblings.drop(1)
                if (remainingSiblings.isNotEmpty()) {
                    val updatedRemaining = remainingSiblings.mapIndexed { idx, block ->
                        block.copy(
                            position = idx,
                            leftId = if (idx == 0) null else remainingSiblings[idx - 1].id
                        )
                    }
                    blockRepository.saveBlocks(updatedRemaining)
                }

                if (parent != null) {
                    requestEditBlock(parent.uuid, parent.content.length)
                }
            } else if (siblings.size > 1) {
                val nextBlock = siblings[1]
                blockRepository.deleteBlock(blockUuid)

                val remainingSiblings = siblings.drop(1)
                val updatedRemaining = remainingSiblings.mapIndexed { idx, block ->
                    block.copy(
                        position = idx,
                        leftId = if (idx == 0) null else remainingSiblings[idx - 1].id
                    )
                }
                blockRepository.saveBlocks(updatedRemaining)

                requestEditBlock(nextBlock.uuid, 0)
            }
        }
    }

    fun focusPreviousBlock(blockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            val pageBlocksResult = blockRepository.getBlocksForPage(currentBlock.pageId).first()
            val allBlocks = pageBlocksResult.getOrNull() ?: return@launch
            val sortedBlocks = com.logseq.kmp.outliner.BlockSorter.sort(allBlocks)

            val currentIndex = sortedBlocks.indexOfFirst { it.uuid == blockUuid }

            if (currentIndex > 0) {
                val prevBlock = sortedBlocks[currentIndex - 1]
                requestEditBlock(prevBlock.uuid, prevBlock.content.length)
            }
        }
    }

    fun focusNextBlock(blockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            val pageBlocksResult = blockRepository.getBlocksForPage(currentBlock.pageId).first()
            val allBlocks = pageBlocksResult.getOrNull() ?: return@launch
            val sortedBlocks = com.logseq.kmp.outliner.BlockSorter.sort(allBlocks)

            val currentIndex = sortedBlocks.indexOfFirst { it.uuid == blockUuid }

            if (currentIndex != -1 && currentIndex < sortedBlocks.size - 1) {
                val nextBlock = sortedBlocks[currentIndex + 1]
                requestEditBlock(nextBlock.uuid, 0)
            }
        }
    }

    fun navigateTo(screen: Screen, addToHistory: Boolean = true) {
        _uiState.update { state ->
            val newHistory = if (addToHistory) {
                // Trim any forward history and add new screen
                val trimmed = state.navigationHistory.take(state.historyIndex + 1)
                trimmed + screen
            } else {
                state.navigationHistory
            }
            val newIndex = if (addToHistory) newHistory.size - 1 else state.historyIndex

            state.copy(
                currentScreen = screen,
                currentPage = if (screen is Screen.PageView) screen.page else null,
                navigationHistory = newHistory,
                historyIndex = newIndex,
                statusMessage = when(screen) {
                    is Screen.PageView -> {
                        addToRecent(screen.page)
                        "Opened page: ${screen.page.name}"
                    }
                    is Screen.Journals -> "Opened Journals"
                    is Screen.Flashcards -> "Opened Flashcards"
                    is Screen.AllPages -> "Opened All Pages"
                    is Screen.Notifications -> "Opened Notifications"
                    is Screen.Logs -> "Opened Logs"
                    is Screen.Performance -> "Opened Performance"
                }
            )
        }
        if (screen is Screen.PageView) {
            refreshCurrentPage()
        }
    }

    /**
     * Navigate back in history (Alt+Left or Cmd+[)
     */
    fun goBack(): Boolean {
        val state = _uiState.value
        if (!state.canGoBack) return false

        val newIndex = state.historyIndex - 1
        val screen = state.navigationHistory[newIndex]
        _uiState.update {
            it.copy(
                currentScreen = screen,
                currentPage = if (screen is Screen.PageView) screen.page else null,
                historyIndex = newIndex,
                statusMessage = "Back"
            )
        }
        return true
    }

    /**
     * Navigate forward in history (Alt+Right or Cmd+])
     */
    fun goForward(): Boolean {
        val state = _uiState.value
        if (!state.canGoForward) return false

        val newIndex = state.historyIndex + 1
        val screen = state.navigationHistory[newIndex]
        _uiState.update {
            it.copy(
                currentScreen = screen,
                currentPage = if (screen is Screen.PageView) screen.page else null,
                historyIndex = newIndex,
                statusMessage = "Forward"
            )
        }
        return true
    }

    fun navigateTo(destination: String) {
        val newScreen = when (destination) {
            "journals" -> Screen.Journals
            "flashcards" -> Screen.Flashcards
            "all-pages" -> Screen.AllPages
            "notifications" -> Screen.Notifications
            "logs" -> Screen.Logs
            else -> Screen.Journals
        }
        navigateTo(newScreen)
    }

    /**
     * Navigate to a page by its name (used for wiki links)
     * Creates the page if it doesn't exist
     */
    fun navigateToPageByName(pageName: String) {
        scope.launch {
            // Try to find the page by name
            val existingPage = pageRepository.getPageByName(pageName).first().getOrNull()
            if (existingPage != null) {
                navigateTo(Screen.PageView(existingPage))
            } else {
                // Page doesn't exist - create it
                val newPage = createPage(pageName)
                if (newPage != null) {
                    navigateTo(Screen.PageView(newPage))
                    _uiState.update { it.copy(statusMessage = "Created page: $pageName") }
                } else {
                    _uiState.update { it.copy(statusMessage = "Failed to create page: $pageName") }
                }
            }
        }
    }
    
    fun navigateToPageByUuid(pageUuid: String) {
        scope.launch {
            val page = pageRepository.getPageByUuid(pageUuid).first().getOrNull()
            if (page != null) {
                navigateTo(Screen.PageView(page))
            } else {
                _uiState.update { it.copy(statusMessage = "Page not found: $pageUuid") }
            }
        }
    }
    
    fun navigateToBlock(blockUuid: String) {
        scope.launch {
            val block = blockRepository.getBlockByUuid(blockUuid).first().getOrNull()
            if (block != null) {
                val page = cachedAllPages.find { it.id == block.pageId }
                if (page != null) {
                    navigateTo(Screen.PageView(page))
                    // TODO: Scroll to block
                }
            }
        }
    }

    /**
     * Create a new page with the given name
     */
    private suspend fun createPage(pageName: String): Page? {
        return try {
            val now = kotlinx.datetime.Clock.System.now()
            val uuid = generateUuid()
            val pageId = generatePageId()

            // Detect if this is a journal page (matches date patterns like 2026-01-21 or 2026_01_21)
            val isJournal = pageName.matches(Regex("^\\d{4}[-_]\\d{2}[-_]\\d{2}$"))

            val newPage = Page(
                id = pageId,
                uuid = uuid,
                name = pageName,
                namespace = null,
                filePath = null, // Will be set when saving
                createdAt = now,
                updatedAt = now,
                properties = emptyMap(),
                isFavorite = false,
                isJournal = isJournal
            )

            pageRepository.savePage(newPage)
            logger.info("Created new page: $pageName")
            newPage
        } catch (e: Exception) {
            logger.error("Failed to create page: $pageName", e)
            null
        }
    }

    /**
     * Generate a UUID for new entities
     */
    private fun generateUuid(): String {
        // Simple UUID-like generation (could use platform-specific UUID)
        val chars = "0123456789abcdef"
        fun randomHex(length: Int) = (1..length).map { chars.random() }.joinToString("")
        return "${randomHex(8)}-${randomHex(4)}-${randomHex(4)}-${randomHex(4)}-${randomHex(12)}"
    }

    /**
     * Generate a unique page ID
     */
    private var pageIdCounter = Clock.System.now().toEpochMilliseconds()
    private fun generatePageId(): Long = pageIdCounter++

    private var blockIdCounter = Clock.System.now().toEpochMilliseconds()
    private fun generateBlockId(): Long = blockIdCounter++

    // requestEditBlock and addNewBlock were duplicated here - removing the second definitions
    
    /**
     * Get the content of a block by its UUID
     */
    suspend fun getBlockContent(blockUuid: String): String? {
        val blockResult = blockRepository.getBlockByUuid(blockUuid).first()
        return blockResult.getOrNull()?.content
    }

    /**
     * Save a block's content change and persist to disk via GraphWriter
     */
    fun saveBlockContent(blockId: String, newContent: String, version: Long, page: Page) {
        // Debounce the database write to avoid UI stutter on every keystroke
        debounceManager.debounce(blockId) {
            try {
                // 1. Get the current block
                val blockResult = blockRepository.getBlockByUuid(blockId).first()
                val block = blockResult.getOrNull() ?: run {
                    logger.error("Block not found: $blockId")
                    return@debounce
                }

                // 2. Update the block with new content and version
                val updatedBlock = block.copy(content = newContent, version = version)
                blockRepository.saveBlock(updatedBlock)

                // 3. Get all blocks for the page to save to disk
                val allBlocksResult = blockRepository.getBlocksForPage(page.id).first()
                val allBlocks = allBlocksResult.getOrNull() ?: emptyList()

                // 4. Queue save to disk (debounced)
                val graphPath = _uiState.value.currentGraphPath
                if (graphPath.isNotEmpty()) {
                    graphWriter.queueSave(page, allBlocks, graphPath)
                }

                logger.debug("Block content saved: $blockId")
            } catch (e: Exception) {
                logger.error("Failed to save block content", e)
                _uiState.update { it.copy(statusMessage = "Error saving: ${e.message}") }
            }
        }
    }

    /**
     * Start the auto-save processor
     */
    fun startAutoSave() {
        graphWriter.startAutoSave(scope)
        logger.info("Auto-save started")
    }

    /**
     * Stop the auto-save processor
     */
    fun stopAutoSave() {
        graphWriter.stopAutoSave()
        logger.info("Auto-save stopped")
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(sidebarExpanded = !it.sidebarExpanded) }
        updateCommands()
    }

    fun toggleRightSidebar() {
        _uiState.update { it.copy(rightSidebarExpanded = !it.rightSidebarExpanded) }
        updateCommands()
    }

    fun setSettingsVisible(visible: Boolean) {
        _uiState.update { it.copy(settingsVisible = visible) }
    }

    fun setCommandPaletteVisible(visible: Boolean) {
        _uiState.update { it.copy(commandPaletteVisible = visible) }
    }
    
    fun setSearchDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(searchDialogVisible = visible) }
    }

    fun setThemeMode(mode: LogseqThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        updateCommands()
    }

    fun setLanguage(language: Language) {
        _uiState.update { it.copy(language = language) }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        platformSettings.putBoolean("onboardingCompleted", completed)
        _uiState.update { it.copy(onboardingCompleted = completed) }
    }
    
    fun toggleDebugMode() {
        _uiState.update { it.copy(isDebugMode = !it.isDebugMode) }
    }
    
    /**
     * Execute a command by ID
     */
    suspend fun executeCommand(commandId: String, context: CommandContext = CommandContext()): CommandResult {
        return commandManager.executeCommand(commandId, context)
    }
    
    /**
     * Execute a slash command
     */
    suspend fun executeSlashCommand(input: String, context: CommandContext = CommandContext()): CommandResult {
        return commandManager.executeSlashCommand(input, context)
    }
    
    /**
     * Get command suggestions for the command palette
     */
    suspend fun getCommandSuggestions(query: String, context: CommandContext = CommandContext()): List<EditorCommand> {
        return commandManager.getCommandSuggestions(query, context).map { it.command }
    }
    
    /**
     * Check if input is a slash command
     */
    suspend fun isSlashCommand(input: String): Boolean {
        return commandManager.isSlashCommand(input)
    }
    
    /**
     * Get available commands for current context
     */
    suspend fun getAvailableCommands(): List<EditorCommand> {
        val context = CommandContext(
            currentPageId = _uiState.value.currentPage?.uuid,
            currentBlockId = _uiState.value.currentPage?.uuid // This would be updated by actual editor
        )
        return commandManager.getAvailableCommands(context)
    }

    private fun updateCommands() {
        scope.launch {
            try {
                val availableCommands = getAvailableCommands()
                val legacyCommands = availableCommands.map { cmd ->
                    Command(cmd.id, cmd.label, cmd.shortcut) {
                        scope.launch {
                            executeCommand(cmd.id)
                        }
                    }
                }
                _uiState.update { it.copy(commands = legacyCommands) }
            } catch (e: Exception) {
                logger.error("Failed to update commands", e)
            }
        }
    }
    
    /**
     * Get the command manager for advanced usage
     */
    fun getCommandManager(): CommandManager = commandManager
    
    /**
     * Search pages for autocomplete
     */
    fun searchPages(query: String): Flow<List<SearchResultItem>> {
        val request = SearchRequest(query = query, limit = 10)
        return searchRepository.searchWithFilters(request).map {
            val searchResult = it.getOrNull()
            if (searchResult != null) {
                val items = mutableListOf<SearchResultItem>()
                // Add Pages and Aliases
                searchResult.pages.forEach { page ->
                    // Check if it's a name match
                    if (page.name.contains(query, ignoreCase = true)) {
                        items.add(SearchResultItem.PageItem(page))
                    }
                    
                    // Check for alias matches
                    val aliases = page.properties["alias"]?.split(",")?.map { it.trim() } ?: emptyList()
                    aliases.forEach { alias ->
                        if (alias.contains(query, ignoreCase = true) && !alias.equals(page.name, ignoreCase = true)) {
                            // Only add alias if it's not the same as the page name (to avoid duplicates)
                            // and if it hasn't been added already for this page
                            if (items.none { it is SearchResultItem.AliasItem && it.page.id == page.id && it.alias.equals(alias, ignoreCase = true) }) {
                                items.add(SearchResultItem.AliasItem(page, alias))
                            }
                        }
                    }
                }
                
                // Add "Create Page" option if no exact match
                val exactMatch = items.any { 
                    (it is SearchResultItem.PageItem && it.page.name.equals(query, ignoreCase = true)) ||
                    (it is SearchResultItem.AliasItem && it.alias.equals(query, ignoreCase = true))
                }
                if (!exactMatch && query.isNotBlank()) {
                    items.add(SearchResultItem.CreatePageItem(query))
                }
                items
            } else {
                emptyList()
            }
        }
    }
}