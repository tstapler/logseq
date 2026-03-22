package com.logseq.kmp.outliner

import com.logseq.kmp.model.Block
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockSorterLevelRepairTest {

    private fun createBlock(id: Long, parentId: Long?, content: String, position: Int, level: Int): Block {
        val uuid = "00000000-0000-0000-0000-${id.toString().padStart(12, '0')}"
        return Block(
            id = id,
            uuid = uuid,
            pageId = 1L,
            parentId = parentId,
            content = content,
            level = level,
            position = position,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            properties = emptyMap()
        )
    }

    @Test
    fun `test level repair`() {
        // Create blocks with incorrect levels
        // Root (level 10 - WRONG, should be 0)
        //   - Child (level 0 - WRONG, should be 1)
        //     - Grandchild (level 5 - WRONG, should be 2)
        
        val root = createBlock(1, null, "Root", 0, 10)
        val child = createBlock(2, 1, "Child", 0, 0)
        val grandchild = createBlock(3, 2, "Grandchild", 0, 5)
        
        val input = listOf(grandchild, root, child)
        val sorted = BlockSorter.sort(input)
        
        assertEquals(3, sorted.size)
        
        assertEquals("Root", sorted[0].content)
        assertEquals(0, sorted[0].level, "Root level should be repaired to 0")
        
        assertEquals("Child", sorted[1].content)
        assertEquals(1, sorted[1].level, "Child level should be repaired to 1")
        
        assertEquals("Grandchild", sorted[2].content)
        assertEquals(2, sorted[2].level, "Grandchild level should be repaired to 2")
    }

    @Test
    fun `test stable sorting with duplicate positions`() {
        // Blocks with same position should be sorted by ID descending to be stable
        val b1 = createBlock(1, null, "B1", 0, 0)
        val b2 = createBlock(2, null, "B2", 0, 0)
        val b3 = createBlock(3, null, "B3", 0, 0)
        
        val sorted = BlockSorter.sort(listOf(b1, b2, b3))
        println("Sorted IDs: ${sorted.map { it.id }}")
        
        // roots.sortedWith(compareByDescending<Block> { it.position }.thenByDescending { it.id })
        // stack order (bottom to top): ID 3, ID 2, ID 1
        // pop order: ID 1, ID 2, ID 3
        
        assertEquals(3, sorted.size)
        assertEquals(1L, sorted[0].id)
        assertEquals(2L, sorted[1].id)
        assertEquals(3L, sorted[2].id)
    }
}
