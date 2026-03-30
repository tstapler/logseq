package com.logseq.kmp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.db.GraphWriter
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.outliner.BlockSorter
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.PageRepository
import com.logseq.kmp.ui.LogseqViewModel
import com.logseq.kmp.ui.components.BlockList
import com.logseq.kmp.ui.components.MobileBlockToolbar
import com.logseq.kmp.ui.components.ReferencesPanel
import com.logseq.kmp.ui.i18n.t
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Page view screen.
 * Updated to use UUID-native storage.
 */
@Composable
fun PageView(
    page: Page,
    blockRepository: BlockRepository,
    pageRepository: PageRepository,
    graphWriter: GraphWriter,
    graphLoader: GraphLoader,
    currentGraphPath: String,
    onToggleFavorite: (Page) -> Unit,
    onRefresh: () -> Unit,
    onLinkClick: (String) -> Unit,
    viewModel: LogseqViewModel,
    isDebugMode: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Local state for editing
    var editingBlockUuid by remember { mutableStateOf<String?>(null) }
    var editingCursorIndex by remember { mutableStateOf<Int?>(null) }
    var collapsedBlockUuids by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load blocks for this page
    var blocks by remember { mutableStateOf<List<Block>>(emptyList()) }

    // Trigger full load if page content hasn't been loaded yet OR if blocks have unloaded content
    LaunchedEffect(page.uuid, page.isContentLoaded, blocks) {
        val hasUnloadedBlocks = blocks.isNotEmpty() && blocks.any { !it.isLoaded }
        if (!page.isContentLoaded || hasUnloadedBlocks) {
            graphLoader.loadFullPage(page.uuid)
        }
    }

    // Collect blocks from repository
    LaunchedEffect(page.uuid) {
        blockRepository.getBlocksForPage(page.uuid).collect { result ->
            result.onSuccess { loadedBlocks ->
                blocks = loadedBlocks
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
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
            // Page header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = page.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { onToggleFavorite(page) }) {
                        Icon(
                            imageVector = if (page.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (page.isFavorite) "Unfavorite" else "Favorite",
                            tint = if (page.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (page.namespace != null) {
                    Text(
                        text = "${t("common.namespace")}: ${page.namespace}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Blocks content
            item {
                if (blocks.isEmpty()) {
                    // Empty page placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.addBlockToPage(page.uuid) }
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
                    val sortedBlocks = remember(blocks) { BlockSorter.sort(blocks) }

                    BlockList(
                        blocks = sortedBlocks,
                        isDebugMode = isDebugMode,
                        editingBlockUuid = editingBlockUuid,
                        editingCursorIndex = editingCursorIndex,
                        collapsedBlocks = collapsedBlockUuids,
                        onStartEditing = { uuid -> editingBlockUuid = uuid },
                        onStopEditing = { editingBlockUuid = null },
                        onContentChange = { blockUuid, newContent, version ->
                            viewModel.saveBlockContent(blockUuid, newContent, version, page)
                        },
                        onLinkClick = onLinkClick,
                        onNewBlock = { uuid -> viewModel.addNewBlock(uuid) },
                        onSplitBlock = { uuid, pos -> viewModel.splitBlock(uuid, pos) },
                        onMergeBlock = { uuid -> viewModel.mergeBlock(uuid) },
                        onIndent = { blockUuid -> viewModel.indentBlock(blockUuid) },
                        onOutdent = { blockUuid -> viewModel.outdentBlock(blockUuid) },
                        onMoveUp = { blockUuid -> viewModel.moveBlockUp(blockUuid) },
                        onMoveDown = { blockUuid -> viewModel.moveBlockDown(blockUuid) },
                        onLoadContent = { pageUuid -> scope.launch { graphLoader.loadFullPage(pageUuid) } },

                        onBackspace = { blockUuid -> viewModel.handleBackspace(blockUuid) },
                        onToggleCollapse = { blockUuid ->
                            collapsedBlockUuids = if (collapsedBlockUuids.contains(blockUuid)) {
                                collapsedBlockUuids - blockUuid
                            } else {
                                collapsedBlockUuids + blockUuid
                            }
                        },
                        onFocusUp = { blockUuid -> viewModel.focusPreviousBlock(blockUuid) },
                        onFocusDown = { blockUuid -> viewModel.focusNextBlock(blockUuid) },
                        onResolveContent = { uuid -> viewModel.getBlockContent(uuid) },
                        onSearchPages = { query -> viewModel.searchPages(query) }
                    )

                    // Clickable area below blocks to append new block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { viewModel.addBlockToPage(page.uuid) }
                    )
                }
            }

            // References section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                ReferencesPanel(
                    page = page,
                    blockRepository = blockRepository,
                    pageRepository = pageRepository,
                    onLinkClick = onLinkClick
                )
            }
        }

        MobileBlockToolbar(
            editingBlockId = editingBlockUuid,
            onIndent = { blockUuid -> scope.launch { viewModel.indentBlock(blockUuid) } },
            onOutdent = { blockUuid -> scope.launch { viewModel.outdentBlock(blockUuid) } },
            onMoveUp = { blockUuid -> scope.launch { viewModel.moveBlockUp(blockUuid) } },
            onMoveDown = { blockUuid -> scope.launch { viewModel.moveBlockDown(blockUuid) } },
            onAddBlock = { blockUuid -> scope.launch { viewModel.addNewBlock(blockUuid) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        )
    }
}
