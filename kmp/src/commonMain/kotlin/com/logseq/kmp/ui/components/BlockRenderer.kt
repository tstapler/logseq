package com.logseq.kmp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.*
import com.logseq.kmp.model.Block
import kotlin.math.abs

import androidx.compose.ui.geometry.Rect
import com.logseq.kmp.ui.screens.SearchResultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.logseq.kmp.editor.components.AutocompleteMenu

data class AutocompleteState(
    val query: String,
    val cursorRect: Rect
)

/**
 * Markdown patterns for parsing various markdown syntax elements
 */
object MarkdownPatterns {
    val boldPattern = Regex("""(\*\*|__)(.+?)\1""")
    val italicPattern = Regex("""(?<!\*)(\*|_)(?!\1)(.+?)\1""")
    val codePattern = Regex("""`([^`]+)`""")
    val strikethroughPattern = Regex("""~~(.+?)~~""")
    val linkPattern = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
    val imagePattern = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")
    val wikiLinkPattern = Regex("""\[\[([^\]]+)\]\]""")
    val blockRefPattern = Regex("""\(\(([^)]+)\)\)""")
    val tagPattern = Regex("""#([^\s#.,!\[\]()]+)""")
    // Auto-detect plain URLs - matches http:// or https:// followed by non-whitespace
    val urlPattern = Regex("""https?://[^\s<>"]+""")
}

const val WIKI_LINK_TAG = "WIKI_LINK"
const val BLOCK_REF_TAG = "BLOCK_REF"
const val TAG_TAG = "TAG"

/**
 * Renders a block with support for:
 * - View mode (default) with clickable wiki links
 * - Edit mode (when clicked)
 * - Proper indentation based on block level
 * - Collapse/expand indicator for blocks with children
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
    onContentChange: (String) -> Unit,
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
    // Only update if content has actually changed externally to avoid resetting cursor
    LaunchedEffect(block.content) {
        if (textFieldValue.text != block.content) {
            val newSelection = if (textFieldValue.selection.max <= block.content.length) {
                textFieldValue.selection
            } else {
                TextRange(block.content.length)
            }
            textFieldValue = TextFieldValue(text = block.content, selection = newSelection)
        }
    }
    
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    var hasFocused by remember { mutableStateOf(false) }

    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

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

    val density = LocalDensity.current
    val dragThreshold = remember(density) { with(density) { 48.dp.toPx() } }

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
                .padding(start = (block.level * 24).dp, top = 2.dp, bottom = 2.dp)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = offsetY
                },
            verticalAlignment = Alignment.Top
        ) {
            // Drag Handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to move",
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 4.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                offsetY = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                offsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetY += dragAmount.y
                                if (abs(offsetY) > dragThreshold) {
                                    if (offsetY > 0) onMoveDown() else onMoveUp()
                                    offsetY = 0f
                                }
                            }
                        )
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            // Collapse/expand indicator (caret)
            if (hasChildren) {
                Icon(
                    imageVector = if (isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onToggleCollapse() }
                        .padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.width(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Bullet point (Always shown)
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
            )
            
            // DEBUG: Show level to diagnose indentation issues
            if (isDebugMode) {
                Text(
                   text = "L${block.level}",
                   style = MaterialTheme.typography.labelSmall,
                   color = Color.Red
                )
            }

            if (!block.isLoaded) {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else if (isEditing) {
                // Edit mode
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onContentChange(newValue.text)

                        // Trigger detection logic
                        val cursor = newValue.selection.min
                        val textBeforeCursor = newValue.text.take(cursor)
                        val match = Regex("\\[\\[([^\\]]*)$").find(textBeforeCursor)

                        if (match != null) {
                            val query = match.groupValues[1]
                            val rect = textLayoutResult?.getCursorRect(cursor)
                            if (rect != null) {
                                autocompleteState = AutocompleteState(query, rect)
                            } else {
                                autocompleteState = null
                            }
                        } else {
                            autocompleteState = null
                        }
                    },
                    onTextLayout = { textLayoutResult = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (autocompleteState != null && searchResults.isNotEmpty()) {
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionUp -> {
                                            selectedIndex = (selectedIndex - 1 + searchResults.size) % searchResults.size
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            selectedIndex = (selectedIndex + 1) % searchResults.size
                                            true
                                        }
                                        Key.Enter -> {
                                            val item = searchResults[selectedIndex]
                                            val pageName = when (item) {
                                                is SearchResultItem.PageItem -> item.page.name
                                                is SearchResultItem.CreatePageItem -> item.query
                                                else -> null
                                            }
                                            
                                            if (pageName != null) {
                                                // Replace query with [[Page Name]]
                                                // Query is matched from `[[`
                                                val text = textFieldValue.text
                                                val cursor = textFieldValue.selection.min
                                                val query = autocompleteState!!.query
                                                val triggerLength = 2 // [[
                                                
                                                val startIndex = cursor - query.length - triggerLength
                                                if (startIndex >= 0) {
                                                    val before = text.substring(0, startIndex)
                                                    val after = text.substring(cursor)
                                                    val replacement = "[[${pageName}]]"
                                                    val newText = before + replacement + after
                                                    val newCursor = startIndex + replacement.length
                                                    
                                                    textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                                                    onContentChange(newText)
                                                    autocompleteState = null
                                                }
                                            }
                                            true
                                        }
                                        Key.Escape -> {
                                            autocompleteState = null
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            } else if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.Enter -> {
                                        if (event.isShiftPressed) {
                                            false
                                        } else {
                                            val selection = textFieldValue.selection
                                            if (selection.collapsed && selection.start < textFieldValue.text.length) {
                                                onSplitBlock(block.uuid, selection.start)
                                            } else {
                                                onNewBlock(block.uuid)
                                            }
                                            true
                                        }
                                    }
                                    Key.Backspace -> {
                                        if (textFieldValue.selection.collapsed && textFieldValue.selection.start == 0) {
                                            if (textFieldValue.text.isEmpty()) {
                                                onBackspace()
                                            } else {
                                                onMergeBlock(block.uuid)
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
                            } else {
                                false
                            }
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                hasFocused = true
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
            } else {
                // View mode with wiki links
                // WikiLinkText handles both link clicks and regular text clicks internally
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                WikiLinkText(
                    text = block.content,
                    textColor = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onBackground,
                    linkColor = linkColor,
                    resolvedRefs = resolvedRefs,
                    onLinkClick = onLinkClick,
                    onUrlClick = { url ->
                        try {
                            uriHandler.openUri(url)
                        } catch (e: Exception) {
                            // Ignore if can't open URL
                        }
                    },
                    onClick = onStartEditing,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
        
        // Render Autocomplete Menu
        if (autocompleteState != null && searchResults.isNotEmpty()) {
            AutocompleteMenu(
                items = searchResults,
                selectedIndex = selectedIndex,
                onItemSelected = { item ->
                    val pageName = when (item) {
                        is SearchResultItem.PageItem -> item.page.name
                        is SearchResultItem.CreatePageItem -> item.query
                        else -> null
                    }
                    
                    if (pageName != null) {
                        val text = textFieldValue.text
                        val cursor = textFieldValue.selection.min
                        val query = autocompleteState!!.query
                        val triggerLength = 2 // [[
                        
                        val startIndex = cursor - query.length - triggerLength
                        if (startIndex >= 0) {
                            val before = text.substring(0, startIndex)
                            val after = text.substring(cursor)
                            val replacement = "[[${pageName}]]"
                            val newText = before + replacement + after
                            val newCursor = startIndex + replacement.length
                            
                            textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                            onContentChange(newText)
                            autocompleteState = null
                        }
                    }
                },
                onDismiss = { autocompleteState = null },
                cursorRect = autocompleteState?.cursorRect
            )
        }
    }
}

/**
 * Parses markdown and applies proper styling and annotations
 */
private fun parseMarkdownWithStyling(
    text: String,
    linkColor: Color,
    textColor: Color,
    resolvedRefs: Map<String, String> = emptyMap()
): AnnotatedString {
    // 1. Pre-process text to resolve block refs (expand them)
    val sb = StringBuilder()
    var lastIndex = 0
    val refMap = mutableMapOf<IntRange, String>() // Map range in SB to UUID (for styling)
    
    MarkdownPatterns.blockRefPattern.findAll(text).forEach { match ->
        // Append text before match
        sb.append(text.substring(lastIndex, match.range.first))
        
        val refUuid = match.groupValues[1]
        val content = resolvedRefs[refUuid] ?: "((...))" // Show placeholder if not resolved
        val start = sb.length
        sb.append(content)
        val end = sb.length
        
        refMap[start until end] = refUuid
        lastIndex = match.range.last + 1
    }
    sb.append(text.substring(lastIndex))
    val newText = sb.toString()

    // 2. Build AnnotatedString from newText
    val builder = AnnotatedString.Builder(newText)
    val textLength = newText.length

    // Apply base text color
    if (textColor != Color.Unspecified) {
        builder.addStyle(SpanStyle(color = textColor), 0, textLength)
    }
    
    // Apply styling for Block Refs
    refMap.forEach { (range, uuid) ->
        builder.addStringAnnotation(BLOCK_REF_TAG, uuid, range.first, range.last + 1)
        builder.addStyle(
            SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
                fontStyle = FontStyle.Italic,
                background = Color.Gray.copy(alpha = 0.05f)
            ),
            range.first, 
            range.last + 1
        )
    }

    // Helper for safe bounds
    fun safeRange(start: Int, end: Int): Pair<Int, Int> {
        val safeStart = start.coerceIn(0, textLength)
        val safeEnd = end.coerceIn(safeStart, textLength)
        return safeStart to safeEnd
    }

    // Apply styling for images
    MarkdownPatterns.imagePattern.findAll(newText).forEach { match ->
        val imageUrl = match.groupValues[2]
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStringAnnotation("image", imageUrl, safeStart, safeEnd)
            builder.addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                safeStart, safeEnd
            )
        }
    }

    // Apply styling for links
    val markdownLinkRanges = mutableListOf<IntRange>()
    MarkdownPatterns.linkPattern.findAll(newText).forEach { match ->
        val linkUrl = match.groupValues[2]
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            markdownLinkRanges.add(match.range)
            builder.addStringAnnotation("link", linkUrl, safeStart, safeEnd)
            builder.addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                safeStart, safeEnd
            )
        }
    }

    // Apply styling for plain URLs
    MarkdownPatterns.urlPattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        val isInside = markdownLinkRanges.any { it.contains(safeStart) }
        if (!isInside && safeStart < safeEnd) {
            builder.addStringAnnotation("url", match.value, safeStart, safeEnd)
            builder.addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for code
    MarkdownPatterns.codePattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.1f)),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for strikethrough
    MarkdownPatterns.strikethroughPattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for bold
    MarkdownPatterns.boldPattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for italic
    MarkdownPatterns.italicPattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStyle(
                SpanStyle(fontStyle = FontStyle.Italic),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for wiki links
    MarkdownPatterns.wikiLinkPattern.findAll(newText).forEach { match ->
        val linkText = match.groupValues[1]
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStringAnnotation(WIKI_LINK_TAG, linkText, safeStart, safeEnd)
            builder.addStyle(
                SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                safeStart, safeEnd
            )
        }
    }

    // Apply styling for tags
    MarkdownPatterns.tagPattern.findAll(newText).forEach { match ->
        val tagName = match.groupValues[1]
        // Skip if inside a wiki link range (simple collision check)
        val isInsideLink = MarkdownPatterns.wikiLinkPattern.findAll(newText).any { linkMatch ->
            match.range.first >= linkMatch.range.first && match.range.last <= linkMatch.range.last
        }
        
        if (!isInsideLink) {
            val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
            if (safeStart < safeEnd) {
                builder.addStringAnnotation(TAG_TAG, tagName, safeStart, safeEnd)
                builder.addStyle(
                    SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                    safeStart, safeEnd
                )
            }
        }
    }

    return builder.toAnnotatedString()
}

/**
 * Renders text with clickable wiki links [[Page Name]]
 */
@Composable
fun WikiLinkText(
    text: String,
    textColor: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    resolvedRefs: Map<String, String> = emptyMap(),
    onLinkClick: (String) -> Unit = {},
    onUrlClick: (String) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val annotatedString = remember(text, linkColor, textColor, resolvedRefs) {
        parseMarkdownWithStyling(text, linkColor, textColor, resolvedRefs)
    }

    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            val annotations = annotatedString.getStringAnnotations(start = offset, end = offset)
            
            // Priority: Wiki Link > Tag > Markdown Link > URL > Image > Default
            val wikiLink = annotations.firstOrNull { it.tag == WIKI_LINK_TAG }
            val tag = annotations.firstOrNull { it.tag == TAG_TAG }
            val link = annotations.firstOrNull { it.tag == "link" }
            val url = annotations.firstOrNull { it.tag == "url" }
            val image = annotations.firstOrNull { it.tag == "image" }
            
            when {
                wikiLink != null -> onLinkClick(wikiLink.item)
                tag != null -> onLinkClick(tag.item) // Treat tag click as link click (navigate to page)
                link != null -> onUrlClick(link.item)
                url != null -> onUrlClick(url.item)
                image != null -> onUrlClick(image.item)
                else -> onClick() // Enter edit mode
            }
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            color = textColor
        ),
        modifier = modifier.padding(vertical = 4.dp)
    )
}

/**
 * Renders a list of blocks with proper hierarchy, supporting collapse/expand
 */
@Composable
fun BlockList(
    blocks: List<Block>,
    isDebugMode: Boolean = false,
    editingBlockId: String?,
    collapsedBlocks: Set<Long> = emptySet(),
    onStartEditing: (String) -> Unit,
    onStopEditing: () -> Unit,
    onContentChange: (String, String) -> Unit,
    onLinkClick: (String) -> Unit,
    onNewBlock: (String) -> Unit,
    onSplitBlock: (String, Int) -> Unit,
    onMergeBlock: (String) -> Unit = {},
    editingCursorIndex: Int? = null,
    onBackspace: (String) -> Unit = {}, // uuid
    onLoadContent: (Long) -> Unit = {},
    onToggleCollapse: (Long) -> Unit = {},
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
    // Build a map of parent ID to children for quick lookup
    val childrenByParent = remember(blocks) {
        blocks.groupBy { it.parentId }
    }

    // Get IDs of blocks that have children
    val blocksWithChildren = remember(blocks) {
        blocks.mapNotNull { it.parentId }.toSet()
    }

    // Get all descendant IDs of a block (for hiding when collapsed)
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

    // Calculate which blocks should be hidden due to collapsed ancestors
    val hiddenBlocks = remember(blocks, collapsedBlocks) {
        collapsedBlocks.flatMap { getDescendantIds(it) }.toSet()
    }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            // Only show if not hidden by a collapsed ancestor
            if (block.id !in hiddenBlocks) {
                val hasChildren = block.id in blocksWithChildren
                val isCollapsed = block.id in collapsedBlocks
                
                BlockRenderer(
                    block = block,
                    isDebugMode = isDebugMode,
                    isEditing = editingBlockId == block.uuid,
                    hasChildren = hasChildren,
                    isCollapsed = isCollapsed,
                    onStartEditing = { onStartEditing(block.uuid) },
                    onStopEditing = onStopEditing,
                    onContentChange = { newContent -> onContentChange(block.uuid, newContent) },
                    onLinkClick = onLinkClick,
                    onNewBlock = onNewBlock,
                    onSplitBlock = onSplitBlock,
                    onMergeBlock = onMergeBlock,
                    initialCursorPosition = if (editingBlockId == block.uuid) editingCursorIndex else null,
                    onBackspace = { onBackspace(block.uuid) },
                    onLoadContent = { onLoadContent(block.pageId) },
                    onToggleCollapse = { onToggleCollapse(block.id) },
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