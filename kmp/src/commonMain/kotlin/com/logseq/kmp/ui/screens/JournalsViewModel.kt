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
    
    fun updateBlockContent(blockUuid: String, newContent: String) {
        scope.launch {
            val blockResult = blockRepository.getBlockByUuid(blockUuid).first()
            val block = blockResult.getOrNull() ?: return@launch
            
            val updatedBlock = block.copy(content = newContent)
            blockRepository.saveBlock(updatedBlock)
            
            // Refresh blocks for the page
            val pageBlocksResult = blockRepository.getBlocksForPage(block.pageId).first()
            val pageBlocks = pageBlocksResult.getOrNull() ?: return@launch
            
            _uiState.update { state ->
                val newBlocks = state.blocks.toMutableMap()
                newBlocks[block.pageId] = pageBlocks
                state.copy(blocks = newBlocks)
            }
        }
    }
    
    fun indentBlock(blockUuid: String) {
        scope.launch {
            blockRepository.indentBlock(blockUuid)
            refreshBlocksForBlock(blockUuid)
        }
    }

    fun outdentBlock(blockUuid: String) {
        scope.launch {
            blockRepository.outdentBlock(blockUuid)
            refreshBlocksForBlock(blockUuid)
        }
    }

    fun moveBlockUp(blockUuid: String) {
        scope.launch {
            blockRepository.moveBlockUp(blockUuid)
            refreshBlocksForBlock(blockUuid)
        }
    }

    fun moveBlockDown(blockUuid: String) {
        scope.launch {
            blockRepository.moveBlockDown(blockUuid)
            refreshBlocksForBlock(blockUuid)
        }
    }
    
    private suspend fun refreshBlocksForBlock(blockUuid: String) {
        val blockResult = blockRepository.getBlockByUuid(blockUuid).first()
        val block = blockResult.getOrNull() ?: return
        
        val pageBlocksResult = blockRepository.getBlocksForPage(block.pageId).first()
        val pageBlocks = pageBlocksResult.getOrNull() ?: return
        
        _uiState.update { state ->
            val newBlocks = state.blocks.toMutableMap()
            newBlocks[block.pageId] = pageBlocks
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
}

data class JournalsUiState(
    val pages: List<Page> = emptyList(),
    val blocks: Map<Long, List<Block>> = emptyMap(),
    val loadingPageIds: Set<Long> = emptySet()
)
