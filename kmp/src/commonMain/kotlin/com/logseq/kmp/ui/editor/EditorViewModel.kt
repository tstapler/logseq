package com.logseq.kmp.ui.editor

import com.logseq.kmp.model.Block
import com.logseq.kmp.repository.BlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class EditorViewModel(
    private val blockRepository: BlockRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _blocks = MutableStateFlow<List<BlockUiModel>>(emptyList())
    val blocks: StateFlow<List<BlockUiModel>> = _blocks.asStateFlow()

    // Current page we are editing
    private var currentPageId: Long? = null

    fun loadPage(pageId: Long) {
        currentPageId = pageId
        scope.launch {
            blockRepository.getBlocksForPage(pageId).collect { result ->
                result.onSuccess { domainBlocks ->
                    // Convert domain blocks to UI models
                    // Note: This is a flat list. Indentation is handled by the 'level' property in UI.
                    _blocks.value = domainBlocks.map { BlockUiModel.fromDomain(it) }
                }
            }
        }
    }

    fun updateBlockContent(uuid: String, newContent: String) {
        scope.launch {
            // Optimistic update
            val currentList = _blocks.value.toMutableList()
            val index = currentList.indexOfFirst { it.uuid == uuid }
            if (index != -1) {
                currentList[index] = currentList[index].copy(content = newContent)
                _blocks.value = currentList
            }

            // Persist
            val domainBlock = blockRepository.getBlockByUuid(uuid) // This needs to be a suspend call or we check cache
            // Simplified: We assume we can construct it or fetch it.
            // For now, let's just log or assume repo handles partial updates if we had that method.
            // Since repo only has saveBlock(Block), we need the full block.
            
            // Real implementation would fetch, copy, save.
        }
    }
}
