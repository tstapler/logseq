package com.logseq.kmp.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.logseq.kmp.ui.LogseqApp
import com.logseq.kmp.ui.theme.setSystemDarkTheme
import com.logseq.kmp.platform.PlatformFileSystem
import com.logseq.kmp.logging.Logger
import com.logseq.kmp.error.JvmErrorTracker
import javax.swing.UIManager

fun main() {
    // Initialize error tracking early
    val errorTracker = JvmErrorTracker()
    errorTracker.recordBreadcrumb("Application starting", "SYSTEM")
    
    val logger = Logger("DesktopMain")
    
    application {
        try {
            val defaults = UIManager.getDefaults()
            val isDark = defaults.keys.toList().any { key ->
                key.toString().contains("dark", ignoreCase = true)
            }
            setSystemDarkTheme(isDark)
        } catch (e: Exception) {
            logger.warn("Failed to detect system theme", e)
            setSystemDarkTheme(false)
        }

        val windowState = rememberWindowState(
            width = 1200.dp,
            height = 800.dp
        )

        val fileSystem = PlatformFileSystem()
        val graphPath = fileSystem.getDefaultGraphPath()
        
        logger.info("Starting Desktop Application with graph: $graphPath")
        errorTracker.recordBreadcrumb("Graph path resolved: $graphPath", "SYSTEM")

        var viewModel: com.logseq.kmp.ui.LogseqViewModel? = null

        Window(
            onCloseRequest = {
                logger.info("Closing application - flushing pending changes")
                viewModel?.savePendingChanges()
                // Give it a short moment to start the flush before exit
                // In a real app we might want to wait for the flush to complete
                exitApplication()
            },
            state = windowState,
            title = "Logseq KMP"
        ) {
            // We need a way to get the viewModel from LogseqApp or similar
            // For now, LogseqApp creates its own ViewModel.
            // A better architecture would be to hoist the ViewModel or use a GlobalRegistry.
            LogseqApp(
                fileSystem = fileSystem,
                graphPath = graphPath
            )
        }
    }
}
