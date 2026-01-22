package com.logseq.kmp.db

import com.logseq.kmp.platform.PlatformFileSystem
import com.logseq.kmp.repository.DatascriptBlockRepository
import com.logseq.kmp.repository.InMemorySimplePageRepository
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
        val pageRepository = InMemorySimplePageRepository()
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
        val blocksResult = blockRepository.getBlocksForPage(contentsPage.id).first()
        assertTrue(blocksResult.isSuccess, "Failed to get blocks for 'contents' page")
        val blocks = blocksResult.getOrNull() ?: emptyList()
        
        val newPage = pages.find { it.name == "new page" }!!
        val newPageBlocksResult = blockRepository.getBlocksForPage(newPage.id).first()
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
}
