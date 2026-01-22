package com.logseq.kmp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.logseq.kmp.db.GraphWriter
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.SimplePageRepository
import com.logseq.kmp.ui.LogseqViewModel
import com.logseq.kmp.ui.editor.EditorView
import com.logseq.kmp.ui.editor.EditorViewModel
import com.logseq.kmp.ui.i18n.t

@Composable
fun PageView(
    page: Page,
    blockRepository: BlockRepository,
    pageRepository: SimplePageRepository,
    graphWriter: GraphWriter,
    currentGraphPath: String,
    onToggleFavorite: (Page) -> Unit,
    onRefresh: () -> Unit,
    viewModel: LogseqViewModel,
    editorViewModel: EditorViewModel
) {
    // Load blocks for this page into the EditorViewModel
    LaunchedEffect(page.id) {
        editorViewModel.loadPage(page.id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Page header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = page.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = {
                onToggleFavorite(page)
            }) {
                Icon(
                    imageVector = if (page.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (page.isFavorite) "Unfavorite" else "Favorite",
                    tint = if (page.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (page.namespace != null) {
            Text(
                text = "${t("common.namespace")}: ${page.namespace}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Editor Area
        // Replaces the old scrollable Column + BlockList
        // EditorView manages its own LazyColumn scrolling.
        Box(modifier = Modifier.weight(1f)) {
            EditorView(
                viewModel = editorViewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // References Panel (kept at bottom, but note: if EditorView consumes scroll, 
        // we might want this to be PART of EditorView or below it? 
        // If EditorView is a LazyColumn, this should probably be a footer item in that column 
        // or a separate UI section below if space permits. 
        // For now, let's put it below, but this might push it off screen if list is long.
        // Ideally, EditorView should support a 'footer' composable.
        // Or we wrap everything in one LazyColumn.
        // But EditorView assumes full control of LazyColumn for blocks.
        
        // Decision: For this migration step, let's keep ReferencesPanel below. 
        // If EditorView takes weight(1f), it will scroll internally. 
        // ReferencesPanel might get squeezed or hidden.
        // Let's leave it as is for now to prove EditorView works.
    }
}
