package com.logseq.kmp.ui.editor

import com.logseq.kmp.model.Block
import com.logseq.kmp.repository.DatascriptBlockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private lateinit var repository: DatascriptBlockRepository
    private lateinit var viewModel: EditorViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = DatascriptBlockRepository()
        // In a real app we'd inject the scope/dispatcher, but for now we rely on the default in VM 
        // which defaults to Main (that we just set)
        viewModel = EditorViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadPage() = runTest(testDispatcher) {
        // Setup data
        val pageId = 1L
        val now = Clock.System.now()
        val uuid1 = "11111111-1111-1111-1111-111111111111"
        val uuid2 = "22222222-2222-2222-2222-222222222222"
        
        val block1 = Block(
            id = 100, uuid = uuid1, pageId = pageId,
            content = "Block 1", position = 0, createdAt = now, updatedAt = now
        )
        val block2 = Block(
            id = 101, uuid = uuid2, pageId = pageId,
            content = "Block 2", position = 1, createdAt = now, updatedAt = now
        )
        repository.saveBlock(block1)
        repository.saveBlock(block2)

        // Action
        viewModel.loadPage(pageId)
        
        // Advance coroutines
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify
        val blocks = viewModel.blocks.value
        assertEquals(2, blocks.size)
        // Note: Sort order depends on repository return. 
        // DatascriptBlockRepository.getBlocksForPage sorts by position.
        val b1 = blocks.find { it.uuid == uuid1 }
        val b2 = blocks.find { it.uuid == uuid2 }
        
        assertEquals("Block 1", b1?.content)
        assertEquals("Block 2", b2?.content)
    }

    @Test
    fun testUpdateContent() = runTest(testDispatcher) {
        // Setup
        val pageId = 1L
        val now = Clock.System.now()
        val uuid1 = "11111111-1111-1111-1111-111111111111"
        
        val block = Block(
            id = 100, uuid = uuid1, pageId = pageId,
            content = "Original", position = 0, createdAt = now, updatedAt = now
        )
        repository.saveBlock(block)
        viewModel.loadPage(pageId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Action
        viewModel.updateBlockContent(uuid1, "Updated")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify UI State
        assertEquals("Updated", viewModel.blocks.value[0].content)
    }
}
