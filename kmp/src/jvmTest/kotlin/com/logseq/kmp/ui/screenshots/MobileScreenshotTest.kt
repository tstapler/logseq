package com.logseq.kmp.ui.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.ui.fixtures.FakeFileSystem
import com.logseq.kmp.ui.fixtures.PopulatedFakeBlockRepository
import com.logseq.kmp.ui.fixtures.PopulatedFakePageRepository
import com.logseq.kmp.ui.screens.JournalsView
import com.logseq.kmp.ui.screens.JournalsViewModel
import com.logseq.kmp.ui.theme.LogseqTheme
import com.logseq.kmp.ui.theme.LogseqThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class MobileScreenshotTest {

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
    fun mobile_journals_light() {
        val viewModel = makeJournalsViewModel()
        val blockRepo = PopulatedFakeBlockRepository()

        composeTestRule.setContent {
            LogseqTheme(themeMode = LogseqThemeMode.LIGHT) {
                Box(
                    modifier = Modifier
                        .clipToBounds()
                ) {
                    JournalsView(
                        viewModel = viewModel,
                        blockRepository = blockRepo,
                        isDebugMode = false,
                        onLinkClick = {},
                        onContentChange = { _, _, _, _ -> }
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/mobile_journals_light.png")
    }

    @Test
    fun mobile_journals_dark() {
        val viewModel = makeJournalsViewModel()
        val blockRepo = PopulatedFakeBlockRepository()

        composeTestRule.setContent {
            LogseqTheme(themeMode = LogseqThemeMode.DARK) {
                Box(
                    modifier = Modifier
                        .clipToBounds()
                ) {
                    JournalsView(
                        viewModel = viewModel,
                        blockRepository = blockRepo,
                        isDebugMode = false,
                        onLinkClick = {},
                        onContentChange = { _, _, _, _ -> }
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/mobile_journals_dark.png")
    }
}
