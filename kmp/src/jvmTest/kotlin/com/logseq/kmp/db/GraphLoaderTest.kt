package com.logseq.kmp.db

import com.logseq.kmp.platform.PlatformFileSystem
import com.logseq.kmp.repository.DatascriptBlockRepository
import com.logseq.kmp.repository.InMemoryPageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class GraphLoaderTest {

    @Test
    fun testLoadDemoGraph() {
        println("Starting test...")
        runBlocking {
            val fileSystem = PlatformFileSystem()
            println("FileSystem created")
        val pageRepository = InMemoryPageRepository()
        println("PageRepository created")
        val blockRepository = DatascriptBlockRepository()
        println("BlockRepository created")
        val graphLoader = GraphLoader(fileSystem, pageRepository, blockRepository)
        println("GraphLoader created")

        val userDir = System.getProperty("user.dir")
        val projectRoot = if (userDir.endsWith("/kmp") || userDir.endsWith("\\kmp")) {
            File(userDir).parentFile
        } else {
            File(userDir)
        }
        val graphPath = File(projectRoot, "deps/graph-parser/test/resources/exporter-test-graph").absolutePath

        println("Loading graph from: $graphPath")
        println("Graph path exists: ${File(graphPath).exists()}")
        println("Graph path is directory: ${File(graphPath).isDirectory}")
        println("Pages dir exists: ${File(graphPath, "pages").exists()}")
        
        graphLoader.loadGraph(graphPath) { progress ->
            println("Progress: $progress")
        }
        println("Graph loaded")

        // Verify pages
        val pagesResult = pageRepository.getAllPages().first()
        assertTrue(pagesResult.isSuccess, "Failed to get pages")
        val pages = pagesResult.getOrNull() ?: emptyList()
        println("Loaded ${pages.size} pages")
        assertTrue(pages.isNotEmpty(), "No pages loaded")

        // Verify specific pages exist
        val expectedPages = listOf("contents", "new page", "some page")
        expectedPages.forEach { expectedName ->
            val page = pages.find { it.name == expectedName }
            assertNotNull(page, "Page '$expectedName' not found")
        }
        println("Verified expected pages")

        // Verify blocks
        val contentsPage = pages.find { it.name == "contents" }!!
        val blocksResult = blockRepository.getBlocksForPage(contentsPage.uuid).first()
        assertTrue(blocksResult.isSuccess, "Failed to get blocks for 'contents' page")
        val blocks = blocksResult.getOrNull() ?: emptyList()
        
        val newPage = pages.find { it.name == "new page" }!!
        val newPageBlocksResult = blockRepository.getBlocksForPage(newPage.uuid).first()
        val newPageBlocks = newPageBlocksResult.getOrNull() ?: emptyList()
        
        assertTrue(blocks.isNotEmpty() || newPageBlocks.isNotEmpty(), "No blocks loaded for 'contents' or 'new page'")
        
        if (blocks.isNotEmpty()) {
             println("Loaded ${blocks.size} blocks for 'contents' page")
        }
        if (newPageBlocks.isNotEmpty()) {
             println("Loaded ${newPageBlocks.size} blocks for 'new page' page")
        }
        }
    }

    @Test
    fun testLoadGraphProgressive() {
        runBlocking {
            val fileSystem = PlatformFileSystem()
            val pageRepository = InMemoryPageRepository()
            val blockRepository = DatascriptBlockRepository()
            val graphLoader = GraphLoader(fileSystem, pageRepository, blockRepository)

            val userDir = System.getProperty("user.dir")
            val projectRoot = if (userDir.endsWith("/kmp") || userDir.endsWith("\\kmp")) {
                File(userDir).parentFile
            } else {
                File(userDir)
            }
            val graphPath = File(projectRoot, "deps/graph-parser/test/resources/exporter-test-graph").absolutePath

            var phase1Complete = false
            var fullyLoaded = false

            graphLoader.loadGraphProgressive(
                graphPath = graphPath,
                immediateJournalCount = 5,
                onProgress = { println("Progress: $it") },
                onPhase1Complete = { phase1Complete = true },
                onFullyLoaded = { fullyLoaded = true }
            )

            assertTrue(phase1Complete, "Phase 1 should be complete")
            assertTrue(fullyLoaded, "Graph should be fully loaded")

            // Verify pages loaded
            val pagesResult = pageRepository.getAllPages().first()
            val pages = pagesResult.getOrNull() ?: emptyList()
            assertTrue(pages.isNotEmpty(), "Pages should be loaded")

            // Verify a page is loaded in METADATA_ONLY mode (isLoaded = false)
            // "contents" page is likely not a journal, so it should be loaded in background (METADATA_ONLY)
            val contentsPage = pages.find { it.name == "contents" }
            assertNotNull(contentsPage, "contents page should exist")
            
            val blocksResult = blockRepository.getBlocksForPage(contentsPage.uuid).first()
            val blocks = blocksResult.getOrNull() ?: emptyList()
            assertTrue(blocks.isNotEmpty(), "Blocks should be loaded for contents page")
            
            // Check isLoaded flag
            // Since loadGraphProgressive uses METADATA_ONLY for pages, blocks should have isLoaded = false
            val firstBlock = blocks.firstOrNull()
            assertNotNull(firstBlock, "First block should not be null")
            assertEquals(false, firstBlock.isLoaded, "Block should not be fully loaded initially") 
            
            // Now load full page
            graphLoader.loadFullPage(contentsPage.uuid)
            
            // Verify blocks are now loaded
            val reloadedBlocksResult = blockRepository.getBlocksForPage(contentsPage.uuid).first()
            val reloadedBlocks = reloadedBlocksResult.getOrNull() ?: emptyList()
            val reloadedFirstBlock = reloadedBlocks.firstOrNull()
            assertNotNull(reloadedFirstBlock, "Reloaded first block should not be null")
            assertEquals(true, reloadedFirstBlock.isLoaded, "Block should be fully loaded after loadFullPage")
        }
    }
}
