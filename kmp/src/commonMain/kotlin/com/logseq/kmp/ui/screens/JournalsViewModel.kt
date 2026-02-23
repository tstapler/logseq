package com.logseq.kmp.ui.screens

import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.SimplePageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.logseq.kmp.outliner.BlockSorter

class JournalsViewModel(
    private val pageRepository: SimplePageRepository,
    private val blockRepository: BlockRepository,
    private val graphLoader: GraphLoader,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(JournalsUiState())
    val uiState: StateFlow<JournalsUiState> = _uiState.asStateFlow()

    private var currentOffset = 0
    private val pageSize = 10
    private var isLoading = false
    private var hasMore = true

    init {
        // Observe the first page of journals continuously to handle initial loading and updates
        // This fixes the empty state on startup when GraphLoader hasn't finished yet
        scope.launch {
            pageRepository.getJournalPages(pageSize, 0).collect { result ->
                val latestFirstPage = result.getOrNull() ?: emptyList()
                
                // Load blocks for these pages
                loadBlocksForPages(latestFirstPage)
                
                // If we are currently empty and we got some data, populate the list
                // This happens when GraphLoader finishes loading Phase 1
                if (_uiState.value.pages.isEmpty() && latestFirstPage.isNotEmpty()) {
                    _uiState.update { it.copy(pages = latestFirstPage) }
                    currentOffset = latestFirstPage.size
                    hasMore = latestFirstPage.size >= pageSize
                } else if (latestFirstPage.isNotEmpty()) {
                    // Update existing pages if needed (e.g. refreshed content)
                    _uiState.update { it.copy(pages = latestFirstPage) }
                }
            }
        }
    }
    
    private suspend fun loadBlocksForPages(pages: List<Page>) {
        val newBlocks = _uiState.value.blocks.toMutableMap()
        
        pages.forEach { page ->
            val result = blockRepository.getBlocksForPage(page.id).first()
            val blocks = result.getOrNull() ?: emptyList()
            if (blocks.isNotEmpty()) {
                newBlocks[page.id] = blocks
            }
        }
        
        _uiState.update { it.copy(blocks = newBlocks) }
    }

    fun loadMore() {
        if (isLoading || !hasMore) return

        isLoading = true
        scope.launch {
            try {
                val result = pageRepository.getJournalPages(pageSize, currentOffset).first()
                val newPages = result.getOrNull() ?: emptyList()

                if (newPages.isEmpty()) {
                    hasMore = false
                } else {
                    loadBlocksForPages(newPages)
                    
                    _uiState.update { currentState ->
                        currentState.copy(
                            pages = currentState.pages + newPages
                        )
                    }
                    currentOffset += newPages.size
                    // If we got fewer pages than requested, we reached the end
                    if (newPages.size < pageSize) {
                        hasMore = false
                    }
                }
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
    
    fun refresh() {
        currentOffset = 0
        hasMore = true
        _uiState.update { it.copy(pages = emptyList(), blocks = emptyMap()) }
        loadMore()
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
                graphLoader.loadFullPage(pageId)
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
            
            // Refresh blocks for the page
            val pageBlocksResult = blockRepository.getBlocksForPage(currentBlock.pageId).first()
            val pageBlocks = pageBlocksResult.getOrNull() ?: return@launch
            
            _uiState.update { state ->
                val newBlocks = state.blocks.toMutableMap()
                newBlocks[currentBlock.pageId] = pageBlocks
                state.copy(blocks = newBlocks)
            }
            
            requestEditBlock(newBlock.uuid)
        }
    }

    fun splitBlock(blockUuid: String, cursorPosition: Int) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            val fullContent = currentBlock.content
            // Safety check
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
            
            // Refresh blocks for the page
            val pageBlocksResult = blockRepository.getBlocksForPage(currentBlock.pageId).first()
            val pageBlocks = pageBlocksResult.getOrNull() ?: return@launch
            
            _uiState.update { state ->
                val newBlocks = state.blocks.toMutableMap()
                newBlocks[currentBlock.pageId] = pageBlocks
                state.copy(blocks = newBlocks)
            }
            
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
            
            // Refresh blocks for the page
            val pageBlocksResult = blockRepository.getBlocksForPage(page.id).first()
            val pageBlocks = pageBlocksResult.getOrNull() ?: emptyList()
            
            _uiState.update { state ->
                val newBlocks = state.blocks.toMutableMap()
                newBlocks[page.id] = pageBlocks
                state.copy(blocks = newBlocks)
            }
            
            requestEditBlock(newBlock.uuid)
        }
    }

    fun mergeBlock(blockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            // Get ALL siblings including current block (getBlockSiblings excludes current)
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

                // Update subsequent siblings: fix both position AND leftId
                val subsequentSiblings = siblings.drop(currentIndex + 1)
                if (subsequentSiblings.isNotEmpty()) {
                    val updatedSubsequent = subsequentSiblings.mapIndexed { idx, block ->
                        block.copy(
                            position = currentIndex + idx,  // Positions continue from currentIndex
                            leftId = if (idx == 0) prevBlock.id else subsequentSiblings[idx - 1].id
                        )
                    }
                    blockRepository.saveBlocks(updatedSubsequent)
                }

                refreshBlocksForPage(prevBlock.uuid)
                requestEditBlock(prevBlock.uuid, mergePoint)
            }
        }
    }

    fun handleBackspace(blockUuid: String) {
        scope.launch {
            val currentBlockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val currentBlock = currentBlockResult.getOrNull() ?: return@launch

            // Get ALL siblings including current block (getBlockSiblings excludes current)
            val pageBlocksResult = blockRepository.getBlocksForPage(currentBlock.pageId).first()
            val allBlocks = pageBlocksResult.getOrNull() ?: return@launch
            val siblings = allBlocks
                .filter { it.parentId == currentBlock.parentId }
                .sortedBy { it.position }

            val currentIndex = siblings.indexOfFirst { it.uuid == currentBlock.uuid }

            if (currentIndex > 0) {
                // Move to previous sibling
                val previousBlock = siblings[currentIndex - 1]

                // Delete current empty block
                blockRepository.deleteBlock(blockUuid)

                // Update subsequent siblings: fix both position AND leftId
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

                refreshBlocksForPage(previousBlock.uuid)
                requestEditBlock(previousBlock.uuid, previousBlock.content.length)

            } else if (currentBlock.parentId != null) {
                // At start of children list, move to parent
                val parent = allBlocks.find { it.id == currentBlock.parentId }

                blockRepository.deleteBlock(blockUuid)

                // Update remaining siblings' positions and leftIds
                val remainingSiblings = siblings.drop(1)  // Skip current (index 0)
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
                    refreshBlocksForPage(parent.uuid)
                    requestEditBlock(parent.uuid, parent.content.length)
                }
            } else {
                // Root block at position 0. If there are other root blocks, delete this one.
                if (siblings.size > 1) {
                    val nextBlock = siblings[1]  // Next sibling (index 1)
                    blockRepository.deleteBlock(blockUuid)

                    // Update remaining siblings
                    val remainingSiblings = siblings.drop(1)
                    val updatedRemaining = remainingSiblings.mapIndexed { idx, block ->
                        block.copy(
                            position = idx,
                            leftId = if (idx == 0) null else remainingSiblings[idx - 1].id
                        )
                    }
                    blockRepository.saveBlocks(updatedRemaining)

                    refreshBlocksForPage(nextBlock.uuid)
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
    val loadingPageIds: Set<Long> = emptySet(),
    val editingBlockId: String? = null,
    val editingCursorIndex: Int? = null,
    val collapsedBlockIds: Set<Long> = emptySet()
)
