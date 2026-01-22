package com.logseq.kmp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logseq.kmp.performance.PerformanceMonitor
import com.logseq.kmp.performance.TraceEvent
import kotlinx.coroutines.launch

@Composable
fun PerformanceDashboard(
    modifier: Modifier = Modifier
) {
    val events by PerformanceMonitor.events.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filteredEvents = remember(events, searchQuery) {
        events.filter { event ->
            searchQuery.isBlank() ||
            event.name.contains(searchQuery, ignoreCase = true) ||
            event.thread.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Performance Monitor",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )

            // Scroll Buttons
            IconButton(onClick = {
                scope.launch { listState.animateScrollToItem(0) }
            }) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to Top")
            }

            IconButton(onClick = {
                scope.launch { listState.animateScrollToItem(filteredEvents.lastIndex.coerceAtLeast(0)) }
            }) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to Bottom")
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search events...") },
                modifier = Modifier.width(200.dp),
                singleLine = true
            )

            // Clear
            IconButton(onClick = { PerformanceMonitor.clear() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Events")
            }
        }

        HorizontalDivider()

        // Event List
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredEvents) { event ->
                    PerformanceEventItem(event)
                }
            }
        }
    }
}

@Composable
fun PerformanceEventItem(event: TraceEvent) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Duration Indicator (Color coded by duration severity)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(getDurationColor(event.duration), MaterialTheme.shapes.small)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = event.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[${event.thread}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (event.type == "trace") {
                        Text(
                            text = "${event.duration} ms",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = "MARK",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

private fun getDurationColor(duration: Long): Color {
    return when {
        duration > 1000 -> Color(0xFFF44336) // Red (> 1s)
        duration > 100 -> Color(0xFFFFC107)  // Amber (> 100ms)
        else -> Color(0xFF4CAF50)            // Green
    }
}
