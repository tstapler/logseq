package com.logseq.kmp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.PageRepository
import kotlinx.coroutines.flow.first

/**
 * Panel showing linked and unlinked references to a page.
 * Similar to Logseq's backlinks panel.
 */
@Composable
fun ReferencesPanel(
    page: Page,
    blockRepository: BlockRepository,
    pageRepository: PageRepository,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkedRefs by blockRepository.getLinkedReferences(page.name).collectAsState(initial = Result.success(emptyList()))
    val unlinkedRefs by blockRepository.getUnlinkedReferences(page.name).collectAsState(initial = Result.success(emptyList()))

    val linkedBlocks = linkedRefs.getOrNull() ?: emptyList()
    val unlinkedBlocks = unlinkedRefs.getOrNull() ?: emptyList()

    Column(modifier = modifier.fillMaxWidth()) {
        // Linked References Section
        if (linkedBlocks.isNotEmpty()) {
            ReferenceSection(
                title = "${linkedBlocks.size} Linked Reference${if (linkedBlocks.size != 1) "s" else ""}",
                blocks = linkedBlocks,
                pageRepository = pageRepository,
                onLinkClick = onLinkClick,
                initiallyExpanded = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Unlinked References Section
        if (unlinkedBlocks.isNotEmpty()) {
            ReferenceSection(
                title = "${unlinkedBlocks.size} Unlinked Reference${if (unlinkedBlocks.size != 1) "s" else ""}",
                blocks = unlinkedBlocks,
                pageRepository = pageRepository,
                onLinkClick = onLinkClick,
                initiallyExpanded = false
            )
        }
    }
}

@Composable
private fun ReferenceSection(
    title: String,
    blocks: List<Block>,
    pageRepository: PageRepository,
    onLinkClick: (String) -> Unit,
    initiallyExpanded: Boolean = true
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section Content
        AnimatedVisibility(visible = expanded) {
            Column {
                // Group blocks by page for display
                val blocksByPage = blocks.groupBy { it.pageUuid }

                blocksByPage.forEach { (pageUuid, pageBlocks) ->
                    ReferencePageGroup(
                        pageUuid = pageUuid,
                        blocks = pageBlocks,
                        pageRepository = pageRepository,
                        onLinkClick = onLinkClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferencePageGroup(
    pageUuid: String,
    blocks: List<Block>,
    pageRepository: PageRepository,
    onLinkClick: (String) -> Unit
) {
    // Look up the page name
    var pageName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pageUuid) {
        val page = pageRepository.getPageByUuid(pageUuid).first().getOrNull()
        pageName = page?.name
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Page name header (clickable)
            pageName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onLinkClick(name) }
                        .padding(bottom = 8.dp)
                )
            }

            // Blocks from this page
            blocks.forEach { block ->
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    WikiLinkText(
                        text = block.content,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        linkColor = MaterialTheme.colorScheme.primary,
                        onLinkClick = onLinkClick,
                        onClick = { /* Could open this specific block */ },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
