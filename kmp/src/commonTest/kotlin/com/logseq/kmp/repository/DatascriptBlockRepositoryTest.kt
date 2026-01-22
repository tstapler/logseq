package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class DatascriptBlockRepositoryTest {

    private lateinit var repository: DatascriptBlockRepository
    private val now = Clock.System.now()

    @BeforeTest
    fun setup() {
        repository = DatascriptBlockRepository()
    }

    private fun createBlock(id: Long, parentId: Long? = null, position: Int, content: String = "Block $id"): Block {
        // Pad ID to make valid UUID: 00000000-0000-0000-0000-000000000001
        val uuidSuffix = id.toString().padStart(12, '0')
        val uuid = "00000000-0000-0000-0000-$uuidSuffix"
        
        return Block(
            id = id,
            uuid = uuid,
            pageId = 1,
            content = content,
            parentId = parentId,
            position = position,
            leftId = null, // simplified for helper, logic should handle it
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun testIndentBlock() = runTest {
        // Setup: B1 -> B2. Indent B2 into B1.
        val b1 = createBlock(1, position = 0)
        val b2 = createBlock(2, position = 1)
        val uuid2 = b2.uuid
        
        repository.saveBlock(b1)
        repository.saveBlock(b2)

        // Action
        val result = repository.indentBlock(uuid2)
        
        // Verify
        assertTrue(result.isSuccess, "Indent failed: ${result.exceptionOrNull()}")
        
        // Fetch B2 to check parent
        val res = repository.getBlockByUuid(uuid2).first()
        val block = res.getOrNull()
        assertNotNull(block)
        assertEquals(1L, block.parentId) // Should now be child of B1
    }

    @Test
    fun testMoveBlockUp() = runTest {
        // Setup: B1 -> B2. Move B2 up.
        val b1 = createBlock(1, position = 0)
        val b2 = createBlock(2, position = 1)
        val uuid1 = b1.uuid
        val uuid2 = b2.uuid
        
        repository.saveBlock(b1)
        repository.saveBlock(b2)
        
        // Action
        val result = repository.moveBlockUp(uuid2)
        assertTrue(result.isSuccess)
        
        // Verify positions
        val res = repository.getBlocksForPage(1).first()
        val blocks = res.getOrNull() ?: emptyList()
        assertEquals(2, blocks.size)
        assertEquals(uuid2, blocks[0].uuid) // B2 first
        assertEquals(uuid1, blocks[1].uuid) // B1 second
    }
    
    @Test
    fun testMoveBlockDown() = runTest {
        // Setup: B1 -> B2. Move B1 down.
        val b1 = createBlock(1, position = 0)
        val b2 = createBlock(2, position = 1)
        val uuid1 = b1.uuid
        val uuid2 = b2.uuid
        
        repository.saveBlock(b1)
        repository.saveBlock(b2)
        
        // Action
        val result = repository.moveBlockDown(uuid1)
        assertTrue(result.isSuccess)
        
        // Verify positions
        val res = repository.getBlocksForPage(1).first()
        val blocks = res.getOrNull() ?: emptyList()
        assertEquals(2, blocks.size)
        assertEquals(uuid2, blocks[0].uuid) // B2 first
        assertEquals(uuid1, blocks[1].uuid) // B1 second
    }
}
