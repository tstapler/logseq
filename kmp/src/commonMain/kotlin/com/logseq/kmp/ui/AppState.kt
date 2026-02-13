package com.logseq.kmp.ui

import com.logseq.kmp.model.Page
import com.logseq.kmp.ui.theme.LogseqThemeMode
import com.logseq.kmp.ui.i18n.Language

sealed class Screen {
    data object Journals : Screen()
    data object Flashcards : Screen()
    data object AllPages : Screen()
    data object Notifications : Screen()
    data object Logs : Screen()
    data object Performance : Screen()
    data class PageView(val page: Page) : Screen()
}

data class AppState(
    val sidebarExpanded: Boolean = true,
    val rightSidebarExpanded: Boolean = false,
    val settingsVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isFullyLoaded: Boolean = true,  // True when all background loading is complete
    val themeMode: LogseqThemeMode = LogseqThemeMode.SYSTEM,
    val language: Language = Language.ENGLISH,
    val onboardingCompleted: Boolean = false,
    val currentScreen: Screen = Screen.Journals,
    val currentPage: Page? = null,
    val currentGraphPath: String = "",
    val commandPaletteVisible: Boolean = false,
    val searchDialogVisible: Boolean = false,
    val commands: List<Command> = emptyList(),
    val statusMessage: String = "Ready",
    // Navigation history for forward/back navigation
    val navigationHistory: List<Screen> = listOf(Screen.Journals),
    val historyIndex: Int = 0,
    // Derived/Cached page lists for UI
    val regularPages: List<Page> = emptyList(),
    val journalPages: List<Page> = emptyList(),
    val favoritePages: List<Page> = emptyList(),
    val recentPages: List<Page> = emptyList(),
    val editingBlockId: String? = null,
    val editingCursorIndex: Int? = null,
    // Debug settings
    val isDebugMode: Boolean = false
) {
    val canGoBack: Boolean get() = historyIndex > 0
    val canGoForward: Boolean get() = historyIndex < navigationHistory.size - 1
}

data class Command(
    val id: String,
    val label: String,
    val shortcut: String? = null,
    val action: () -> Unit
)
