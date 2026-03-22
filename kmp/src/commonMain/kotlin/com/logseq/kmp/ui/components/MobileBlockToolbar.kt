package com.logseq.kmp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MobileBlockToolbar(
    editingBlockId: String?,
    onIndent: (String) -> Unit,
    onOutdent: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onAddBlock: (String) -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (editingBlockId == null) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Undo
            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }

            // Redo
            IconButton(onClick = onRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }

            // Outdent
            IconButton(onClick = { onOutdent(editingBlockId) }) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Outdent"
                )
            }

            // Indent
            IconButton(onClick = { onIndent(editingBlockId) }) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Indent"
                )
            }

            // Move Up
            IconButton(onClick = { onMoveUp(editingBlockId) }) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "Move Up"
                )
            }

            // Move Down
            IconButton(onClick = { onMoveDown(editingBlockId) }) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = "Move Down"
                )
            }

            // Add Block
            IconButton(onClick = { onAddBlock(editingBlockId) }) {
                Icon(Icons.Default.Add, contentDescription = "New Block")
            }
        }
    }
}
