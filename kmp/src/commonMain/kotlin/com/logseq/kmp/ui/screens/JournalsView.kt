package com.logseq.kmp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.ui.components.BlockList
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
    onContentChange: (String, String, Page) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var editingBlockId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope() // For repository calls

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

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        items(
            items = uiState.pages,
            key = { page -> page.id }
        ) { page ->
            val blockList = uiState.blocks[page.id] ?: emptyList()
            
            JournalEntry(
                page = page,
                blocks = blockList,
                isDebugMode = isDebugMode,
                editingBlockId = editingBlockId,
                onStartEditing = { blockId -> editingBlockId = blockId },
                onStopEditing = { editingBlockId = null },
                onContentChange = { blockId, newContent ->
                    onContentChange(blockId, newContent, page)
                },
                onLinkClick = onLinkClick,
                onIndent = { blockUuid ->
                    scope.launch { viewModel.indentBlock(blockUuid) }
                },
                onOutdent = { blockUuid ->
                    scope.launch { viewModel.outdentBlock(blockUuid) }
                },
                onMoveUp = { blockUuid ->
                    scope.launch { viewModel.moveBlockUp(blockUuid) }
                },
                onMoveDown = { blockUuid ->
                    scope.launch { viewModel.moveBlockDown(blockUuid) }
                },
                onLoadContent = { pageId -> viewModel.loadPageContent(pageId) }
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
    onStartEditing: (String) -> Unit,
    onStopEditing: () -> Unit,
    onContentChange: (String, String) -> Unit,
    onLinkClick: (String) -> Unit,
    onIndent: (String) -> Unit,
    onOutdent: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onLoadContent: (Long) -> Unit,
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
            // Empty journal placeholder
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
            )
        } else {
            // Sort blocks hierarchically for display
            val sortedBlocks = remember(blocks) { 
                com.logseq.kmp.outliner.BlockSorter.sort(blocks)
            }
            
            BlockList(
                blocks = sortedBlocks,
                isDebugMode = isDebugMode,
                editingBlockId = editingBlockId,
                onStartEditing = onStartEditing,
                onStopEditing = onStopEditing,
                onContentChange = onContentChange,
                onLinkClick = onLinkClick,
                onIndent = onIndent,
                onOutdent = onOutdent,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onLoadContent = onLoadContent
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
