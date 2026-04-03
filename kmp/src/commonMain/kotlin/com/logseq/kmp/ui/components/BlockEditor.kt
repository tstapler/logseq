package com.logseq.kmp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.logseq.kmp.ui.screens.SearchResultItem

/**
 * The editable text field for a block in edit mode, including keyboard
 * handlers and autocomplete trigger detection.
 */
@Composable
internal fun BlockEditor(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    isEditing: Boolean,
    hasFocused: Boolean,
    onHasFocusedChange: (Boolean) -> Unit,
    blockUuid: String,
    autocompleteState: AutocompleteState?,
    onAutocompleteStateChange: (AutocompleteState?) -> Unit,
    searchResults: List<SearchResultItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    localVersion: Long,
    onLocalVersionIncrement: () -> Long,
    onContentChange: (String, Long) -> Unit,
    onStopEditing: () -> Unit,
    onNewBlock: (String) -> Unit,
    onSplitBlock: (String, Int) -> Unit,
    onMergeBlock: (String) -> Unit,
    onBackspace: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onFocusUp: () -> Unit,
    onFocusDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val oldText = textFieldValue.text
            onTextFieldValueChange(newValue)
            if (newValue.text != oldText) {
                val newVersion = onLocalVersionIncrement()
                onContentChange(newValue.text, newVersion)
            }

            // Autocomplete trigger detection
            val cursor = newValue.selection.min
            val textBeforeCursor = newValue.text.take(cursor)
            val match = Regex("\\[\\[([^\\]]*)$").find(textBeforeCursor)

            if (match != null) {
                val query = match.groupValues[1]
                val safeCursor = cursor.coerceIn(0, textLayoutResult?.layoutInput?.text?.length ?: 0)
                val rect = if (textLayoutResult != null && safeCursor <= textLayoutResult!!.layoutInput.text.length) {
                    try {
                        textLayoutResult?.getCursorRect(safeCursor)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                if (rect != null) {
                    onAutocompleteStateChange(AutocompleteState(query, rect))
                } else {
                    onAutocompleteStateChange(null)
                }
            } else {
                onAutocompleteStateChange(null)
            }
        },
        onTextLayout = { textLayoutResult = it },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onBackground
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                handleKeyEvent(
                    event = event,
                    textFieldValue = textFieldValue,
                    onTextFieldValueChange = onTextFieldValueChange,
                    textLayoutResult = textLayoutResult,
                    autocompleteState = autocompleteState,
                    onAutocompleteStateChange = onAutocompleteStateChange,
                    searchResults = searchResults,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = onSelectedIndexChange,
                    localVersion = localVersion,
                    onLocalVersionIncrement = onLocalVersionIncrement,
                    onContentChange = onContentChange,
                    blockUuid = blockUuid,
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
                )
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onHasFocusedChange(true)
                }
                if (!focusState.isFocused && isEditing && hasFocused) {
                    onStopEditing()
                }
            },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            ) {
                innerTextField()
            }
        }
    )
}

/**
 * Handles all keyboard events for the block editor, including autocomplete
 * navigation and standard block operations (Enter, Backspace, Tab, arrows).
 */
private fun handleKeyEvent(
    event: KeyEvent,
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    textLayoutResult: TextLayoutResult?,
    autocompleteState: AutocompleteState?,
    onAutocompleteStateChange: (AutocompleteState?) -> Unit,
    searchResults: List<SearchResultItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    localVersion: Long,
    onLocalVersionIncrement: () -> Long,
    onContentChange: (String, Long) -> Unit,
    blockUuid: String,
    onNewBlock: (String) -> Unit,
    onSplitBlock: (String, Int) -> Unit,
    onMergeBlock: (String) -> Unit,
    onBackspace: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onFocusUp: () -> Unit,
    onFocusDown: () -> Unit,
): Boolean {
    // Autocomplete keyboard navigation
    if (autocompleteState != null && searchResults.isNotEmpty()) {
        if (event.type == KeyEventType.KeyDown) {
            return when (event.key) {
                Key.DirectionUp -> {
                    onSelectedIndexChange((selectedIndex - 1 + searchResults.size) % searchResults.size)
                    true
                }
                Key.DirectionDown -> {
                    onSelectedIndexChange((selectedIndex + 1) % searchResults.size)
                    true
                }
                Key.Enter -> {
                    applyAutocompleteSelection(
                        searchResults = searchResults,
                        selectedIndex = selectedIndex,
                        textFieldValue = textFieldValue,
                        onTextFieldValueChange = onTextFieldValueChange,
                        autocompleteState = autocompleteState,
                        onAutocompleteStateChange = onAutocompleteStateChange,
                        onLocalVersionIncrement = onLocalVersionIncrement,
                        onContentChange = onContentChange,
                    )
                    true
                }
                Key.Escape -> {
                    onAutocompleteStateChange(null)
                    true
                }
                else -> false
            }
        }
        return false
    }

    // Standard block keyboard shortcuts
    if (event.type == KeyEventType.KeyDown) {
        return when (event.key) {
            Key.Enter -> {
                if (event.isShiftPressed) {
                    false
                } else {
                    val selection = textFieldValue.selection
                    if (selection.collapsed && selection.start < textFieldValue.text.length) {
                        onSplitBlock(blockUuid, selection.start)
                    } else {
                        onNewBlock(blockUuid)
                    }
                    true
                }
            }
            Key.Backspace -> {
                if (textFieldValue.selection.collapsed && textFieldValue.selection.start == 0) {
                    if (textFieldValue.text.isEmpty()) {
                        onBackspace()
                    } else {
                        onMergeBlock(blockUuid)
                    }
                    true
                } else {
                    false
                }
            }
            Key.Tab -> {
                if (event.isShiftPressed) {
                    onOutdent()
                } else {
                    onIndent()
                }
                true
            }
            Key.DirectionUp -> {
                if (event.isAltPressed) {
                    onMoveUp()
                    true
                } else {
                    val selection = textFieldValue.selection
                    val layout = textLayoutResult
                    if (selection.collapsed && layout != null) {
                        val line = layout.getLineForOffset(selection.start)
                        if (line == 0) {
                            onFocusUp()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
            Key.DirectionDown -> {
                if (event.isAltPressed) {
                    onMoveDown()
                    true
                } else {
                    val selection = textFieldValue.selection
                    val layout = textLayoutResult
                    if (selection.collapsed && layout != null) {
                        val line = layout.getLineForOffset(selection.end)
                        if (line == layout.lineCount - 1) {
                            onFocusDown()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
            else -> false
        }
    }
    return false
}

/**
 * Applies the currently selected autocomplete item by replacing the
 * `[[query` text with `[[Page Name]]`.
 */
internal fun applyAutocompleteSelection(
    searchResults: List<SearchResultItem>,
    selectedIndex: Int,
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    autocompleteState: AutocompleteState,
    onAutocompleteStateChange: (AutocompleteState?) -> Unit,
    onLocalVersionIncrement: () -> Long,
    onContentChange: (String, Long) -> Unit,
) {
    val item = searchResults[selectedIndex]
    val pageName = when (item) {
        is SearchResultItem.PageItem -> item.page.name
        is SearchResultItem.AliasItem -> item.alias
        is SearchResultItem.CreatePageItem -> item.query
        else -> null
    }

    if (pageName != null) {
        val text = textFieldValue.text
        val cursor = textFieldValue.selection.min
        val query = autocompleteState.query
        val triggerLength = 2 // [[

        val startIndex = cursor - query.length - triggerLength
        if (startIndex >= 0) {
            val before = text.substring(0, startIndex)
            val after = text.substring(cursor)
            val replacement = "[[$pageName]]"
            val newText = before + replacement + after
            val newCursor = startIndex + replacement.length

            onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
            val newVersion = onLocalVersionIncrement()
            onContentChange(newText, newVersion)
            onAutocompleteStateChange(null)
        }
    }
}
