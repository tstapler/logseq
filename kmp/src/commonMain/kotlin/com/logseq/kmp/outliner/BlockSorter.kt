package com.logseq.kmp.outliner

import com.logseq.kmp.model.Block

object BlockSorter {
    /**
     * Sorts a flat list of blocks into a hierarchical depth-first order (visual display order).
     * 
     * Algorithm:
     * 1. Group blocks by parentId.
     * 2. Find root blocks (parentId = null or parent not in list).
     * 3. Sort roots by position/index.
     * 4. Recursively append children (sorted by position) for each block.
     */
    fun sort(blocks: List<Block>): List<Block> {
        if (blocks.isEmpty()) return emptyList()

        val childrenByParent = blocks.groupBy { it.parentId }
        val allBlockIds = blocks.map { it.id }.toSet()

        // Roots are blocks with no parent OR parent is not in the current list
        // Sort descending because we'll push them onto a stack (LIFO)
        val roots = blocks.filter { 
            it.parentId == null || !allBlockIds.contains(it.parentId) 
        }.sortedWith(compareByDescending<Block> { it.position }.thenByDescending { it.id })

        val result = mutableListOf<Block>()
        val visited = mutableSetOf<Long>()
        // Stack stores pair of (Block, ActualLevel) to repair levels during traversal
        val stack = mutableListOf<Pair<Block, Int>>()
        
        roots.forEach { stack.add(it to 0) }
        
        while (stack.isNotEmpty()) {
            val (block, actualLevel) = stack.removeAt(stack.size - 1)
            if (visited.contains(block.id)) continue
            
            visited.add(block.id)
            
            // Repair the level if it doesn't match the actual depth in the hierarchy
            val repairedBlock = if (block.level != actualLevel) {
                block.copy(level = actualLevel)
            } else {
                block
            }
            result.add(repairedBlock)
            
            // Push children in reverse order so the first child is popped first
            val children = childrenByParent[block.id]
                ?.sortedWith(compareByDescending<Block> { it.position }.thenByDescending { it.id }) 
                ?: emptyList()
            
            children.forEach { stack.add(it to actualLevel + 1) }
        }

        // Sanity check: If we missed any blocks (e.g. cycles), add them at the end
        if (result.size < blocks.size) {
            val remaining = blocks.filter { !visited.contains(it.id) }
            println("BlockSorter WARNING: ${remaining.size} orphaned blocks found (orphaned from hierarchy or cycle). Appending to end.")
            remaining.forEach { println(" - Orphan: ${it.content} (Parent: ${it.parentId})") }
            result.addAll(remaining)
        }

        return result
    }
}
