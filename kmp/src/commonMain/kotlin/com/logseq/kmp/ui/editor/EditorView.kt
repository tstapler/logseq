package com.logseq.kmp.ui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.logseq.kmp.model.Block

@Composable
fun EditorView(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val blocks by viewModel.blocks.collectAsState()

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(blocks, key = { it.uuid }) { blockUiModel ->
            BlockView(
                model = blockUiModel,
                onContentChange = { newContent ->
                    viewModel.updateBlockContent(blockUiModel.uuid, newContent)
                }
            )
        }
    }
}
