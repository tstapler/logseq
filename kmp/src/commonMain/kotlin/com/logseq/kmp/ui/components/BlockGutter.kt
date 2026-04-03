package com.logseq.kmp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The left-side gutter of a block row: drag handle, collapse/expand toggle,
 * bullet point, and optional debug level indicator.
 */
@Composable
internal fun BlockGutter(
    level: Int,
    isDebugMode: Boolean,
    hasChildren: Boolean,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val dragThreshold = remember(density) { with(density) { 48.dp.toPx() } }

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
        text = "\u2022",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
    )

    // DEBUG: Show level to diagnose indentation issues
    if (isDebugMode) {
        Text(
            text = "L$level",
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.Red
        )
    }
}
