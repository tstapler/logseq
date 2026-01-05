package com.logseq.kmp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.logseq.kmp.model.Page
import com.logseq.kmp.ui.Screen

@Composable
fun LeftSidebar(
    expanded: Boolean,
    isLoading: Boolean,
    favoritePages: List<Page>,
    recentPages: List<Page>,
    currentScreen: Screen,
    onPageClick: (Page) -> Unit,
    onNavigate: (String) -> Unit,
    onToggleFavorite: (Page) -> Unit,
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
                            isSelected = (currentScreen as? Screen.PageView)?.page?.id == page.id,
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
                            isSelected = (currentScreen as? Screen.PageView)?.page?.id == page.id,
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
