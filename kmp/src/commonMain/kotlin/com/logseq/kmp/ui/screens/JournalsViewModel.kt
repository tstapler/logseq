package com.logseq.kmp.ui.screens

import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.PageRepository
import com.logseq.kmp.logging.Logger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.logseq.kmp.outliner.BlockSorter

private data class UndoEntry(
    val undo: suspend () -> Unit,
    val redo: (suspend () -> Unit)?
)

/**
 * ViewModel for Journals screen.
 * Updated to use UUID-native storage for all references.
 */
class JournalsViewModel(
    private val pageRepository: PageRepository,
    private val blockRepository: BlockRepository,
    private val graphLoader: GraphLoader,
    private val scope: CoroutineScope
) {
    private val logger = Logger("JournalsViewModel")
    private val _uiState = MutableStateFlow(JournalsUiState())
    val uiState: StateFlow<JournalsUiState> = _uiState.asStateFlow()

    // --- Undo/Redo ---
    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()
    private val MAX_UNDO = 100

    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun record(undo: suspend () -> Unit, redo: (suspend () -> Unit)? = null) {
        undoStack.addLast(UndoEntry(undo, redo))
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
    }

    fun undo(): Job = scope.launch {
        val entry = undoStack.removeLastOrNull() ?: return@launch
        entry.undo()
        if (entry.redo != null) {
            redoStack.addLast(entry)
            _canRedo.value = true
        } else {
            redoStack.clear()
            _canRedo.value = false
        }
        _canUndo.value = undoStack.isNotEmpty()
    }

    fun redo(): Job = scope.launch {
        val entry = redoStack.removeLastOrNull() ?: return@launch
        entry.redo?.invoke() ?: return@launch
        undoStack.addLast(entry)
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
    }

    // --- Undo/Redo helpers ---

    private suspend fun applyContentChange(blockUuid: String, content: String, version: Long) {
        val block = blockRepository.getBlockByUuid(blockUuid).first().getOrNull() ?: return
        val updated = block.copy(content = content, version = version)
        blockRepository.saveBlock(updated)
        _uiState.update { state ->
            val newBlocks = state.blocks.toMutableMap()
            val pageBlocks = newBlocks[block.pageUuid]?.toMutableList() ?: return@update state
            val idx = pageBlocks.indexOfFirst { it.uuid == blockUuid }
            if (idx >= 0) pageBlocks[idx] = updated
            newBlocks[block.pageUuid] = pageBlocks
            state.copy(blocks = newBlocks)
        }
    }

    private suspend fun getPageUuidForBlock(blockUuid: String): String? {
        return _uiState.value.blocks.entries
            .find { (_, blocks) -> blocks.any { it.uuid == blockUuid } }
            ?.key
            ?: blockRepository.getBlockByUuid(blockUuid).first().getOrNull()?.pageUuid
    }

    private suspend fun takePageSnapshot(pageUuid: String): List<Block> =
        blockRepository.getBlocksForPage(pageUuid).first().getOrNull() ?: emptyList()

    private suspend fun restorePageToSnapshot(pageUuid: String, snapshot: List<Block>) {
        val current = blockRepository.getBlocksForPage(pageUuid).first().getOrNull() ?: return
        val snapshotUuids = snapshot.map { it.uuid }.toSet()
        val currentUuids = current.map { it.uuid }.toSet()
        (currentUuids - snapshotUuids).forEach { uuid ->
            blockRepository.deleteBlock(uuid, deleteChildren = false)
        }
        blockRepository.saveBlocks(snapshot)
        _uiState.update { state ->
            val newBlocks = state.blocks.toMutableMap()
            newBlocks[pageUuid] = snapshot
            state.copy(blocks = newBlocks)
        }
    }

    private val pageSize = 10
    private var totalVisibleCount = pageSize
    private var isLoading = false
    private var hasMore = true

    private val blockCollectionJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private var paginationJob: kotlinx.coroutines.Job? = null

    init {
        startPaginationObserver()
        generateTodayJournal()
    }

    /**
     * Ensures today's journal entry exists.
     */
    fun generateTodayJournal(): Job = scope.launch {
        val today = kotlin.time.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
        val pageName = today.toString() // Standard YYYY-MM-DD
        
        val existing = pageRepository.getPageByName(pageName).first().getOrNull()
        if (existing == null) {
            val pageUuid = com.logseq.kmp.util.UuidGenerator.generateV7()
            val newPage = Page(
                uuid = pageUuid,
                name = pageName,
                createdAt = today.atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault()),
                updatedAt = kotlin.time.Clock.System.now(),
                isJournal = true,
                journalDate = today
            )
            pageRepository.savePage(newPage)
            
            // Add initial block
            val initialBlock = Block(
                uuid = com.logseq.kmp.util.UuidGenerator.generateV7(),
                pageUuid = pageUuid,
                content = "",
                position = 0,
                createdAt = kotlin.time.Clock.System.now(),
                updatedAt = kotlin.time.Clock.System.now()
            )
            blockRepository.saveBlock(initialBlock)
            
            logger.info("Generated today's journal: $pageName")
        }
    }

    private fun startPaginationObserver() {
        paginationJob?.cancel()
        paginationJob = scope.launch {
            // Observe all visible journal pages reactively
            pageRepository.getJournalPages(totalVisibleCount, 0).collect { result ->
                val journals = result.getOrNull() ?: emptyList()
                
                _uiState.update { it.copy(
                    pages = journals,
                    isLoading = false,
                    hasMore = journals.size >= totalVisibleCount
                ) }
                
                // Ensure blocks are observed for all current pages
                observeBlocksForPages(journals)
            }
        }
    }

    private fun observeBlocksForPages(pages: List<Page>) {
        val currentUuids = pages.map { it.uuid }.toSet()
        
        // Cancel jobs for pages no longer visible
        blockCollectionJobs.keys.filter { it !in currentUuids }.forEach { uuid ->
            blockCollectionJobs.remove(uuid)?.cancel()
        }

        pages.forEach { page ->
            if (page.uuid !in blockCollectionJobs) {
                blockCollectionJobs[page.uuid] = scope.launch {
                    // Trigger initial load if needed
                    if (!page.isContentLoaded) {
                        graphLoader.loadFullPage(page.uuid)
                    }
                    
                    // Observe blocks reactively for this page
                    blockRepository.getBlocksForPage(page.uuid).collect { result ->
                        val blocks = result.getOrNull() ?: emptyList()
                        _uiState.update { state ->
                            val newBlocks = state.blocks.toMutableMap()
                            newBlocks[page.uuid] = blocks
                            state.copy(blocks = newBlocks)
                        }
                    }
                }
            }
        }
    }

    fun loadMore() {
        if (isLoading || !hasMore) return

        totalVisibleCount += pageSize
        startPaginationObserver()
    }
    
    fun refresh() {
        totalVisibleCount = pageSize
        hasMore = true
        _uiState.update { it.copy(pages = emptyList(), blocks = emptyMap()) }
        startPaginationObserver()
    }
    
    fun updateBlockContent(blockUuid: String, newContent: String, newVersion: Long): Job = scope.launch {
        val block = blockRepository.getBlockByUuid(blockUuid).first().getOrNull() ?: return@launch
        val oldContent = block.content
        val oldVersion = block.version
        if (oldContent == newContent) return@launch

        applyContentChange(blockUuid, newContent, newVersion)

        record(
            undo = {
                applyContentChange(blockUuid, oldContent, oldVersion)
                requestEditBlock(blockUuid, oldContent.length)
            },
            redo = {
                applyContentChange(blockUuid, newContent, newVersion)
                requestEditBlock(blockUuid, newContent.length)
            }
        )
    }
    
    fun indentBlock(blockUuid: String): Job = scope.launch {
        val pageUuid = getPageUuidForBlock(blockUuid) ?: return@launch
        val before = takePageSnapshot(pageUuid)
        blockRepository.indentBlock(blockUuid)
        refreshBlocksForPage(blockUuid)
        val after = takePageSnapshot(pageUuid)
        record(
            undo = { restorePageToSnapshot(pageUuid, before); requestEditBlock(blockUuid) },
            redo = { restorePageToSnapshot(pageUuid, after); requestEditBlock(blockUuid) }
        )
    }

    fun outdentBlock(blockUuid: String): Job = scope.launch {
        val pageUuid = getPageUuidForBlock(blockUuid) ?: return@launch
        val before = takePageSnapshot(pageUuid)
        blockRepository.outdentBlock(blockUuid)
        refreshBlocksForPage(blockUuid)
        val after = takePageSnapshot(pageUuid)
        record(
            undo = { restorePageToSnapshot(pageUuid, before); requestEditBlock(blockUuid) },
            redo = { restorePageToSnapshot(pageUuid, after); requestEditBlock(blockUuid) }
        )
    }

    fun moveBlockUp(blockUuid: String): Job = scope.launch {
        val pageUuid = getPageUuidForBlock(blockUuid) ?: return@launch
        val before = takePageSnapshot(pageUuid)
        blockRepository.moveBlockUp(blockUuid)
        refreshBlocksForPage(blockUuid)
        val after = takePageSnapshot(pageUuid)
        record(
            undo = { restorePageToSnapshot(pageUuid, before); requestEditBlock(blockUuid) },
            redo = { restorePageToSnapshot(pageUuid, after); requestEditBlock(blockUuid) }
        )
    }

    fun moveBlockDown(blockUuid: String): Job = scope.launch {
        val pageUuid = getPageUuidForBlock(blockUuid) ?: return@launch
        val before = takePageSnapshot(pageUuid)
        blockRepository.moveBlockDown(blockUuid)
        refreshBlocksForPage(blockUuid)
        val after = takePageSnapshot(pageUuid)
        record(
            undo = { restorePageToSnapshot(pageUuid, before); requestEditBlock(blockUuid) },
            redo = { restorePageToSnapshot(pageUuid, after); requestEditBlock(blockUuid) }
        )
    }

    private suspend fun refreshBlocksForPage(blockUuid: String) {
        val pageUuid = _uiState.value.blocks.entries
            .find { (_, blocks) -> blocks.any { it.uuid == blockUuid } }
            ?.key
            ?: return

        val pageBlocks = blockRepository.getBlocksForPage(pageUuid).first().getOrNull() ?: return

        _uiState.update { state ->
            val newBlocks = state.blocks.toMutableMap()
            newBlocks[pageUuid] = pageBlocks
            state.copy(blocks = newBlocks)
        }
    }

    fun loadPageContent(pageUuid: String): Job = scope.launch {
        if (_uiState.value.loadingPageUuids.contains(pageUuid)) return@launch
        
        _uiState.update { it.copy(loadingPageUuids = it.loadingPageUuids + pageUuid) }
        try {
            val page = _uiState.value.pages.find { it.uuid == pageUuid }
            
            // Check if we need to reload - either page not fully loaded OR blocks not loaded
            val blocksResult = blockRepository.getBlocksForPage(pageUuid).first()
            val currentBlocks = blocksResult.getOrNull() ?: emptyList()
            val hasUnloadedBlocks = currentBlocks.isNotEmpty() && currentBlocks.any { !it.isLoaded }
            
            if (page != null && (!page.isContentLoaded || hasUnloadedBlocks)) {
                graphLoader.loadFullPage(page.uuid)
            }
            
            // Re-fetch blocks after potential reload
            val result = blockRepository.getBlocksForPage(pageUuid).first()
            val blocks = result.getOrNull() ?: emptyList()
            
            _uiState.update { state ->
                val newBlocks = state.blocks.toMutableMap()
                newBlocks[pageUuid] = blocks
                state.copy(blocks = newBlocks, loadingPageUuids = state.loadingPageUuids - pageUuid)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(loadingPageUuids = it.loadingPageUuids - pageUuid) }
        }
    }

    fun requestEditBlock(blockUuid: String?, cursorIndex: Int? = null) {
        _uiState.update { it.copy(editingBlockUuid = blockUuid, editingCursorIndex = cursorIndex) }
    }

    private fun generateUuid(): String {
        return com.logseq.kmp.util.UuidGenerator.generateV7()
    }

    fun addNewBlock(currentBlockUuid: String): Job = scope.launch {
        val block = blockRepository.getBlockByUuid(currentBlockUuid).first().getOrNull() ?: return@launch
        val pageUuid = block.pageUuid
        val before = takePageSnapshot(pageUuid)
        blockRepository.splitBlock(currentBlockUuid, block.content.length).onSuccess { newBlock ->
            requestEditBlock(newBlock.uuid)
            val after = takePageSnapshot(pageUuid)
            record(
                undo = { restorePageToSnapshot(pageUuid, before); requestEditBlock(currentBlockUuid) },
                redo = { restorePageToSnapshot(pageUuid, after); requestEditBlock(newBlock.uuid) }
            )
        }
    }

    fun splitBlock(blockUuid: String, cursorPosition: Int): Job = scope.launch {
        val pageUuid = getPageUuidForBlock(blockUuid) ?: return@launch
        val before = takePageSnapshot(pageUuid)
        blockRepository.splitBlock(blockUuid, cursorPosition).onSuccess { newBlock ->
            requestEditBlock(newBlock.uuid)
            val after = takePageSnapshot(pageUuid)
            record(
                undo = { restorePageToSnapshot(pageUuid, before); requestEditBlock(blockUuid, cursorPosition) },
                redo = { restorePageToSnapshot(pageUuid, after); requestEditBlock(newBlock.uuid) }
            )
        }
    }

    fun addBlockToPage(pageUuid: String): Job = scope.launch {
        val pageResult = pageRepository.getPageByUuid(pageUuid).first()
        val page = pageResult.getOrNull() ?: return@launch

        val blocksResult = blockRepository.getBlocksForPage(page.uuid).first()
        val blocks = blocksResult.getOrNull() ?: emptyList()
        
        val topLevelBlocks = blocks.filter { it.parentUuid == null }.sortedBy { it.position }
        val lastBlock = topLevelBlocks.lastOrNull()
        
        val newPosition = if (lastBlock != null) (lastBlock.position) + 1 else 0

        val now = kotlin.time.Clock.System.now()
        val newBlock = Block(
            uuid = generateUuid(),
            pageUuid = page.uuid,
            parentUuid = null,
            leftUuid = lastBlock?.uuid,
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

    fun mergeBlock(blockUuid: String): Job = scope.launch {
        val currentBlock = blockRepository.getBlockByUuid(blockUuid).first().getOrNull() ?: return@launch
        val pageUuid = currentBlock.pageUuid
        val before = takePageSnapshot(pageUuid)

        val pageBlocks = blockRepository.getBlocksForPage(pageUuid).first().getOrNull() ?: return@launch
        val siblings = pageBlocks
            .filter { it.parentUuid == currentBlock.parentUuid }
            .sortedBy { it.position }

        val currentIndex = siblings.indexOfFirst { it.uuid == blockUuid }

        if (currentIndex > 0) {
            val prevBlock = siblings[currentIndex - 1]
            blockRepository.mergeBlocks(prevBlock.uuid, blockUuid, "").onSuccess {
                requestEditBlock(prevBlock.uuid, prevBlock.content.length)
                val after = takePageSnapshot(pageUuid)
                record(
                    undo = { restorePageToSnapshot(pageUuid, before); requestEditBlock(blockUuid, 0) },
                    redo = { restorePageToSnapshot(pageUuid, after); requestEditBlock(prevBlock.uuid, prevBlock.content.length) }
                )
            }
        }
    }

    fun handleBackspace(blockUuid: String): Job = scope.launch {
        val currentBlock = blockRepository.getBlockByUuid(blockUuid).first().getOrNull() ?: return@launch
        val pageUuid = currentBlock.pageUuid
        val before = takePageSnapshot(pageUuid)

        val pageBlocks = blockRepository.getBlocksForPage(pageUuid).first().getOrNull() ?: return@launch
        val siblings = pageBlocks
            .filter { it.parentUuid == currentBlock.parentUuid }
            .sortedBy { it.position }

        val currentIndex = siblings.indexOfFirst { it.uuid == blockUuid }

        suspend fun afterOp(focusUuid: String, focusPos: Int) {
            requestEditBlock(focusUuid, focusPos)
            val after = takePageSnapshot(pageUuid)
            record(
                undo = { restorePageToSnapshot(pageUuid, before); requestEditBlock(blockUuid, 0) },
                redo = { restorePageToSnapshot(pageUuid, after); requestEditBlock(focusUuid, focusPos) }
            )
        }

        if (currentIndex > 0) {
            val prevBlock = siblings[currentIndex - 1]
            blockRepository.mergeBlocks(prevBlock.uuid, blockUuid, "").onSuccess {
                afterOp(prevBlock.uuid, prevBlock.content.length)
            }
        } else if (currentBlock.parentUuid != null) {
            val parent = pageBlocks.find { it.uuid == currentBlock.parentUuid }
            if (parent != null) {
                if (currentBlock.content.isEmpty()) {
                    blockRepository.deleteBlock(blockUuid)
                    afterOp(parent.uuid, parent.content.length)
                } else {
                    blockRepository.mergeBlocks(parent.uuid, blockUuid, "").onSuccess {
                        afterOp(parent.uuid, parent.content.length)
                    }
                }
            }
        } else {
            if (currentBlock.content.isEmpty() && siblings.size > 1) {
                val nextBlock = siblings[1]
                blockRepository.deleteBlock(blockUuid)
                afterOp(nextBlock.uuid, 0)
            }
        }
    }

    fun toggleBlockCollapse(blockUuid: String) {
        _uiState.update { state ->
            val newCollapsed = if (blockUuid in state.collapsedBlockUuids) {
                state.collapsedBlockUuids - blockUuid
            } else {
                state.collapsedBlockUuids + blockUuid
            }
            state.copy(collapsedBlockUuids = newCollapsed)
        }
    }

    fun focusPreviousBlock(blockUuid: String): Job = scope.launch {
        val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
        val currentBlock = currentBlockResult.getOrNull() ?: return@launch
        
        val visibleBlocks = getVisibleBlocksForPage(currentBlock.pageUuid)
        val currentIndex = visibleBlocks.indexOfFirst { it.uuid == blockUuid }
        
        if (currentIndex > 0) {
            val prevBlock = visibleBlocks[currentIndex - 1]
            requestEditBlock(prevBlock.uuid, prevBlock.content.length) // Focus end
        }
    }

    fun focusNextBlock(blockUuid: String): Job = scope.launch {
        val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
        val currentBlock = currentBlockResult.getOrNull() ?: return@launch
        
        val visibleBlocks = getVisibleBlocksForPage(currentBlock.pageUuid)
        val currentIndex = visibleBlocks.indexOfFirst { it.uuid == blockUuid }
        
        if (currentIndex != -1 && currentIndex < visibleBlocks.size - 1) {
            val nextBlock = visibleBlocks[currentIndex + 1]
            requestEditBlock(nextBlock.uuid, 0) // Focus start
        }
    }

    private fun getVisibleBlocksForPage(pageUuid: String): List<Block> {
        val blocks = _uiState.value.blocks[pageUuid] ?: return emptyList()
        val sortedBlocks = BlockSorter.sort(blocks)
        
        val collapsedUuids = _uiState.value.collapsedBlockUuids
        if (collapsedUuids.isEmpty()) return sortedBlocks
        
        val childrenByParent = blocks.groupBy { it.parentUuid }
        
        fun getDescendantUuids(blockUuid: String): Set<String> {
            val descendants = mutableSetOf<String>()
            val queue = ArrayDeque<String>()
            queue.add(blockUuid)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                childrenByParent[current]?.forEach { child ->
                    descendants.add(child.uuid)
                    queue.add(child.uuid)
                }
            }
            return descendants
        }
        
        val hiddenUuids = collapsedUuids.flatMap { getDescendantUuids(it) }.toSet()
        
        return sortedBlocks.filter { it.uuid !in hiddenUuids }
    }
}

data class JournalsUiState(
    val pages: List<Page> = emptyList(),
    val blocks: Map<String, List<Block>> = emptyMap(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val loadingPageUuids: Set<String> = emptySet(),
    val editingBlockUuid: String? = null,
    val editingCursorIndex: Int? = null,
    val collapsedBlockUuids: Set<String> = emptySet()
)
