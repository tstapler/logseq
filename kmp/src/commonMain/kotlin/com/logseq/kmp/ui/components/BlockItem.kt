package com.logseq.kmp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.logseq.kmp.model.Block
import com.logseq.kmp.ui.screens.SearchResultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Orchestrator composable that assembles a single block row from
 * [BlockGutter], [BlockEditor] / [BlockViewer], and the autocomplete menu.
 * This is the main composition unit for a single block.
 */
@Composable
internal fun BlockItem(
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
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember(block.uuid) {
        mutableStateOf(TextFieldValue(text = block.content))
    }

    var localVersion by remember(block.uuid) { mutableLongStateOf(block.version) }

    // Autocomplete State
    var autocompleteState by remember { mutableStateOf<AutocompleteState?>(null) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }

    // Fetch autocomplete results
    LaunchedEffect(autocompleteState?.query) {
        val query = autocompleteState?.query
        if (query != null) {
            onSearchPages(query).collect { results ->
                searchResults = results
                selectedIndex = 0
            }
        } else {
            searchResults = emptyList()
        }
    }

    // Handle external updates to block content (e.g. undo/redo, sync)
    // Only update if the incoming version is newer than our local version
    LaunchedEffect(block.version) {
        if (block.version > localVersion) {
            val newSelection = if (textFieldValue.selection.max <= block.content.length) {
                textFieldValue.selection
            } else {
                TextRange(block.content.length)
            }
            textFieldValue = TextFieldValue(text = block.content, selection = newSelection)
            localVersion = block.version
        }
    }

    var hasFocused by remember { mutableStateOf(false) }

    // Resolved block references content
    val resolvedRefs = remember { mutableStateMapOf<String, String>() }

    // Scan for block refs and fetch content
    LaunchedEffect(block.content) {
        MarkdownPatterns.blockRefPattern.findAll(block.content).forEach { match ->
            val refUuid = match.groupValues[1]
            if (!resolvedRefs.containsKey(refUuid)) {
                val content = onResolveContent(refUuid)
                if (content != null) {
                    resolvedRefs[refUuid] = content
                }
            }
        }
    }

    // Request focus when entering edit mode
    LaunchedEffect(isEditing) {
        if (isEditing) {
            if (initialCursorPosition != null) {
                textFieldValue = textFieldValue.copy(selection = TextRange(initialCursorPosition))
            }
            focusRequester.requestFocus()
        } else {
            hasFocused = false
            autocompleteState = null // Clear autocomplete when exiting edit mode
        }
    }

    // Trigger load if not loaded
    LaunchedEffect(block.isLoaded) {
        if (!block.isLoaded) {
            onLoadContent()
        }
    }

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = (block.level * 24).dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Gutter: drag handle, collapse toggle, bullet, debug info
            BlockGutter(
                level = block.level,
                isDebugMode = isDebugMode,
                hasChildren = hasChildren,
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
            )

            if (!block.isLoaded) {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else if (isEditing) {
                // Edit mode
                BlockEditor(
                    textFieldValue = textFieldValue,
                    onTextFieldValueChange = { textFieldValue = it },
                    focusRequester = focusRequester,
                    isEditing = isEditing,
                    hasFocused = hasFocused,
                    onHasFocusedChange = { hasFocused = it },
                    blockUuid = block.uuid,
                    autocompleteState = autocompleteState,
                    onAutocompleteStateChange = { autocompleteState = it },
                    searchResults = searchResults,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                    localVersion = localVersion,
                    onLocalVersionIncrement = { ++localVersion },
                    onContentChange = onContentChange,
                    onStopEditing = onStopEditing,
                    onNewBlock = onNewBlock,
                    onSplitBlock = onSplitBlock,
                    onMergeBlock = onMergeBlock,
                    onBackspace = onBackspace,
                    onIndent = onIndent,
                    onOutdent = onOutdent,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onFocusUp = onFocusUp,
                    onFocusDown = onFocusDown,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // View mode
                BlockViewer(
                    content = block.content,
                    textColor = textColor,
                    linkColor = linkColor,
                    resolvedRefs = resolvedRefs,
                    onLinkClick = onLinkClick,
                    onStartEditing = onStartEditing,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Render Autocomplete Menu
        if (autocompleteState != null && searchResults.isNotEmpty()) {
            AutocompleteMenu(
                items = searchResults,
                selectedIndex = selectedIndex,
                onItemSelected = { item ->
                    applyAutocompleteSelection(
                        searchResults = searchResults,
                        selectedIndex = searchResults.indexOf(item).coerceAtLeast(0),
                        textFieldValue = textFieldValue,
                        onTextFieldValueChange = { textFieldValue = it },
                        autocompleteState = autocompleteState!!,
                        onAutocompleteStateChange = { autocompleteState = it },
                        onLocalVersionIncrement = { ++localVersion },
                        onContentChange = onContentChange,
                    )
                },
                onDismiss = { autocompleteState = null },
                cursorRect = autocompleteState?.cursorRect
            )
        }
    }
}
