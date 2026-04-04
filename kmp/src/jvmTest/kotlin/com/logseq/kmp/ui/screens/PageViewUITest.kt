package com.logseq.kmp.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.db.GraphWriter
import com.logseq.kmp.platform.PlatformFileSystem
import com.logseq.kmp.platform.PlatformSettings
import com.logseq.kmp.repository.InMemorySearchRepository
import com.logseq.kmp.ui.LogseqViewModel
import com.logseq.kmp.ui.fixtures.FakeFileSystem
import com.logseq.kmp.ui.fixtures.PopulatedFakeBlockRepository
import com.logseq.kmp.ui.fixtures.PopulatedFakePageRepository
import com.logseq.kmp.ui.fixtures.TestFixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class PageViewUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeViewModel(
        pageRepo: PopulatedFakePageRepository,
        blockRepo: PopulatedFakeBlockRepository
    ): LogseqViewModel {
        val platformFileSystem = PlatformFileSystem()
        val searchRepo = InMemorySearchRepository()
        val graphLoader = GraphLoader(FakeFileSystem(), pageRepo, blockRepo)
        val graphWriter = GraphWriter(platformFileSystem)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return LogseqViewModel(
            fileSystem = platformFileSystem,
            pageRepository = pageRepo,
            blockRepository = blockRepo,
            searchRepository = searchRepo,
            graphLoader = graphLoader,
            graphWriter = graphWriter,
            platformSettings = PlatformSettings(),
            scope = scope
        )
    }

    @Test
    fun pageView_showsPageTitle() {
        val page = TestFixtures.samplePage()
        val pageRepo = PopulatedFakePageRepository()
        val blockRepo = PopulatedFakeBlockRepository()
        val viewModel = makeViewModel(pageRepo, blockRepo)

        composeTestRule.setContent {
            MaterialTheme {
                PageView(
                    page = page,
                    blockRepository = blockRepo,
                    pageRepository = pageRepo,
                    graphWriter = GraphWriter(PlatformFileSystem()),
                    graphLoader = GraphLoader(FakeFileSystem(), pageRepo, blockRepo),
                    currentGraphPath = "${System.getProperty("user.home")}/logseq_test_ui",
                    onToggleFavorite = {},
                    onRefresh = {},
                    onLinkClick = {},
                    viewModel = viewModel,
                    isDebugMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("Test Page").assertIsDisplayed()
    }

    @Test
    fun pageView_showsEmptyStatePlaceholder() {
        val page = TestFixtures.samplePage()
        val pageRepo = PopulatedFakePageRepository()
        // Use empty block repo so page shows placeholder
        val emptyBlockRepo = com.logseq.kmp.ui.fixtures.FakeBlockRepository()
        val searchRepo = InMemorySearchRepository()
        val platformFileSystem = PlatformFileSystem()
        val graphLoader = GraphLoader(FakeFileSystem(), pageRepo, emptyBlockRepo)
        val graphWriter = GraphWriter(platformFileSystem)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = LogseqViewModel(
            fileSystem = platformFileSystem,
            pageRepository = pageRepo,
            blockRepository = emptyBlockRepo,
            searchRepository = searchRepo,
            graphLoader = graphLoader,
            graphWriter = graphWriter,
            platformSettings = PlatformSettings(),
            scope = scope
        )

        composeTestRule.setContent {
            MaterialTheme {
                PageView(
                    page = page,
                    blockRepository = emptyBlockRepo,
                    pageRepository = pageRepo,
                    graphWriter = graphWriter,
                    graphLoader = graphLoader,
                    currentGraphPath = "${System.getProperty("user.home")}/logseq_test_ui",
                    onToggleFavorite = {},
                    onRefresh = {},
                    onLinkClick = {},
                    viewModel = viewModel,
                    isDebugMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("Click to write...").assertIsDisplayed()
    }
}
