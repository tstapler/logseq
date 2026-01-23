package com.logseq.kmp.integration

import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.outliner.BlockSorter
import com.logseq.kmp.parsing.LogseqParser
import com.logseq.kmp.parser.MarkdownParser
import com.logseq.kmp.platform.FileSystem
import com.logseq.kmp.repository.InMemoryBlockRepository
import com.logseq.kmp.repository.InMemorySimplePageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PipelineReproductionTest {

    private val fileSystem = object : FileSystem {
        val files = mutableMapOf<String, String>()
        override fun getDefaultGraphPath() = "/graph"
        override fun expandTilde(path: String) = path
        override fun readFile(path: String) = files[path]
        override fun writeFile(path: String, content: String) = true
        // Fix: listFiles should return filenames only, filtered by directory
        override fun listFiles(path: String) = files.keys
            .filter { it.startsWith(path) && it != path }
            .map { it.substringAfterLast("/") }
            .distinct()
            
        override fun listDirectories(path: String) = emptyList<String>()
        override fun fileExists(path: String) = files.containsKey(path)
        override fun directoryExists(path: String) = true
        override fun createDirectory(path: String) = true
        override fun deleteFile(path: String) = true
        override fun pickDirectory() = null
    }

    private val pageRepository = InMemorySimplePageRepository()
    private val blockRepository = InMemoryBlockRepository()
    private val graphLoader = GraphLoader(fileSystem, pageRepository, blockRepository)

    @Test
    fun `reproduce ordering issue`() = runBlocking {
        val content = """
- Till Listening to [[The Knowledge: How to Rebuild Our World from Scratch]]
  - xTool MetalFab Laser Welder
  - Perhaps we need something like [[Fiver]]
  - [[Rediscovering Paper]]
    - You need to bathe the contents
    - [[iron Gall Ink]]
    - The movable type printing press
- Next Block
        """.trimIndent()

        fileSystem.files["/graph/pages/test.md"] = content
        
        graphLoader.loadGraph("/graph") { }
        
        val pages = pageRepository.getAllPages().first().getOrNull()!!
        val page = pages[0]
        val blocks = blockRepository.getBlocksForPage(page.id).first().getOrNull()!!
        
        println("Loaded ${blocks.size} blocks from repository")
        
        // Check "Rediscovering Paper" block
        val rediscovering = blocks.find { it.content == "[[Rediscovering Paper]]" }
        assertNotNull(rediscovering, "Rediscovering Paper block not found")
        
        val root = blocks.find { it.content.startsWith("Till Listening") }!!
        
        println("Root ID: ${root.id}")
        println("Rediscovering ID: ${rediscovering.id}, Parent ID: ${rediscovering.parentId}")
        
        // Verify parentage in DB
        assertEquals(root.id, rediscovering.parentId, "Rediscovering Paper should be child of Root in DB")
        
        // Verify Sorting
        val sorted = BlockSorter.sort(blocks)
        
        println("Sorted Order:")
        sorted.forEach { 
            val indent = "  ".repeat(it.level)
            println("$indent- ${it.content} (ID: ${it.id}, Parent: ${it.parentId})")
        }
        
        // Verify position in sorted list
        // Root should be first (or early)
        val rootIndex = sorted.indexOf(root)
        val rediscoveringIndex = sorted.indexOf(rediscovering)
        
        // Rediscovering should be AFTER Root
        assert(rediscoveringIndex > rootIndex)
        
        // Check if "xTool" and "Perhaps" come before "Rediscovering" (siblings)
        val xtool = blocks.find { it.content.startsWith("xTool") }!!
        val perhaps = blocks.find { it.content.startsWith("Perhaps") }!!
        
        val xtoolIndex = sorted.indexOf(xtool)
        val perhapsIndex = sorted.indexOf(perhaps)
        
        assert(xtoolIndex > rootIndex)
        assert(perhapsIndex > xtoolIndex)
        assert(rediscoveringIndex > perhapsIndex)
        
        // Check children of Rediscovering
        val bathing = blocks.find { it.content.startsWith("You need to bathe") }!!
        val ironGall = blocks.find { it.content.startsWith("[[iron Gall Ink]]") }!!
        val movable = blocks.find { it.content.startsWith("The movable") }!!
        
        val bathingIndex = sorted.indexOf(bathing)
        val ironGallIndex = sorted.indexOf(ironGall)
        val movableIndex = sorted.indexOf(movable)
        
        assert(bathingIndex > rediscoveringIndex)
        assertEquals(rediscovering.id, bathing.parentId, "Bathing should be child of Rediscovering")
        assertEquals(2, bathing.level, "Bathing should be level 2")
        
        assert(ironGallIndex > bathingIndex)
        assertEquals(rediscovering.id, ironGall.parentId, "Iron Gall should be child of Rediscovering")
        assertEquals(2, ironGall.level, "Iron Gall should be level 2")
        
        assert(movableIndex > ironGallIndex)
        assertEquals(rediscovering.id, movable.parentId, "Movable should be child of Rediscovering")
        assertEquals(2, movable.level, "Movable should be level 2")
    }
}
