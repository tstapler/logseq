package com.logseq.kmp.ui.screenshots

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.takahirom.roborazzi.captureRoboImage
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.ui.MainLayout
import com.logseq.kmp.ui.Screen
import com.logseq.kmp.ui.components.LeftSidebar
import com.logseq.kmp.ui.components.TopBar
import com.logseq.kmp.ui.AppState
import com.logseq.kmp.ui.fixtures.FakeFileSystem
import com.logseq.kmp.ui.fixtures.PopulatedFakeBlockRepository
import com.logseq.kmp.ui.fixtures.PopulatedFakePageRepository
import com.logseq.kmp.ui.screens.JournalsView
import com.logseq.kmp.ui.screens.JournalsViewModel
import com.logseq.kmp.ui.theme.LogseqTheme
import com.logseq.kmp.ui.theme.LogseqThemeMode
import com.logseq.kmp.platform.PlatformSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class DesktopScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeJournalsViewModel(): JournalsViewModel {
        val pageRepo = PopulatedFakePageRepository()
        val blockRepo = PopulatedFakeBlockRepository()
        val fileSystem = FakeFileSystem()
        val graphLoader = GraphLoader(fileSystem, pageRepo, blockRepo)
        return JournalsViewModel(pageRepo, blockRepo, graphLoader, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun desktop_journals_light() {
        val viewModel = makeJournalsViewModel()
        val blockRepo = PopulatedFakeBlockRepository()

        composeTestRule.setContent {
            LogseqTheme(themeMode = LogseqThemeMode.LIGHT) {
                MainLayout(
                    topBar = {
                        TopBar(
                            appState = AppState(currentScreen = Screen.Journals),
                            platformSettings = PlatformSettings(),
                            onSettingsClick = {},
                            onNewPageClick = {},
                            onNavigate = {},
                            onThemeChange = {},
                            onLanguageChange = {},
                            onResetOnboarding = {},
                            onToggleDebug = {}
                        )
                    },
                    leftSidebar = {
                        LeftSidebar(
                            expanded = true,
                            isLoading = false,
                            favoritePages = emptyList(),
                            recentPages = emptyList(),
                            currentScreen = Screen.Journals,
                            onPageClick = {},
                            onNavigate = {},
                            onToggleFavorite = {}
                        )
                    },
                    rightSidebar = {},
                    content = {
                        JournalsView(
                            viewModel = viewModel,
                            blockRepository = blockRepo,
                            isDebugMode = false,
                            onLinkClick = {},
                            onContentChange = { _, _, _, _ -> }
                        )
                    },
                    statusBar = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/desktop_journals_light.png")
    }

    @Test
    fun desktop_journals_dark() {
        val viewModel = makeJournalsViewModel()
        val blockRepo = PopulatedFakeBlockRepository()

        composeTestRule.setContent {
            LogseqTheme(themeMode = LogseqThemeMode.DARK) {
                MainLayout(
                    topBar = {
                        TopBar(
                            appState = AppState(currentScreen = Screen.Journals),
                            platformSettings = PlatformSettings(),
                            onSettingsClick = {},
                            onNewPageClick = {},
                            onNavigate = {},
                            onThemeChange = {},
                            onLanguageChange = {},
                            onResetOnboarding = {},
                            onToggleDebug = {}
                        )
                    },
                    leftSidebar = {
                        LeftSidebar(
                            expanded = true,
                            isLoading = false,
                            favoritePages = emptyList(),
                            recentPages = emptyList(),
                            currentScreen = Screen.Journals,
                            onPageClick = {},
                            onNavigate = {},
                            onToggleFavorite = {}
                        )
                    },
                    rightSidebar = {},
                    content = {
                        JournalsView(
                            viewModel = viewModel,
                            blockRepository = blockRepo,
                            isDebugMode = false,
                            onLinkClick = {},
                            onContentChange = { _, _, _, _ -> }
                        )
                    },
                    statusBar = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/desktop_journals_dark.png")
    }
}
