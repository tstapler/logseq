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
import com.logseq.kmp.editor.commands.CommandContext
import com.logseq.kmp.editor.commands.CommandSuggestion
import com.logseq.kmp.ui.i18n.t
import kotlinx.coroutines.launch

@Composable
fun EnhancedCommandPalette(
    visible: Boolean,
    commandManager: com.logseq.kmp.editor.commands.CommandManager,
    onDismiss: () -> Unit,
    onCommandExecuted: () -> Unit = {}
) {
    if (!visible) return

    var searchQuery by remember { mutableStateOf("") }
    var isSlashCommand by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<CommandSuggestion>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
            searchQuery = ""
            isSlashCommand = false
            suggestions = emptyList()
            selectedIndex = 0
        }
    }

    LaunchedEffect(searchQuery) {
        if (visible) {
            coroutineScope.launch {
                isSlashCommand = commandManager.isSlashCommand(searchQuery)
                if (isSlashCommand) {
                    // Handle slash commands differently
                    // For now, show regular command suggestions
                    val slashSuggestions = commandManager.getSlashCommandSuggestions(searchQuery)
                    if (slashSuggestions.isNotEmpty()) {
                        // Map slash suggestions to command suggestions
                        suggestions = emptyList() // TODO: Implement mapping
                    } else {
                        suggestions = emptyList()
                    }
                } else {
                    val context = CommandContext()
                    suggestions = commandManager.getCommandSuggestions(searchQuery, context)
                }
                selectedIndex = 0
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        if (suggestions.isNotEmpty()) {
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
                                    if (suggestions.isNotEmpty()) {
                                        selectedIndex = (selectedIndex + 1) % suggestions.size
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (suggestions.isNotEmpty()) {
                                        selectedIndex = if (selectedIndex <= 0) suggestions.size - 1 else selectedIndex - 1
                                    }
                                    true
                                }
                                Key.Enter -> {
                                    if (suggestions.isNotEmpty()) {
                                        val suggestion = suggestions[selectedIndex]
                                        executeCommand(suggestion, commandManager, coroutineScope, onCommandExecuted)
                                        onDismiss()
                                    } else if (isSlashCommand) {
                                        // Execute slash command directly
                                        executeSlashCommand(searchQuery, commandManager, coroutineScope, onCommandExecuted)
                                        onDismiss()
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
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { 
                        Text(
                            if (isSlashCommand) t("command.palette.slash.placeholder") 
                            else t("command.palette.placeholder")
                        ) 
                    },
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

                if (suggestions.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(suggestions) { index, suggestion ->
                            EnhancedCommandItem(
                                suggestion = suggestion,
                                isSelected = index == selectedIndex,
                                onClick = {
                                    executeCommand(suggestion, commandManager, coroutineScope, onCommandExecuted)
                                    onDismiss()
                                }
                            )
                        }
                    }
                } else if (isSlashCommand) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Press Enter to execute slash command",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t("command.palette.no-results"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedCommandItem(
    suggestion: CommandSuggestion,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.highlightedLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            
            if (suggestion.command.description.isNotEmpty()) {
                Text(
                    text = suggestion.command.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (suggestion.command.shortcut != null) {
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = suggestion.command.shortcut,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun executeCommand(
    suggestion: CommandSuggestion,
    commandManager: com.logseq.kmp.editor.commands.CommandManager,
    scope: kotlinx.coroutines.CoroutineScope,
    onCommandExecuted: () -> Unit
) {
    scope.launch {
        try {
            val result = commandManager.executeCommand(suggestion.command.id)
            if (result is com.logseq.kmp.editor.commands.CommandResult.Success) {
                onCommandExecuted()
            }
        } catch (e: Exception) {
            // Error handled by command manager
        }
    }
}

private fun executeSlashCommand(
    input: String,
    commandManager: com.logseq.kmp.editor.commands.CommandManager,
    scope: kotlinx.coroutines.CoroutineScope,
    onCommandExecuted: () -> Unit
) {
    scope.launch {
        try {
            val result = commandManager.executeSlashCommand(input)
            if (result is com.logseq.kmp.editor.commands.CommandResult.Success) {
                onCommandExecuted()
            }
        } catch (e: Exception) {
            // Error handled by command manager
        }
    }
}