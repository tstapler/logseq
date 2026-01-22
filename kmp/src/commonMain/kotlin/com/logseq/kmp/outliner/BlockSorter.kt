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
        val roots = blocks.filter { 
            it.parentId == null || !allBlockIds.contains(it.parentId) 
        }.sortedBy { it.position }

        val result = mutableListOf<Block>()
        
        fun appendBlockAndChildren(block: Block) {
            result.add(block)
            val children = childrenByParent[block.id]?.sortedBy { it.position } ?: emptyList()
            children.forEach { appendBlockAndChildren(it) }
        }

        roots.forEach { appendBlockAndChildren(it) }

        // Sanity check: If we missed any blocks (e.g. cycles or detached orphans), add them at the end
        // This prevents data loss in UI, though order might be weird
        if (result.size < blocks.size) {
            val processedIds = result.map { it.id }.toSet()
            val remaining = blocks.filter { !processedIds.contains(it.id) }
            result.addAll(remaining)
        }

        return result
    }
}
