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
    // Auto-detect plain URLs - matches http:// or https:// followed by non-whitespace
    val urlPattern = Regex("""https?://[^\s<>"]+""")
}

const val WIKI_LINK_TAG = "WIKI_LINK"
const val BLOCK_REF_TAG = "BLOCK_REF"

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
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember(block.uuid, block.content) {
        mutableStateOf(TextFieldValue(text = block.content))
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
        }
    }

    // Trigger load if not loaded
    LaunchedEffect(block.isLoaded) {
        if (!block.isLoaded) {
            onLoadContent()
        }
    }

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
            // Spacer for alignment - width must MATCH the Icon size (18.dp) + padding (4.dp)
            // But we already have a spacer?
            // "Spacer(modifier = Modifier.width(18.dp).padding(end = 4.dp))"
            // Wait, padding(end=4.dp) adds space to the right of the 18.dp width? 
            // Or does it effectively make the spacer 22dp wide?
            // Icon has modifier .size(18.dp).padding(end = 4.dp)
            // This means the Icon takes 18dp space, and has 4dp padding inside/outside depending on order?
            // Compose modifiers are applied sequentially.
            // .size(18.dp) -> sets size constraints.
            // .padding(end=4.dp) -> adds padding.
            // Total width occupied = 18 + 4 = 22dp (if padding is external).
            
            // Spacer:
            // .width(18.dp).padding(end=4.dp)
            // Total width = 18 + 4 = 22dp.
            
            // So alignment should be correct.
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
                        if (event.type == KeyEventType.KeyDown) {
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
}

/**
 * Parses the text and extracts markdown elements (images, links, formatting, wiki links)
 */
private fun parseMarkdown(
    text: String,
    linkColor: Color,
    textColor: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var currentIndex = 0
    
    // Process markdown in order of precedence
    val result = processMarkdownText(text)
    
    // Apply wiki link parsing on the processed result
    return parseWikiLinks(result, linkColor, textColor)
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

    return builder.toAnnotatedString()
}

/**
 * Applies wiki link styling to an already styled AnnotatedString
 */
private fun parseWikiLinksWithStyling(
    annotatedString: AnnotatedString,
    linkColor: Color,
    textColor: Color,
): AnnotatedString {
    // Get the plain text to find wiki links
    val text = annotatedString.text
    val linkPattern = MarkdownPatterns.wikiLinkPattern
    val matches = linkPattern.findAll(text)

    val builder = AnnotatedString.Builder(annotatedString)
    val textLength = text.length

    // Add wiki link styling
    for (match in matches) {
        val linkText = match.groupValues[1]

        // Bounds check to prevent out-of-bounds errors
        val safeStart = match.range.first.coerceIn(0, textLength)
        val safeEnd = (match.range.last + 1).coerceIn(safeStart, textLength)

        if (safeStart < safeEnd) {
            // Use addStringAnnotation for existing text ranges (not pushStringAnnotation which only works for appended text)
            builder.addStringAnnotation(
                tag = WIKI_LINK_TAG,
                annotation = linkText,
                start = safeStart,
                end = safeEnd
            )
            builder.addStyle(
                style = SpanStyle(
                    color = linkColor,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.None,
                ),
                start = safeStart,
                end = safeEnd,
            )
        }
    }

    return builder.toAnnotatedString()
}

/**
 * Processes markdown patterns in text and returns final text with annotations
 */
private fun processMarkdownText(text: String): String {
    var processedText = text
    
    // Process images first
    MarkdownPatterns.imagePattern.findAll(processedText).forEach { match ->
        val imageUrl = match.groupValues[2]
        val replacement = match.value // Keep original for now, will be styled later
        processedText = processedText.replace(match.value, replacement)
    }
    
    // Process links
    MarkdownPatterns.linkPattern.findAll(processedText).forEach { match ->
        val linkText = match.groupValues[1]
        val linkUrl = match.groupValues[2]
        processedText = processedText.replace(match.value, linkText) // Keep only link text
    }
    
    // Process code
    MarkdownPatterns.codePattern.findAll(processedText).forEach { match ->
        val codeText = match.groupValues[1]
        processedText = processedText.replace(match.value, codeText)
    }
    
    // Process strikethrough
    MarkdownPatterns.strikethroughPattern.findAll(processedText).forEach { match ->
        val strikethroughText = match.groupValues[1]
        processedText = processedText.replace(match.value, strikethroughText)
    }
    
    // Process bold
    MarkdownPatterns.boldPattern.findAll(processedText).forEach { match ->
        val boldText = match.groupValues[2]
        processedText = processedText.replace(match.value, boldText)
    }
    
    // Process italic last (to avoid conflicts with bold)
    MarkdownPatterns.italicPattern.findAll(processedText).forEach { match ->
        val italicText = match.groupValues[2]
        processedText = processedText.replace(match.value, italicText)
    }
    
    return processedText
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
            // Check if we clicked on a wiki link first (highest priority for internal navigation)
            annotatedString.getStringAnnotations(
                tag = WIKI_LINK_TAG,
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                onLinkClick(annotation.item)
            } ?: run {
                // Check if we clicked on a markdown link [text](url)
                annotatedString.getStringAnnotations(
                    tag = "link",
                    start = offset,
                    end = offset
                ).firstOrNull()?.let { annotation ->
                    onUrlClick(annotation.item)
                } ?: run {
                    // Check if we clicked on an auto-linked URL
                    annotatedString.getStringAnnotations(
                        tag = "url",
                        start = offset,
                        end = offset
                    ).firstOrNull()?.let { annotation ->
                        onUrlClick(annotation.item)
                    } ?: run {
                        // Check if we clicked on an image
                        annotatedString.getStringAnnotations(
                            tag = "image",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { annotation ->
                            onUrlClick(annotation.item)
                        } ?: run {
                            // Clicked on regular text, enter edit mode
                            onClick()
                        }
                    }
                }
            }
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            color = textColor
        ),
        modifier = modifier.padding(vertical = 4.dp)
    )
}

/**
 * Parse text and create AnnotatedString with clickable wiki links
 */
private fun parseWikiLinks(
    text: String,
    linkColor: Color,
    textColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val wikiLinkPattern = """\[\[([^\]]+)\]\]""".toRegex()
        var lastIndex = 0

        // Style for regular text
        val regularTextStyle = if (textColor != Color.Unspecified) {
            SpanStyle(color = textColor)
        } else null

        wikiLinkPattern.findAll(text).forEach { matchResult ->
            // Add text before the link with proper color
            if (matchResult.range.first > lastIndex) {
                val beforeText = text.substring(lastIndex, matchResult.range.first)
                if (regularTextStyle != null) {
                    withStyle(regularTextStyle) {
                        append(beforeText)
                    }
                } else {
                    append(beforeText)
                }
            }

            // Add the link
            val linkText = matchResult.groupValues[1] // The text inside [[...]]
            pushStringAnnotation(tag = "WIKI_LINK", annotation = linkText)
            withStyle(
                style = SpanStyle(
                    color = linkColor,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.None
                )
            ) {
                append(linkText)
            }
            pop()

            lastIndex = matchResult.range.last + 1
        }

        // Add remaining text with proper color
        if (lastIndex < text.length) {
            val remainingText = text.substring(lastIndex)
            if (regularTextStyle != null) {
                withStyle(regularTextStyle) {
                    append(remainingText)
                }
            } else {
                append(remainingText)
            }
        }
    }
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
                    onResolveContent = onResolveContent
                )
            }
        }
    }
}
