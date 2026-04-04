package com.logseq.kmp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.ui.components.BlockList
import com.logseq.kmp.ui.components.MobileBlockToolbar
import kotlinx.coroutines.launch

/**
 * Journals view that displays multiple journal entries with their content
 * in a scrollable list, similar to Logseq's journals page.
 */
@Composable
fun JournalsView(
    viewModel: JournalsViewModel,
    blockRepository: BlockRepository,
    isDebugMode: Boolean,
    onLinkClick: (String) -> Unit,
    onContentChange: (String, String, Long, Page) -> Unit,
    onSearchPages: (String) -> kotlinx.coroutines.flow.Flow<List<SearchResultItem>> = { kotlinx.coroutines.flow.emptyFlow() },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val editingBlockUuid = uiState.editingBlockUuid
    val editingCursorIndex = uiState.editingCursorIndex
    val collapsedBlockUuids = uiState.collapsedBlockUuids
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope() // For repository calls
    val focusManager = LocalFocusManager.current

    // Infinite scroll detection
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1

            lastVisibleItemIndex > (totalItemsNumber - 2)
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMore()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            items(
                items = uiState.pages,
                key = { page -> page.uuid }
            ) { page ->
                val blockList = uiState.blocks[page.uuid] ?: emptyList()
                
                JournalEntry(
                    page = page,
                    blocks = blockList,
                    isDebugMode = isDebugMode,
                    editingBlockId = editingBlockUuid,
                    editingCursorIndex = editingCursorIndex,
                    collapsedBlocks = collapsedBlockUuids,
                    onStartEditing = { blockId -> viewModel.requestEditBlock(blockId) },
                    onStopEditing = { viewModel.requestEditBlock(null) },
                    onContentChange = { blockId, newContent, version ->
                        viewModel.updateBlockContent(blockId, newContent, version)
                    },
                    onLinkClick = onLinkClick,
                    onNewBlock = { uuid -> viewModel.addNewBlock(uuid) },
                    onSplitBlock = { uuid, pos -> viewModel.splitBlock(uuid, pos) },
                    onMergeBlock = { uuid -> viewModel.mergeBlock(uuid) },
                    onIndent = { blockUuid ->
                        viewModel.indentBlock(blockUuid)
                    },
                    onOutdent = { blockUuid ->
                        viewModel.outdentBlock(blockUuid)
                    },
                    onMoveUp = { blockUuid ->
                        viewModel.moveBlockUp(blockUuid)
                    },
                    onMoveDown = { blockUuid ->
                        viewModel.moveBlockDown(blockUuid)
                    },
                    onLoadContent = { pageUuid -> viewModel.loadPageContent(pageUuid) },
                    onBackspace = { blockUuid -> viewModel.handleBackspace(blockUuid) },
                    onAddBlockToPage = { pageUuid -> viewModel.addBlockToPage(pageUuid) },
                    onToggleCollapse = { blockId -> viewModel.toggleBlockCollapse(blockId) },
                    onFocusUp = { blockUuid -> viewModel.focusPreviousBlock(blockUuid) },
                    onFocusDown = { blockUuid -> viewModel.focusNextBlock(blockUuid) },
                    onSearchPages = onSearchPages
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            
            // Loading indicator at the bottom
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    // We could show a spinner here if we exposed isLoading from ViewModel
                }
            }
        }

        MobileBlockToolbar(
            editingBlockId = editingBlockUuid,
            onIndent = { blockId -> scope.launch { viewModel.indentBlock(blockId) } },
            onOutdent = { blockId -> scope.launch { viewModel.outdentBlock(blockId) } },
            onMoveUp = { blockId -> scope.launch { viewModel.moveBlockUp(blockId) } },
            onMoveDown = { blockId -> scope.launch { viewModel.moveBlockDown(blockId) } },
            onAddBlock = { blockId -> scope.launch { viewModel.addNewBlock(blockId) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        )
    }
}

/**
 * A single journal entry with its date header and blocks
 */
@Composable
private fun JournalEntry(
    page: Page,
    blocks: List<Block>, // Passed from ViewModel
    isDebugMode: Boolean,
    editingBlockId: String?,
    editingCursorIndex: Int?,
    collapsedBlocks: Set<String>,
    onStartEditing: (String) -> Unit,
    onStopEditing: () -> Unit,
    onContentChange: (String, String, Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onNewBlock: (String) -> Unit,
    onSplitBlock: (String, Int) -> Unit,
    onMergeBlock: (String) -> Unit,
    onIndent: (String) -> Unit,
    onOutdent: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onLoadContent: (String) -> Unit,
    onBackspace: (String) -> Unit,
    onAddBlockToPage: (String) -> Unit,
    onToggleCollapse: (String) -> Unit,
    onFocusUp: (String) -> Unit,
    onFocusDown: (String) -> Unit,
    onSearchPages: (String) -> kotlinx.coroutines.flow.Flow<List<SearchResultItem>> = { kotlinx.coroutines.flow.emptyFlow() },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Journal date header (formatted nicely)
        Text(
            text = formatJournalDate(page.name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Blocks content
        if (blocks.isEmpty()) {
            // Empty journal placeholder - Click to add first block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddBlockToPage(page.uuid) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Click to write...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                )
            }
        } else {
            // Sort blocks hierarchically for display
            val sortedBlocks = remember(blocks) { 
                com.logseq.kmp.outliner.BlockSorter.sort(blocks)
            }
            
            BlockList(
                blocks = sortedBlocks,
                isDebugMode = isDebugMode,
                editingBlockId = editingBlockId,
                editingCursorIndex = editingCursorIndex,
                collapsedBlocks = collapsedBlocks,
                onStartEditing = onStartEditing,
                onStopEditing = onStopEditing,
                onContentChange = onContentChange,
                onLinkClick = onLinkClick,
                onNewBlock = onNewBlock,
                onSplitBlock = onSplitBlock,
                onMergeBlock = onMergeBlock,
                onIndent = onIndent,
                onOutdent = onOutdent,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onLoadContent = onLoadContent,
                onBackspace = onBackspace,
                onToggleCollapse = onToggleCollapse,
                onFocusUp = onFocusUp,
                onFocusDown = onFocusDown,
                onSearchPages = onSearchPages
            )
            
            // Clickable area below blocks to append new block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp) // Generous touch target
                    .clickable { onAddBlockToPage(page.uuid) }
            )
        }
    }
}

/**
 * Format journal page name to a nicer date format
 * Input: "2026_01_21" or "2026-01-21"
 * Output: "2026-01-21"
 */
private fun formatJournalDate(pageName: String): String {
    // Replace underscores with dashes for consistent formatting
    return pageName.replace("_", "-")
}
