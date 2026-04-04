package com.logseq.kmp.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.ui.fixtures.FakeFileSystem
import com.logseq.kmp.ui.fixtures.PopulatedFakeBlockRepository
import com.logseq.kmp.ui.fixtures.PopulatedFakePageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class JournalsViewUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeViewModel(): JournalsViewModel {
        val pageRepo = PopulatedFakePageRepository()
        val blockRepo = PopulatedFakeBlockRepository()
        val fileSystem = FakeFileSystem()
        val graphLoader = GraphLoader(fileSystem, pageRepo, blockRepo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return JournalsViewModel(pageRepo, blockRepo, graphLoader, scope)
    }

    @Test
    fun journalsView_showsJournalDates() {
        val viewModel = makeViewModel()
        val blockRepo = PopulatedFakeBlockRepository()

        composeTestRule.setContent {
            MaterialTheme {
                JournalsView(
                    viewModel = viewModel,
                    blockRepository = blockRepo,
                    isDebugMode = false,
                    onLinkClick = {},
                    onContentChange = { _, _, _, _ -> }
                )
            }
        }

        // Journal pages from TestFixtures have names "2026-03-28", "2026-03-02", "2026-03-03"
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasText("2026-03-28", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("2026-03-28", substring = true).assertIsDisplayed()
    }

    @Test
    fun journalsView_showsClickToWriteWhenEmpty() {
        val pageRepo = PopulatedFakePageRepository()
        val blockRepo = PopulatedFakeBlockRepository()
        val fileSystem = FakeFileSystem()
        val graphLoader = GraphLoader(fileSystem, pageRepo, blockRepo)
        // Create a viewmodel with empty block repo so journals show placeholder
        val emptyBlockRepo = com.logseq.kmp.ui.fixtures.FakeBlockRepository()
        val graphLoader2 = GraphLoader(fileSystem, pageRepo, emptyBlockRepo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = JournalsViewModel(pageRepo, emptyBlockRepo, graphLoader2, scope)

        composeTestRule.setContent {
            MaterialTheme {
                JournalsView(
                    viewModel = viewModel,
                    blockRepository = emptyBlockRepo,
                    isDebugMode = false,
                    onLinkClick = {},
                    onContentChange = { _, _, _, _ -> }
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasText("Click to write...", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Multiple "Click to write..." nodes exist (one per journal page) — assert at least one is shown
        composeTestRule.onAllNodes(
            androidx.compose.ui.test.hasText("Click to write...", substring = true)
        )[0].assertIsDisplayed()
    }
}
