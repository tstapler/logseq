package com.logseq.kmp.ui.screens

import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.PageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.logseq.kmp.outliner.BlockSorter

class JournalsViewModel(
    private val pageRepository: PageRepository,
    private val blockRepository: BlockRepository,
    private val graphLoader: GraphLoader,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(JournalsUiState())
    val uiState: StateFlow<JournalsUiState> = _uiState.asStateFlow()

    private val pageSize = 10
    private var totalVisibleCount = pageSize
    private var isLoading = false
    private var hasMore = true

    private val blockCollectionJobs = mutableMapOf<Long, kotlinx.coroutines.Job>()
    private var paginationJob: kotlinx.coroutines.Job? = null

    init {
        startPaginationObserver()
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
        val currentIds = pages.map { it.id }.toSet()
        
        // Cancel jobs for pages no longer visible (optional, for performance)
        blockCollectionJobs.keys.filter { it !in currentIds }.forEach { id ->
            blockCollectionJobs.remove(id)?.cancel()
        }

        pages.forEach { page ->
            if (page.id !in blockCollectionJobs) {
                blockCollectionJobs[page.id] = scope.launch {
                    // Trigger initial load if needed
                    if (!page.isContentLoaded) {
                        graphLoader.loadFullPage(page.uuid)
                    }
                    
                    // Observe blocks reactively for this page
                    blockRepository.getBlocksForPage(page.id).collect { result ->
                        val blocks = result.getOrNull() ?: emptyList()
                        _uiState.update { state ->
                            val newBlocks = state.blocks.toMutableMap()
                            newBlocks[page.id] = blocks
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
    
    fun updateBlockContent(blockUuid: String, newContent: String, newVersion: Long) {
        scope.launch {
            val blockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val block = blockResult.getOrNull() ?: return@launch

            val updatedBlock = block.copy(content = newContent, version = newVersion)
            blockRepository.saveBlock(updatedBlock)

            // Update just this block in the local state without refreshing from repository
            // This prevents the UI from resetting during typing
            _uiState.update { state ->
                val newBlocks = state.blocks.toMutableMap()
                val pageBlocks = newBlocks[block.pageId]?.toMutableList() ?: return@update state
                val blockIndex = pageBlocks.indexOfFirst { it.uuid == blockUuid }
                if (blockIndex >= 0) {
                    pageBlocks[blockIndex] = updatedBlock
                    newBlocks[block.pageId] = pageBlocks
                }
                state.copy(blocks = newBlocks)
            }
        }
    }
    
    fun indentBlock(blockUuid: String) {
        scope.launch {
            blockRepository.indentBlock(blockUuid)
            refreshBlocksForPage(blockUuid)
        }
    }

    fun outdentBlock(blockUuid: String) {
        scope.launch {
            blockRepository.outdentBlock(blockUuid)
            refreshBlocksForPage(blockUuid)
        }
    }

    fun moveBlockUp(blockUuid: String) {
        scope.launch {
            blockRepository.moveBlockUp(blockUuid)
            refreshBlocksForPage(blockUuid)
        }
    }

    fun moveBlockDown(blockUuid: String) {
        scope.launch {
            blockRepository.moveBlockDown(blockUuid)
            refreshBlocksForPage(blockUuid)
        }
    }

    private suspend fun refreshBlocksForPage(blockUuid: String) {
        // Find the pageId from the current UI state to avoid an extra query
        val pageId = _uiState.value.blocks.entries
            .find { (_, blocks) -> blocks.any { it.uuid == blockUuid } }
            ?.key
            ?: return

        // Single query to get updated blocks
        val pageBlocks = blockRepository.getBlocksForPage(pageId).first().getOrNull() ?: return

        _uiState.update { state ->
            val newBlocks = state.blocks.toMutableMap()
            newBlocks[pageId] = pageBlocks
            state.copy(blocks = newBlocks)
        }
    }

    fun loadPageContent(pageId: Long) {
        if (_uiState.value.loadingPageIds.contains(pageId)) return
        
        scope.launch {
            _uiState.update { it.copy(loadingPageIds = it.loadingPageIds + pageId) }
            try {
                val page = _uiState.value.pages.find { it.id == pageId }
                if (page != null && !page.isContentLoaded) {
                    graphLoader.loadFullPage(page.uuid)
                }
                // Refresh blocks for the page
                val result = blockRepository.getBlocksForPage(pageId).first()
                val blocks = result.getOrNull() ?: emptyList()
                
                _uiState.update { state ->
                    val newBlocks = state.blocks.toMutableMap()
                    newBlocks[pageId] = blocks
                    state.copy(blocks = newBlocks, loadingPageIds = state.loadingPageIds - pageId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(loadingPageIds = it.loadingPageIds - pageId) }
            }
        }
    }

    fun requestEditBlock(blockUuid: String?, cursorIndex: Int? = null) {
        _uiState.update { it.copy(editingBlockId = blockUuid, editingCursorIndex = cursorIndex) }
    }

    private var blockIdCounter = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    private fun generateBlockId(): Long = blockIdCounter++
    
    private fun generateUuid(): String {
        val chars = "0123456789abcdef"
        fun randomHex(length: Int) = (1..length).map { chars.random() }.joinToString("")
        return "${randomHex(8)}-${randomHex(4)}-${randomHex(4)}-${randomHex(4)}-${randomHex(12)}"
    }

    fun addNewBlock(currentBlockUuid: String) {
        scope.launch {
            // New block below current block is same as split at the end
            blockRepository.getBlockByUuid(currentBlockUuid).first().getOrNull()?.let { block ->
                blockRepository.splitBlock(currentBlockUuid, block.content.length).onSuccess { newBlock ->
                    requestEditBlock(newBlock.uuid)
                }
            }
        }
    }

    fun splitBlock(blockUuid: String, cursorPosition: Int) {
        scope.launch {
            blockRepository.splitBlock(blockUuid, cursorPosition).onSuccess { newBlock ->
                requestEditBlock(newBlock.uuid)
            }
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
            
            if (lastBlock != null) {
                // Split at end of last block to append
                blockRepository.splitBlock(lastBlock.uuid, lastBlock.content.length).onSuccess { newBlock ->
                    requestEditBlock(newBlock.uuid)
                }
            } else {
                // First block on page
                val now = kotlinx.datetime.Clock.System.now()
                val newBlock = Block(
                    id = generateBlockId(),
                    uuid = generateUuid(),
                    pageId = page.id,
                    parentId = null,
                    leftId = null,
                    content = "",
                    level = 0,
                    position = 0,
                    createdAt = now,
                    updatedAt = now,
                    properties = emptyMap(),
                    isLoaded = true
                )
                blockRepository.saveBlock(newBlock)
                requestEditBlock(newBlock.uuid)
            }
        }
    }

    fun mergeBlock(blockUuid: String) {
        scope.launch {
            val currentBlock = blockRepository.getBlockByUuid(blockUuid).first().getOrNull() ?: return@launch

            // Get siblings including current block
            val pageBlocks = blockRepository.getBlocksForPage(currentBlock.pageId).first().getOrNull() ?: return@launch
            val siblings = pageBlocks
                .filter { it.parentId == currentBlock.parentId }
                .sortedBy { it.position }

            val currentIndex = siblings.indexOfFirst { it.uuid == currentBlock.uuid }

            if (currentIndex > 0) {
                val prevBlock = siblings[currentIndex - 1]
                blockRepository.mergeBlocks(prevBlock.uuid, blockUuid, "").onSuccess {
                    requestEditBlock(prevBlock.uuid, prevBlock.content.length)
                }
            }
        }
    }

    fun handleBackspace(blockUuid: String) {
        scope.launch {
            val currentBlock = blockRepository.getBlockByUuid(blockUuid).first().getOrNull() ?: return@launch

            // Get siblings including current block
            val pageBlocks = blockRepository.getBlocksForPage(currentBlock.pageId).first().getOrNull() ?: return@launch
            val siblings = pageBlocks
                .filter { it.parentId == currentBlock.parentId }
                .sortedBy { it.position }

            val currentIndex = siblings.indexOfFirst { it.uuid == currentBlock.uuid }

            if (currentIndex > 0) {
                // Merge with previous sibling
                val prevBlock = siblings[currentIndex - 1]
                blockRepository.mergeBlocks(prevBlock.uuid, blockUuid, "").onSuccess {
                    requestEditBlock(prevBlock.uuid, prevBlock.content.length)
                }
            } else if (currentBlock.parentId != null) {
                // At start of children list, move to parent
                val parent = pageBlocks.find { it.id == currentBlock.parentId }
                if (parent != null) {
                    // If current block is empty, just delete it and focus parent
                    if (currentBlock.content.isEmpty()) {
                        blockRepository.deleteBlock(blockUuid)
                        requestEditBlock(parent.uuid, parent.content.length)
                    } else {
                        // Otherwise merge with parent
                        blockRepository.mergeBlocks(parent.uuid, blockUuid, "").onSuccess {
                            requestEditBlock(parent.uuid, parent.content.length)
                        }
                    }
                }
            } else {
                // Root block at position 0. Just delete if empty.
                if (currentBlock.content.isEmpty() && siblings.size > 1) {
                    val nextBlock = siblings[1]
                    blockRepository.deleteBlock(blockUuid)
                    requestEditBlock(nextBlock.uuid, 0)
                }
            }
        }
    }

    fun toggleBlockCollapse(blockId: Long) {
        _uiState.update { state ->
            val newCollapsed = if (blockId in state.collapsedBlockIds) {
                state.collapsedBlockIds - blockId
            } else {
                state.collapsedBlockIds + blockId
            }
            state.copy(collapsedBlockIds = newCollapsed)
        }
    }

    fun focusPreviousBlock(blockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch
            
            val visibleBlocks = getVisibleBlocksForPage(currentBlock.pageId)
            val currentIndex = visibleBlocks.indexOfFirst { it.uuid == blockUuid }
            
            if (currentIndex > 0) {
                val prevBlock = visibleBlocks[currentIndex - 1]
                requestEditBlock(prevBlock.uuid, prevBlock.content.length) // Focus end
            }
        }
    }

    fun focusNextBlock(blockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch
            
            val visibleBlocks = getVisibleBlocksForPage(currentBlock.pageId)
            val currentIndex = visibleBlocks.indexOfFirst { it.uuid == blockUuid }
            
            if (currentIndex != -1 && currentIndex < visibleBlocks.size - 1) {
                val nextBlock = visibleBlocks[currentIndex + 1]
                requestEditBlock(nextBlock.uuid, 0) // Focus start
            }
        }
    }

    private fun getVisibleBlocksForPage(pageId: Long): List<Block> {
        val blocks = _uiState.value.blocks[pageId] ?: return emptyList()
        val sortedBlocks = BlockSorter.sort(blocks)
        
        val collapsedIds = _uiState.value.collapsedBlockIds
        if (collapsedIds.isEmpty()) return sortedBlocks
        
        val childrenByParent = blocks.groupBy { it.parentId }
        
        fun getDescendantIds(blockId: Long): Set<Long> {
            val descendants = mutableSetOf<Long>()
            val queue = ArrayDeque<Long>()
            queue.add(blockId)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                childrenByParent[current]?.forEach { child ->
                    descendants.add(child.id)
                    queue.add(child.id)
                }
            }
            return descendants
        }
        
        val hiddenIds = collapsedIds.flatMap { getDescendantIds(it) }.toSet()
        
        return sortedBlocks.filter { it.id !in hiddenIds }
    }
}

data class JournalsUiState(
    val pages: List<Page> = emptyList(),
    val blocks: Map<Long, List<Block>> = emptyMap(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val loadingPageIds: Set<Long> = emptySet(),
    val editingBlockId: String? = null,
    val editingCursorIndex: Int? = null,
    val collapsedBlockIds: Set<Long> = emptySet()
)
