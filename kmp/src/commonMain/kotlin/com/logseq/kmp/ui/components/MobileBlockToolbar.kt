package com.logseq.kmp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
        }
    }
}
