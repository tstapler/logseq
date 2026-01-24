package com.logseq.kmp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.*
import com.logseq.kmp.model.Block
import kotlin.math.abs

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
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    onContentChange: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onNewBlock: (String) -> Unit,
    onSplitBlock: (String, Int) -> Unit,
    onBackspace: () -> Unit = {},
    onLoadContent: () -> Unit = {},
    onToggleCollapse: () -> Unit = {},
    onIndent: () -> Unit = {},
    onOutdent: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember(block.uuid, block.content) {
        mutableStateOf(TextFieldValue(text = block.content))
    }

    var hasFocused by remember { mutableStateOf(false) }

    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val dragThreshold = remember(density) { with(density) { 48.dp.toPx() } }

    // Request focus when entering edit mode
    LaunchedEffect(isEditing) {
        if (isEditing) {
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
                                    // Use 'text' instead of 'textFieldValue.text' because textFieldValue might be stale?
                                    // Or simply debug print to see what's happening.
                                    // The basic TextField value should be up to date.
                                    
                                    // Debug
                                    // println("Backspace: isEmpty=${textFieldValue.text.isEmpty()} text='${textFieldValue.text}'")
                                    
                                    if (textFieldValue.text.isEmpty()) {
                                        onBackspace()
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
                                        false
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (event.isAltPressed) {
                                        onMoveDown()
                                        true
                                    } else {
                                        false
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
            // Wrap in a Box to ensure the entire area is clickable even if text is short
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onStartEditing() }
            ) {
                WikiLinkText(
                    text = block.content,
                    onLinkClick = onLinkClick,
                    onClick = onStartEditing,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Renders text with clickable wiki links [[Page Name]]
 */
@Composable
fun WikiLinkText(
    text: String,
    onLinkClick: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground

    val annotatedString = remember(text) {
        parseWikiLinks(text, linkColor, textColor)
    }

    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            // Check if we clicked on a link
            annotatedString.getStringAnnotations(
                tag = "WIKI_LINK",
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                onLinkClick(annotation.item)
            } ?: run {
                // Clicked on regular text, enter edit mode
                onClick()
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

        wikiLinkPattern.findAll(text).forEach { matchResult ->
            // Add text before the link
            if (matchResult.range.first > lastIndex) {
                append(text.substring(lastIndex, matchResult.range.first))
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

        // Add remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
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
    onStartEditing: (String) -> Unit,
    onStopEditing: () -> Unit,
    onContentChange: (String, String) -> Unit,
    onLinkClick: (String) -> Unit,
    onNewBlock: (String) -> Unit,
    onSplitBlock: (String, Int) -> Unit,
    onBackspace: (String) -> Unit = {}, // uuid
    onLoadContent: (Long) -> Unit = {},
    onIndent: (String) -> Unit = {},
    onOutdent: (String) -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Track collapsed blocks by their ID
    var collapsedBlocks by remember { mutableStateOf(setOf<Long>()) }

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
                    onBackspace = { onBackspace(block.uuid) },
                    onLoadContent = { onLoadContent(block.pageId) },
                    onToggleCollapse = {
                        collapsedBlocks = if (isCollapsed) {
                            collapsedBlocks - block.id
                        } else {
                            collapsedBlocks + block.id
                        }
                    },
                    onIndent = { onIndent(block.uuid) },
                    onOutdent = { onOutdent(block.uuid) },
                    onMoveUp = { onMoveUp(block.uuid) },
                    onMoveDown = { onMoveDown(block.uuid) }
                )
            }
        }
    }
}
