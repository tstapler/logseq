package com.logseq.kmp.outliner

import com.logseq.kmp.model.Block
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TreeOperationsTest {

    private fun createBlock(id: Long, parentId: Long? = null, leftId: Long? = null, position: Int = 0, level: Int = 0): Block {
        val idStr = id.toString().padStart(12, '0')
        return Block(
            id = id,
            uuid = "00000000-0000-0000-0000-$idStr",
            content = "Block $id",
            pageId = 1L,
            parentId = parentId,
            leftId = leftId,
            position = position,
            level = level,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0)
        )
    }

    @Test
    fun testIndent() {
        // Setup: B1 -> B2. Indent B2 to be child of B1.
        val b1 = createBlock(1)
        val b2 = createBlock(2, leftId = 1, position = 1)
        val siblings = listOf(b1, b2)

        // Indent B2. B1 has no children, so lastChildOfNewParent is null.
        val result = TreeOperations.indent(b2, siblings, null)
        assertNotNull(result)
        assertEquals(1, result.size) // No next sibling to update
        
        val indented = result[0]
        assertEquals(1L, indented.parentId)
        assertEquals(1, indented.level)
        assertNull(indented.leftId) // First child of B1
    }

    @Test
    fun testIndentWithGapClosing() {
        // Setup: B1 -> B2 -> B3
        val b1 = createBlock(1, position = 0)
        val b2 = createBlock(2, leftId = 1, position = 1)
        val b3 = createBlock(3, leftId = 2, position = 2)
        val siblings = listOf(b1, b2, b3)

        // Indent B2 into B1.
        val result = TreeOperations.indent(b2, siblings, null)
        assertNotNull(result)
        assertEquals(2, result.size) // B2 updated, B3 updated
        
        val indentedB2 = result.find { it.id == 2L }!!
        val updatedB3 = result.find { it.id == 3L }!!

        // Check B2
        assertEquals(1L, indentedB2.parentId)
        
        // Check B3: Should now point to B1 (closing the gap)
        assertEquals(1L, updatedB3.leftId)
    }

    @Test
    fun testIndentWithExistingChildren() {
        // Setup: B1 -> B2. B1 already has child C1.
        val b1 = createBlock(1)
        val b2 = createBlock(2, leftId = 1, position = 1)
        val c1 = createBlock(10, parentId = 1, level = 1) // Child of B1
        val siblings = listOf(b1, b2)

        // Indent B2 into B1. Provide C1 as the last child.
        val result = TreeOperations.indent(b2, siblings, lastChildOfNewParent = c1)
        assertNotNull(result)
        
        val indentedB2 = result[0]
        assertEquals(1L, indentedB2.parentId)
        assertEquals(10L, indentedB2.leftId) // Should follow C1
    }


    @Test
    fun testIndentFirstSiblingFails() {
        val b1 = createBlock(1)
        val result = TreeOperations.indent(b1, listOf(b1))
        assertNull(result)
    }

    @Test
    fun testOutdent() {
        // Setup: Parent -> B2 (Child). Parent is sibling of Uncle.
        val parent = createBlock(1, leftId = null)
        val uncle = createBlock(99, leftId = 1) // Follows Parent
        
        val b2 = createBlock(2, parentId = 1, level = 1, leftId = null) // First child of Parent
        val b3 = createBlock(3, parentId = 1, level = 1, leftId = 2) // Second child of Parent

        val siblings = listOf(b2, b3)
        val parentSiblings = listOf(parent, uncle)
        
        // Outdent B2. It should become a sibling of Parent, between Parent and Uncle.
        val result = TreeOperations.outdent(b2, parent, siblings, parentSiblings)
        assertNotNull(result)
        assertEquals(3, result.size) // B2, B3 (gap close), Uncle (gap open)
        
        val outdentedB2 = result.find { it.id == 2L }!!
        val updatedB3 = result.find { it.id == 3L }!!
        val updatedUncle = result.find { it.id == 99L }!!

        // Check B2
        assertNull(outdentedB2.parentId) // Top level now
        assertEquals(0, outdentedB2.level)
        assertEquals(1L, outdentedB2.leftId) // Follows Parent

        // Check B3 (Gap closed in children list)
        assertNull(updatedB3.leftId) // Was pointing to 2, now first child (null)
        
        // Check Uncle (Gap opened in parent list)
        assertEquals(2L, updatedUncle.leftId) // Now follows B2
    }

    @Test
    fun testMoveUp() {
        val b1 = createBlock(1)
        val b2 = createBlock(2, leftId = 1, position = 1)
        val siblings = listOf(b1, b2)

        val result = TreeOperations.moveUp(b2, siblings)
        assertNotNull(result)
        assertEquals(2, result.size)
        
        val updatedB2 = result.find { it.id == 2L }!!
        val updatedB1 = result.find { it.id == 1L }!!

        assertNull(updatedB2.leftId)
        assertEquals(2L, updatedB1.leftId)
    }

    @Test
    fun testMoveDown() {
        val b1 = createBlock(1)
        val b2 = createBlock(2, leftId = 1, position = 1)
        val siblings = listOf(b1, b2)

        val result = TreeOperations.moveDown(b1, siblings)
        assertNotNull(result)
        assertEquals(2, result.size)

        val updatedB1 = result.find { it.id == 1L }!!
        val updatedB2 = result.find { it.id == 2L }!!

        assertEquals(2L, updatedB1.leftId)
        assertNull(updatedB2.leftId)
    }

    @Test
    fun testReorderSiblings() {
        val b1 = createBlock(1, leftId = 99, position = 5)
        val b2 = createBlock(2, leftId = 1, position = 6)
        val b3 = createBlock(3, leftId = 2, position = 7)
        
        val reordered = TreeOperations.reorderSiblings(listOf(b1, b2, b3))
        
        assertEquals(0, reordered[0].position)
        assertNull(reordered[0].leftId)
        
        assertEquals(1, reordered[1].position)
        assertEquals(1L, reordered[1].leftId)
        
        assertEquals(2, reordered[2].position)
        assertEquals(2L, reordered[2].leftId)
    }
}
