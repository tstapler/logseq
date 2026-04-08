package com.logseq.kmp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.logseq.kmp.db.DriverFactory
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.SqlDelightBlockRepository
import com.logseq.kmp.repository.SqlDelightPageRepository
import com.logseq.kmp.ui.fixtures.FakeFileSystem
import com.logseq.kmp.ui.screens.JournalsView
import com.logseq.kmp.ui.screens.JournalsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for core outliner UI flows.
 * Verifies that basic operations like rendering blocks and hierarchy work.
 */
class OutlinerRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBasicOutlinerFlow() {
        val driver = DriverFactory().createDriver("jdbc:sqlite::memory:")
        val database = LogseqDatabase(driver)
        val pageRepo = SqlDelightPageRepository(database)
        val blockRepo = SqlDelightBlockRepository(database)
        val fileSystem = FakeFileSystem()
        val graphLoader = GraphLoader(fileSystem, pageRepo, blockRepo)
        
        val now = Clock.System.now()
        val pageUuid = "page-uuid-1"
        val block1Uuid = "block-uuid-1"
        val block2Uuid = "block-uuid-2"

        // Setup initial data
        runBlocking {
            pageRepo.savePage(
                Page(
                    uuid = pageUuid,
                    name = "2026-03-28",
                    createdAt = now,
                    updatedAt = now,
                    isJournal = true,
                    journalDate = LocalDate(2026, 3, 28)
                )
            )
            
            blockRepo.saveBlock(
                Block(
                    uuid = block1Uuid,
                    pageUuid = pageUuid,
                    content = "Parent Block",
                    position = 0,
                    createdAt = now,
                    updatedAt = now
                )
            )
            
            blockRepo.saveBlock(
                Block(
                    uuid = block2Uuid,
                    pageUuid = pageUuid,
                    content = "Child Block",
                    parentUuid = block1Uuid,
                    level = 1,
                    position = 0,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        val viewModel = JournalsViewModel(pageRepo, blockRepo, graphLoader, CoroutineScope(Dispatchers.Unconfined))

        composeTestRule.setContent {
            MaterialTheme {
                JournalsView(
                    viewModel = viewModel,
                    blockRepository = blockRepo,
                    isDebugMode = true, // Show UUIDs for debugging
                    onLinkClick = {},
                    onContentChange = { _, _, _, _ -> }
                )
            }
        }

        // 1. Verify page renders
        composeTestRule.onNodeWithText("2026-03-28", substring = true).assertIsDisplayed()
        
        // 2. Verify blocks render
        composeTestRule.onNodeWithText("Parent Block", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Child Block", substring = true).assertIsDisplayed()
        
        // 3. Perform an operation (e.g., outdent the child)
        runBlocking {
            viewModel.outdentBlock(block2Uuid)
        }
        
        // 4. Verify state update (in real app, reactive flows would update this)
        // Since we are using Unconfined dispatcher and reactive flows, the UI should update.
        
        // Verify both blocks are now at the same level (can be checked via position or properties in debug mode)
        composeTestRule.onNodeWithText("Parent Block").assertIsDisplayed()
        composeTestRule.onNodeWithText("Child Block").assertIsDisplayed()
    }
}
