package com.logseq.kmp.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.logseq.kmp.ui.LogseqApp
import com.logseq.kmp.platform.PlatformFileSystem

@Composable
fun App(
    windowState: WindowState,
    fileSystem: PlatformFileSystem,
    graphPath: String,
    onExit: () -> Unit
) {
    Window(
        onCloseRequest = onExit,
        title = "Logseq",
        state = windowState
    ) {
        LogseqApp(
            fileSystem = fileSystem,
            graphPath = graphPath
        )
    }
}
