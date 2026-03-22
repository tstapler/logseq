package com.logseq.kmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

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
            Box(modifier = Modifier.testTag("left-sidebar")) { leftSidebar() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("content-area")
            ) {
                content()
            }

            rightSidebar()
        }

        // Status Bar
        statusBar()
    }
}
