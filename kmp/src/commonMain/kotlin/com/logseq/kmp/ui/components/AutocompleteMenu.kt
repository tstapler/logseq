package com.logseq.kmp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.logseq.kmp.ui.screens.SearchResultItem

@Composable
fun AutocompleteMenu(
    items: List<SearchResultItem>,
    selectedIndex: Int,
    onItemSelected: (SearchResultItem) -> Unit,
    onDismiss: () -> Unit,
    cursorRect: Rect?,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty() || cursorRect == null) return

    val popupPositionProvider = remember(cursorRect) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                // Position below the cursor
                val x = cursorRect.left.toInt() + anchorBounds.left
                val y = cursorRect.bottom.toInt() + anchorBounds.top + 10 // Little offset

                // Keep within window bounds logic could be added here
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = modifier
                .width(300.dp)
                .heightIn(max = 250.dp)
                .shadow(4.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            LazyColumn {
                itemsIndexed(items) { index, item ->
                    AutocompleteItem(
                        item = item,
                        isSelected = index == selectedIndex,
                        onClick = { onItemSelected(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutocompleteItem(
    item: SearchResultItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (item) {
            is SearchResultItem.Header -> {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is SearchResultItem.PageItem -> {
                Text(
                    text = item.page.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
            is SearchResultItem.AliasItem -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.alias,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "→ ${item.page.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
            }
            is SearchResultItem.BlockItem -> {
                Text(
                    text = item.block.content.take(50),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1
                )
            }
            is SearchResultItem.CreatePageItem -> {
                Text(
                    text = "Create page: ${item.query}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
