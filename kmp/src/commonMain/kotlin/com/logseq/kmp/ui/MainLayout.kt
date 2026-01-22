package com.logseq.kmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MainLayout(
    topBar: @Composable () -> Unit,
    leftSidebar: @Composable () -> Unit,
    rightSidebar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    statusBar: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Menu Bar
        topBar()

        // Main Content Area
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            leftSidebar()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                content()
            }

            rightSidebar()
        }

        // Status Bar
        statusBar()
    }
}
