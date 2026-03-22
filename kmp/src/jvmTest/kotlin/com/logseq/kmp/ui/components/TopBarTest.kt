package com.logseq.kmp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.logseq.kmp.platform.PlatformSettings
import com.logseq.kmp.ui.AppState
import com.logseq.kmp.ui.Screen
import com.logseq.kmp.ui.theme.LogseqThemeMode
import org.junit.Rule
import org.junit.Test

class TopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // canGoBack/canGoForward are computed: historyIndex=0, navigationHistory=[Journals] → both false
    private val defaultAppState = AppState(
        currentScreen = Screen.Journals,
        themeMode = LogseqThemeMode.SYSTEM
    )

    @Test
    fun topBar_rendersFileAndViewMenus() {
        composeTestRule.setContent {
            MaterialTheme {
                TopBar(
                    appState = defaultAppState,
                    platformSettings = PlatformSettings(),
                    onSettingsClick = {},
                    onNewPageClick = {},
                    onNavigate = {},
                    onThemeChange = {},
                    onLanguageChange = {},
                    onResetOnboarding = {},
                    onToggleDebug = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("top-bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("File").assertIsDisplayed()
        composeTestRule.onNodeWithText("View").assertIsDisplayed()
    }

    @Test
    fun topBar_navigationButtonsPresent() {
        composeTestRule.setContent {
            MaterialTheme {
                TopBar(
                    appState = defaultAppState,
                    platformSettings = PlatformSettings(),
                    onSettingsClick = {},
                    onNewPageClick = {},
                    onNavigate = {},
                    onThemeChange = {},
                    onLanguageChange = {},
                    onResetOnboarding = {},
                    onToggleDebug = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Go Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Go Forward").assertIsDisplayed()
    }

    @Test
    fun topBar_viewMenuOpensOnClick() {
        composeTestRule.setContent {
            MaterialTheme {
                TopBar(
                    appState = defaultAppState,
                    platformSettings = PlatformSettings(),
                    onSettingsClick = {},
                    onNewPageClick = {},
                    onNavigate = {},
                    onThemeChange = {},
                    onLanguageChange = {},
                    onResetOnboarding = {},
                    onToggleDebug = {}
                )
            }
        }

        composeTestRule.onNodeWithText("View").performClick()

        // View menu items should appear
        composeTestRule.onNodeWithText("Performance Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show Debug Info").assertIsDisplayed()
    }
}
