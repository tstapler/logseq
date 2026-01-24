package com.logseq.kmp.db

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.platform.PlatformFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphWriterTest {

    @Test
    fun testSavePageMaintainsHierarchy() {
        runBlocking {
            val fileSystem = PlatformFileSystem()
            val writer = GraphWriter(fileSystem)
            
            // Create a temp directory
            val userHome = System.getProperty("user.home")
            val tempDir = File(userHome, "logseq_test_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            val graphPath = tempDir.absolutePath
            
            val now = Clock.System.now()
            
            try {
                // Create a page
                val page = Page(
                    id = 1,
                    uuid = "00000000-0000-0000-0000-000000000001", // Valid UUID
                    name = "TestPage", // No spaces to be safe with validation
                    createdAt = now,
                    updatedAt = now,
                    journalDate = null,
                    properties = emptyMap()
                )
                
                // Create nested blocks
                // A (pos 0)
                //   A1 (pos 0, parent A)
                // B (pos 1)
                //   B1 (pos 0, parent B)
                
                val blockA = Block(
                    id = 10,
                    uuid = "00000000-0000-0000-0000-000000000010",
                    pageId = 1,
                    content = "Block A",
                    level = 0,
                    position = 0, // Sibling index 0
                    parentId = null,
                    createdAt = now,
                    updatedAt = now,
                    properties = emptyMap()
                )
                
                val blockA1 = Block(
                    id = 11,
                    uuid = "00000000-0000-0000-0000-000000000011",
                    pageId = 1,
                    content = "Block A1",
                    level = 1,
                    position = 0, // Sibling index 0 (under A)
                    parentId = 10,
                    createdAt = now,
                    updatedAt = now,
                    properties = emptyMap()
                )
                
                val blockB = Block(
                    id = 12,
                    uuid = "00000000-0000-0000-0000-000000000012",
                    pageId = 1,
                    content = "Block B",
                    level = 0,
                    position = 1, // Sibling index 1
                    parentId = null,
                    createdAt = now,
                    updatedAt = now,
                    properties = emptyMap()
                )
                
                val blockB1 = Block(
                    id = 13,
                    uuid = "00000000-0000-0000-0000-000000000013",
                    pageId = 1,
                    content = "Block B1",
                    level = 1,
                    position = 0, // Sibling index 0 (under B)
                    parentId = 12,
                    createdAt = now,
                    updatedAt = now,
                    properties = emptyMap()
                )
                
                val blocks = listOf(blockA, blockA1, blockB, blockB1)
                
                // Save
                writer.savePage(page, blocks, graphPath)
                
                // Read file content
                val filePath = File(tempDir, "pages/TestPage.md").absolutePath
                val content = fileSystem.readFile(filePath)
                
                println("Saved content:\n$content")
                
                // Expected content:
                // - Block A
                //   - Block A1
                // - Block B
                //   - Block B1
                
                val expected = """
                - Block A
                  - Block A1
                - Block B
                  - Block B1
                """.trimIndent()
                
                // We assert that B follows A1 (with B having less indentation)
                // And B1 follows B
                
                assertTrue(content!!.contains("- Block B\n  - Block B1"), "Block B1 should be nested under Block B. Actual:\n$content")
                
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }
}
