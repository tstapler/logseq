package com.logseq.kmp.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.logseq.kmp.db.DriverFactory
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.SqlDelightBlockRepository
import com.logseq.kmp.repository.SqlDelightPageRepository
import com.logseq.kmp.ui.fixtures.FakeFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test

class JournalsViewSqlDelightTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun journalsView_rendersSqlDelightData() {
        val driver = DriverFactory().createDriver("jdbc:sqlite::memory:")
        val database = LogseqDatabase(driver)
        val pageRepo = SqlDelightPageRepository(database)
        val blockRepo = SqlDelightBlockRepository(database)
        val fileSystem = FakeFileSystem()
        val graphLoader = GraphLoader(fileSystem, pageRepo, blockRepo)
        
        // Seed database
        runBlocking {
            val now = Clock.System.now()
            pageRepo.savePage(
                Page(
                    id = 1L,
                    uuid = "00000000-0000-0000-0000-000000000001",
                    name = "2026_03_14",
                    createdAt = now,
                    updatedAt = now,
                    isJournal = true,
                    journalDate = LocalDate(2026, 3, 14)
                )
            )
        }

        val viewModel = JournalsViewModel(pageRepo, blockRepo, graphLoader, CoroutineScope(Dispatchers.Unconfined))

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

        // Check for date header
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasText("2026-03-14", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("2026-03-14", substring = true).assertIsDisplayed()
    }
}
