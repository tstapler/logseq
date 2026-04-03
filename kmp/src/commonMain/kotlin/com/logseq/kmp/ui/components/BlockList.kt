package com.logseq.kmp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.logseq.kmp.model.Block
import com.logseq.kmp.ui.screens.SearchResultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Renders a list of blocks with proper hierarchy, supporting collapse/expand.
 * Updated to use UUID-native storage.
 */
@Composable
fun BlockList(
    blocks: List<Block>,
    isDebugMode: Boolean = false,
    editingBlockUuid: String?,
    collapsedBlocks: Set<String> = emptySet(),
    onStartEditing: (String) -> Unit,
    onStopEditing: () -> Unit,
    onContentChange: (String, String, Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onNewBlock: (String) -> Unit,
    onSplitBlock: (String, Int) -> Unit,
    onMergeBlock: (String) -> Unit = {},
    editingCursorIndex: Int? = null,
    onBackspace: (String) -> Unit = {},
    onLoadContent: (String) -> Unit = {},
    onToggleCollapse: (String) -> Unit = {},
    onIndent: (String) -> Unit = {},
    onOutdent: (String) -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {},
    onFocusUp: (String) -> Unit = {},
    onFocusDown: (String) -> Unit = {},
    onResolveContent: suspend (String) -> String? = { null },
    onSearchPages: (String) -> Flow<List<SearchResultItem>> = { emptyFlow() },
    modifier: Modifier = Modifier
) {
    // Build a map of parent UUID to children for quick lookup
    val childrenByParent = remember(blocks) {
        blocks.groupBy { it.parentUuid }
    }

    // Get UUIDs of blocks that have children
    val blocksWithChildren = remember(blocks) {
        blocks.mapNotNull { it.parentUuid }.toSet()
    }

    // Get all descendant UUIDs of a block (for hiding when collapsed)
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

    // Calculate which blocks should be hidden due to collapsed ancestors
    val hiddenBlocks = remember(blocks, collapsedBlocks) {
        collapsedBlocks.flatMap { getDescendantUuids(it) }.toSet()
    }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            // Only show if not hidden by a collapsed ancestor
            if (block.uuid !in hiddenBlocks) {
                val hasChildren = block.uuid in blocksWithChildren
                val isCollapsed = block.uuid in collapsedBlocks

                BlockRenderer(
                    block = block,
                    isDebugMode = isDebugMode,
                    isEditing = editingBlockUuid == block.uuid,
                    hasChildren = hasChildren,
                    isCollapsed = isCollapsed,
                    onStartEditing = { onStartEditing(block.uuid) },
                    onStopEditing = onStopEditing,
                    onContentChange = { newContent, version -> onContentChange(block.uuid, newContent, version) },
                    onLinkClick = onLinkClick,
                    onNewBlock = onNewBlock,
                    onSplitBlock = onSplitBlock,
                    onMergeBlock = onMergeBlock,
                    initialCursorPosition = if (editingBlockUuid == block.uuid) editingCursorIndex else null,
                    onBackspace = { onBackspace(block.uuid) },
                    onLoadContent = { onLoadContent(block.pageUuid) },
                    onToggleCollapse = { onToggleCollapse(block.uuid) },
                    onIndent = { onIndent(block.uuid) },
                    onOutdent = { onOutdent(block.uuid) },
                    onMoveUp = { onMoveUp(block.uuid) },
                    onMoveDown = { onMoveDown(block.uuid) },
                    onFocusUp = { onFocusUp(block.uuid) },
                    onFocusDown = { onFocusDown(block.uuid) },
                    onResolveContent = onResolveContent,
                    onSearchPages = onSearchPages
                )
            }
        }
    }
}
