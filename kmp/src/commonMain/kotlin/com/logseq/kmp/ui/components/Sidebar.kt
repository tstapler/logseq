package com.logseq.kmp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.logseq.kmp.model.GraphInfo
import com.logseq.kmp.model.Page
import com.logseq.kmp.ui.Screen

/**
 * Main sidebar component for the application.
 * Updated with multi-graph support.
 */
@Composable
fun LeftSidebar(
    expanded: Boolean,
    isLoading: Boolean,
    favoritePages: List<Page>,
    recentPages: List<Page>,
    currentScreen: Screen,
    currentGraphName: String = "",
    availableGraphs: List<GraphInfo> = emptyList(),
    onPageClick: (Page) -> Unit,
    onNavigate: (String) -> Unit,
    onToggleFavorite: (Page) -> Unit,
    onGraphSelected: (String) -> Unit = {},
    onAddGraph: () -> Unit = {},
    onRemoveGraph: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut()
    ) {
        Column(
            modifier = modifier
                .width(250.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Graph Switcher Section
            GraphSwitcher(
                currentGraphName = currentGraphName,
                availableGraphs = availableGraphs,
                onGraphSelected = onGraphSelected,
                onAddGraph = onAddGraph,
                onRemoveGraph = onRemoveGraph
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Navigation Section
            Text(
                "Navigation",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            NavigationItem("Journals", Icons.Default.DateRange, currentScreen is Screen.Journals) { onNavigate("journals") }
            NavigationItem("Flashcards", Icons.Default.Style, currentScreen is Screen.Flashcards) { onNavigate("flashcards") }
            NavigationItem("All Pages", Icons.AutoMirrored.Filled.List, currentScreen is Screen.AllPages) { onNavigate("all-pages") }
            NavigationItem("Notifications", Icons.Default.Notifications, currentScreen is Screen.Notifications) { onNavigate("notifications") }
            NavigationItem("Logs", Icons.Default.Info, currentScreen is Screen.Logs) { onNavigate("logs") }
            NavigationItem("Performance", Icons.Default.Settings, currentScreen is Screen.Performance) { onNavigate("performance") }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                // Favorites Section
                if (favoritePages.isNotEmpty()) {
                    Text(
                        "Favorites",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    favoritePages.forEach { page ->
                        SidebarItem(
                            title = page.name,
                            isSelected = (currentScreen as? Screen.PageView)?.page?.uuid == page.uuid,
                            icon = Icons.Default.Star,
                            isFavorite = true,
                            onFavoriteClick = { onToggleFavorite(page) },
                            onClick = { onPageClick(page) }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Recent Section
                if (recentPages.isNotEmpty()) {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    recentPages.forEach { page ->
                        SidebarItem(
                            title = page.name,
                            isSelected = (currentScreen as? Screen.PageView)?.page?.uuid == page.uuid,
                            icon = Icons.Default.Description,
                            isFavorite = page.isFavorite,
                            onFavoriteClick = { onToggleFavorite(page) },
                            onClick = { onPageClick(page) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Graph switcher component for selecting and managing graphs.
 */
@Composable
fun GraphSwitcher(
    currentGraphName: String,
    availableGraphs: List<GraphInfo>,
    onGraphSelected: (String) -> Unit,
    onAddGraph: () -> Unit,
    onRemoveGraph: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        // Current graph button
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentGraphName.ifEmpty { "Select Graph" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
        
        // Dropdown with graph list
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp)
            ) {
                availableGraphs.forEach { graph ->
                    GraphItem(
                        graph = graph,
                        isActive = graph.displayName == currentGraphName,
                        onSelect = {
                            onGraphSelected(graph.id)
                            expanded = false
                        },
                        onRemove = if (availableGraphs.size > 1) {
                            { onRemoveGraph(graph.id) }
                        } else null
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // Add graph button
                Surface(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAddGraph()
                            expanded = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Graph...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual graph item in the dropdown.
 */
@Composable
fun GraphItem(
    graph: GraphInfo,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = graph.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = graph.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove graph",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun RightSidebar(
    expanded: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
    ) {
        Column(
            modifier = modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Right Sidebar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close Sidebar")
                }
            }
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Placeholder for Block Properties or Linked References
            Text(
                "Block Properties",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("No block selected", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Linked References",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Text(
                "No linked references found.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SidebarItem(
    title: String,
    isSelected: Boolean,
    icon: ImageVector,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                    modifier = Modifier.size(20.dp),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun NavigationItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
