package com.logseq.kmp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logseq.kmp.ui.screens.SearchResultItem
import com.logseq.kmp.ui.screens.SearchViewModel

@Composable
fun SearchDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigateToPage: (String) -> Unit,
    onNavigateToBlock: (String) -> Unit,
    onCreatePage: (String) -> Unit,
    viewModel: SearchViewModel
) {
    if (!visible) return

    val uiState by viewModel.uiState.collectAsState()
    var selectedIndex by remember(uiState.results) { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            try { focusRequester.requestFocus() } catch (_: IllegalStateException) {}
            viewModel.onQueryChange("") // Reset query
        }
    }

    LaunchedEffect(selectedIndex) {
        if (uiState.results.isNotEmpty()) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 100.dp)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) {} // Prevent clicks from dismissing
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    if (uiState.results.isNotEmpty()) {
                                        selectedIndex = (selectedIndex + 1) % uiState.results.size
                                        // Skip headers if selected
                                        if (uiState.results[selectedIndex] is SearchResultItem.Header) {
                                            selectedIndex = (selectedIndex + 1) % uiState.results.size
                                        }
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (uiState.results.isNotEmpty()) {
                                        selectedIndex = if (selectedIndex <= 0) uiState.results.size - 1 else selectedIndex - 1
                                        // Skip headers if selected
                                        if (uiState.results[selectedIndex] is SearchResultItem.Header) {
                                            selectedIndex = if (selectedIndex <= 0) uiState.results.size - 1 else selectedIndex - 1
                                        }
                                    }
                                    true
                                }
                                Key.Enter -> {
                                    if (uiState.results.isNotEmpty()) {
                                        when (val item = uiState.results[selectedIndex]) {
                                            is SearchResultItem.PageItem -> {
                                                onNavigateToPage(item.page.uuid)
                                                onDismiss()
                                            }
                                            is SearchResultItem.AliasItem -> {
                                                onNavigateToPage(item.page.uuid)
                                                onDismiss()
                                            }
                                            is SearchResultItem.BlockItem -> {
                                                onNavigateToBlock(item.block.uuid)
                                                onDismiss()
                                            }
                                            is SearchResultItem.CreatePageItem -> {
                                                onCreatePage(item.query)
                                                onDismiss()
                                            }
                                            else -> {}
                                        }
                                    }
                                    true
                                }
                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                TextField(
                    value = uiState.query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search pages and blocks...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(uiState.results) { index, item ->
                            when (item) {
                                is SearchResultItem.Header -> {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                is SearchResultItem.PageItem -> {
                                    SearchResultRow(
                                        title = item.page.name,
                                        subtitle = "Page",
                                        isSelected = index == selectedIndex,
                                        onClick = {
                                            onNavigateToPage(item.page.uuid)
                                            onDismiss()
                                        }
                                    )
                                }
                                is SearchResultItem.AliasItem -> {
                                    SearchResultRow(
                                        title = item.alias,
                                        subtitle = "Alias for ${item.page.name}",
                                        isSelected = index == selectedIndex,
                                        onClick = {
                                            onNavigateToPage(item.page.uuid)
                                            onDismiss()
                                        }
                                    )
                                }
                                is SearchResultItem.BlockItem -> {
                                    SearchResultRow(
                                        title = item.block.content.take(100), // Truncate
                                        subtitle = "Block",
                                        isSelected = index == selectedIndex,
                                        onClick = {
                                            onNavigateToBlock(item.block.uuid)
                                            onDismiss()
                                        }
                                    )
                                }
                                is SearchResultItem.CreatePageItem -> {
                                    SearchResultRow(
                                        title = "Create page \"${item.query}\"",
                                        subtitle = "New Page",
                                        isSelected = index == selectedIndex,
                                        onClick = {
                                            onCreatePage(item.query)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.results.isEmpty() && !uiState.isLoading && uiState.query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No results found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
