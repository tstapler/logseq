package com.logseq.kmp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.logseq.kmp.model.Block
import com.logseq.kmp.ui.screens.SearchResultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * State holder for the autocomplete popup, tracking the current query
 * and the cursor rectangle for positioning.
 */
data class AutocompleteState(
    val query: String,
    val cursorRect: Rect
)

/**
 * Public entry point for rendering a single block.
 *
 * Delegates to [BlockItem] which orchestrates [BlockGutter],
 * [BlockEditor] / [BlockViewer], and the autocomplete menu.
 *
 * This function preserves the original public API so that all existing
 * callers (e.g. [BlockList]) continue to work without changes.
 */
@Composable
fun BlockRenderer(
    block: Block,
    isDebugMode: Boolean = false,
    isEditing: Boolean,
    hasChildren: Boolean = false,
    isCollapsed: Boolean = false,
    textColor: Color = Color.Unspecified,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    onContentChange: (String, Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onNewBlock: (String) -> Unit,
    onSplitBlock: (String, Int) -> Unit,
    onMergeBlock: (String) -> Unit = {},
    initialCursorPosition: Int? = null,
    onBackspace: () -> Unit = {},
    onLoadContent: () -> Unit = {},
    onToggleCollapse: () -> Unit = {},
    onIndent: () -> Unit = {},
    onOutdent: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onFocusUp: () -> Unit = {},
    onFocusDown: () -> Unit = {},
    onResolveContent: suspend (String) -> String? = { null },
    onSearchPages: (String) -> Flow<List<SearchResultItem>> = { emptyFlow() },
    modifier: Modifier = Modifier
) {
    BlockItem(
        block = block,
        isDebugMode = isDebugMode,
        isEditing = isEditing,
        hasChildren = hasChildren,
        isCollapsed = isCollapsed,
        textColor = textColor,
        linkColor = linkColor,
        onStartEditing = onStartEditing,
        onStopEditing = onStopEditing,
        onContentChange = onContentChange,
        onLinkClick = onLinkClick,
        onNewBlock = onNewBlock,
        onSplitBlock = onSplitBlock,
        onMergeBlock = onMergeBlock,
        initialCursorPosition = initialCursorPosition,
        onBackspace = onBackspace,
        onLoadContent = onLoadContent,
        onToggleCollapse = onToggleCollapse,
        onIndent = onIndent,
        onOutdent = onOutdent,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onFocusUp = onFocusUp,
        onFocusDown = onFocusDown,
        onResolveContent = onResolveContent,
        onSearchPages = onSearchPages,
        modifier = modifier
    )
}
