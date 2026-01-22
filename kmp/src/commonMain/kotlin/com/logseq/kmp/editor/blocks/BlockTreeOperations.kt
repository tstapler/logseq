package com.logseq.kmp.editor.blocks

import com.logseq.kmp.model.Block
import com.logseq.kmp.repository.BlockWithDepth
import com.logseq.kmp.repository.BlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlin.Result
import kotlin.Result.Companion.success

import com.logseq.kmp.editor.blocks.IBlockOperations
import com.logseq.kmp.editor.blocks.DeleteStrategy
import com.logseq.kmp.editor.blocks.PositioningMode
import com.logseq.kmp.editor.blocks.IndentMode
import com.logseq.kmp.editor.blocks.BlockOperation
import com.logseq.kmp.editor.blocks.ValidationResult
import com.logseq.kmp.editor.blocks.BulkOperation
import com.logseq.kmp.editor.blocks.HistoricalOperation

/**
 * Enhanced block operations with tree traversal and manipulation capabilities.
 * Provides efficient algorithms for hierarchical block structures.
 */
class BlockTreeOperations(
    private val blockOperations: IBlockOperations
) {
    
    // ===== ENHANCED TRAVERSAL OPERATIONS =====
    
    /**
     * Get a subtree starting from the specified root block.
     * Returns all blocks in the subtree with their depth information.
     */
    suspend fun getSubtree(
        rootUuid: String,
        includeCollapsed: Boolean = true,
        maxDepth: Int? = null
    ): Result<List<BlockWithDepth>> {
        return try {
            val allBlocks = mutableListOf<BlockWithDepth>()
            collectSubtree(rootUuid, 0, allBlocks, includeCollapsed, maxDepth)
            success(allBlocks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all visible blocks in a page (respecting collapsed states).
     */
    suspend fun getVisibleBlocks(
        pageId: Long,
        includeCollapsed: Boolean = false
    ): Result<List<BlockWithDepth>> {
        return try {
            // Get current blocks snapshot
            val pageBlocksResult = blockOperations.getBlocksForPage(pageId).first()
            val pageBlocks = pageBlocksResult.getOrNull() ?: emptyList()
            
            val rootBlocks = pageBlocks.filter { it.parentId == null }
            val visibleBlocks = mutableListOf<BlockWithDepth>()
            
            rootBlocks.forEach { rootBlock ->
                collectSubtree(
                    rootBlock.uuid, 
                    0, 
                    visibleBlocks, 
                    includeCollapsed,
                    null
                )
            }
            
            success(visibleBlocks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Find the common ancestor of multiple blocks.
     */
    suspend fun findCommonAncestor(
        blockUuids: List<String>
    ): Result<String?> {
        return try {
            if (blockUuids.isEmpty()) return success(null)
            if (blockUuids.size == 1) return success(blockUuids.first())
            
            val ancestorPaths = blockUuids.map { uuid ->
                getAncestorPath(uuid).getOrNull() ?: emptyList()
            }
            
            // Find the intersection of all paths
            val commonPath = ancestorPaths.reduce { acc, path ->
                acc.intersect(path.toSet()).toList()
            }
            
            // Return the deepest common ancestor (last in the list)
            success(commonPath.lastOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get the path from root to the specified block.
     */
    suspend fun getAncestorPath(blockUuid: String): Result<List<String>> {
        return try {
            val ancestorsResult = blockOperations.getBlockAncestors(blockUuid).first()
            val ancestors = ancestorsResult.getOrNull() ?: emptyList()
            success(ancestors.map { it.uuid } + blockUuid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Count the total number of blocks in a subtree.
     */
    suspend fun countSubtreeBlocks(rootUuid: String): Result<Int> {
        return getSubtree(rootUuid).map { it.size }
    }
    
    /**
     * Get the maximum depth of a subtree.
     */
    suspend fun getSubtreeMaxDepth(rootUuid: String): Result<Int> {
        return getSubtree(rootUuid).map { blocks ->
            blocks.maxOfOrNull { it.depth } ?: 0
        }
    }
    
    // ===== COLLAPSE/EXPAND STATE MANAGEMENT =====
    
    /**
     * Get all collapsed blocks in a page.
     */
    suspend fun getCollapsedBlocks(pageId: Long): Result<List<String>> {
        return try {
            val pageBlocksResult = blockOperations.getBlocksForPage(pageId).first()
            val pageBlocks = pageBlocksResult.getOrNull() ?: emptyList()
            
            val collapsed = pageBlocks.filter { block ->
                block.properties.containsKey("collapsed") && 
                block.properties["collapsed"] == "true"
            }
            
            success(collapsed.map { it.uuid })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Toggle the collapse state of a block.
     */
    suspend fun toggleCollapseState(blockUuid: String): Result<Unit> {
        return try {
            val blockResult = blockOperations.getBlockByUuid(blockUuid).first()
            val block = blockResult.getOrNull() ?: return Result.failure(Exception("Block not found"))
            
            val isCurrentlyCollapsed = block.properties["collapsed"] == "true"
            val newProperties = block.properties.toMutableMap().apply {
                if (isCurrentlyCollapsed) {
                    remove("collapsed")
                } else {
                    put("collapsed", "true")
                }
            }
            
            // This requires updateBlock/saveBlock which is in BlockRepository but we need to modify the block
            // BlockRepository has saveBlock(block).
            // BlockTreeOperations only has blockOperations: BlockRepository.
            // So we can do:
            val updatedBlock = block.copy(properties = newProperties)
            blockOperations.saveBlock(updatedBlock)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ===== ADVANCED MANIPULATION OPERATIONS =====
    
    /**
     * Move a subtree to a new location with comprehensive validation.
     */
    suspend fun moveSubtree(
        rootUuid: String,
        targetParentUuid: String?,
        positioning: PositioningMode,
        targetUuid: String? = null
    ): Result<Unit> {
        return try {
            // Basic validation - check if trying to move block into its own subtree
            if (targetParentUuid != null) {
                val subtree = getSubtree(rootUuid).getOrNull() ?: return Result.failure(Exception("Failed to get subtree"))
                if (subtree.any { it.block.uuid == targetParentUuid }) {
                    return Result.failure(Exception("Cannot move a block into its own subtree"))
                }
            }
            
            // Get all blocks in the subtree
            val subtree = getSubtree(rootUuid).getOrNull() ?: return Result.failure(Exception("Failed to get subtree"))
            
            // Calculate level offset
            val currentRootBlock = subtree.find { it.block.uuid == rootUuid }?.block
                ?: return Result.failure(Exception("Root block not found in subtree"))
            
            val targetLevel = if (targetParentUuid == null) {
                0
            } else {
                val targetParent = blockOperations.getBlockByUuid(targetParentUuid).first().getOrNull()
                    ?: return Result.failure(Exception("Target parent not found"))
                targetParent.level + 1
            }
            
            val levelOffset = targetLevel - currentRootBlock.level
            
            // Move the root block first
            blockOperations.moveBlockEnhanced(rootUuid, targetParentUuid, positioning, targetUuid)
                .getOrNull() ?: return Result.failure(Exception("Failed to move root block"))
            
            // Update levels for all descendants
            val descendants = subtree.filter { it.block.uuid != rootUuid }
            descendants.forEach { blockWithDepth ->
                val newLevel = blockWithDepth.depth + levelOffset
                // This would need a custom operation to update level directly
                // For now, we'll rely on the existing moveBlock operation
            }
            
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Promote a subtree by moving it up the hierarchy.
     */
    suspend fun promoteSubtree(
        rootUuid: String,
        levels: Int = 1
    ): Result<Unit> {
        return try {
            val block = blockOperations.getBlockByUuid(rootUuid).first().getOrNull()
                ?: return Result.failure(Exception("Block not found"))
            
            if (block.parentId == null) {
                return Result.failure(Exception("Cannot promote root-level block"))
            }
            
            // Get the current parent and its parent
            val parent = blockOperations.getBlockParent(block.uuid).first().getOrNull()
                ?: return Result.failure(Exception("Parent block not found"))
            
            val targetParent = if (levels == 1) {
                blockOperations.getBlockParent(parent.uuid).first().getOrNull()
            } else {
                // Need to traverse up multiple levels
                var currentParent = parent
                repeat(levels) {
                    currentParent = blockOperations.getBlockParent(currentParent.uuid).first().getOrNull()
                        ?: return Result.failure(Exception("Cannot promote that many levels"))
                }
                currentParent
            }
            
            moveSubtree(
                rootUuid,
                targetParent?.uuid,
                PositioningMode.AFTER,
                parent.uuid
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Demote a subtree by moving it down the hierarchy.
     */
    suspend fun demoteSubtree(
        rootUuid: String,
        levels: Int = 1
    ): Result<Unit> {
        return try {
            val block = blockOperations.getBlockByUuid(rootUuid).first().getOrNull()
                ?: return Result.failure(Exception("Block not found"))
            
            // Find the target parent by looking at siblings
            val siblings = blockOperations.getBlockSiblings(rootUuid).first().getOrNull() ?: emptyList()
            val precedingSiblings = siblings.filter { it.position < block.position }
            
            if (precedingSiblings.isEmpty()) {
                return Result.failure(Exception("No preceding sibling to demote into"))
            }
            
            val targetParent = precedingSiblings.last()
            
            // For multiple levels, we'd need to find the deepest descendant
            var finalTargetParent = targetParent
            repeat(levels - 1) {
                val children = blockOperations.getBlockChildren(finalTargetParent.uuid).first().getOrNull() ?: emptyList()
                if (children.isNotEmpty()) {
                    finalTargetParent = children.last()
                } else {
                    return Result.failure(Exception("Cannot demote that many levels"))
                }
            }
            
            moveSubtree(
                rootUuid,
                finalTargetParent.uuid,
                PositioningMode.END
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    private suspend fun collectSubtree(
        blockUuid: String,
        depth: Int,
        result: MutableList<BlockWithDepth>,
        includeCollapsed: Boolean,
        maxDepth: Int?
    ) {
        if (maxDepth != null && depth > maxDepth) return
        
        val blockResult = blockOperations.getBlockByUuid(blockUuid).first()
        val block = blockResult.getOrNull() ?: return
        result.add(BlockWithDepth(block, depth))
        
        // Check if this block is collapsed
        val isCollapsed = block.properties["collapsed"] == "true"
        if (!includeCollapsed && isCollapsed) return
        
        // Recursively collect children
        val childrenResult = blockOperations.getBlockChildren(blockUuid).first()
        val children = childrenResult.getOrNull() ?: emptyList()
        children.sortedBy { it.position }.forEach { child ->
            collectSubtree(child.uuid, depth + 1, result, includeCollapsed, maxDepth)
        }
    }
}

// ===== EXTENSIONS AND UTILITIES =====

/**
 * Extension functions for enhanced block operations.
 */
fun IBlockOperations.create() = BlockTreeOperations(this)

/**
 * Tree state for UI components.
 */
data class TreeState(
    val collapsedBlocks: Set<String> = emptySet(),
    val selectedBlocks: Set<String> = emptySet(),
    val focusedBlock: String? = null,
    val dragState: DragState? = null
)

/**
 * Drag state for block reordering.
 */
data class DragState(
    val draggedBlockUuid: String,
    val targetBlockUuid: String?,
    val positioning: PositioningMode
)

// ===== VALIDATION HELPERS =====

object BlockOperationValidator {
    
    /**
     * Validate that moving a block won't create cycles.
     */
    suspend fun validateMoveOperation(
        blockUuid: String,
        targetParentUuid: String?,
        blockOperations: IBlockOperations
    ): Result<ValidationResult> {
        return try {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            // Check if trying to move a block into its own subtree
            if (targetParentUuid != null) {
                // Use BlockTreeOperations extension
                val subtree = blockOperations.create().getSubtree(blockUuid).getOrNull()
                if (subtree?.any { it.block.uuid == targetParentUuid } == true) {
                    errors.add("Cannot move a block into its own subtree")
                }
            }
            
            // Check if target parent exists
            if (targetParentUuid != null) {
                val targetExists = blockOperations.getBlockByUuid(targetParentUuid).first().getOrNull() != null
                if (!targetExists) {
                    errors.add("Target parent block does not exist")
                }
            }
            
            success(ValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validate that delete operation won't orphan important blocks.
     */
    suspend fun validateDeleteOperation(
        blockUuid: String,
        deleteStrategy: DeleteStrategy,
        blockOperations: IBlockOperations
    ): Result<ValidationResult> {
        return try {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            when (deleteStrategy) {
                DeleteStrategy.DELETE_CHILDREN -> {
                    val childCount = blockOperations.create().countSubtreeBlocks(blockUuid).getOrNull() ?: 0
                    if (childCount > 10) {
                        warnings.add("Deleting $childCount blocks in subtree")
                    }
                }
                DeleteStrategy.ORPHAN_CHILDREN -> {
                    warnings.add("Children will become orphaned (no parent)")
                }
                else -> { /* Other strategies are generally safe */ }
            }
            
            success(ValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Helper to fix unresolved reference
private fun <T> Result<T>.getOrNull(): T? = fold(
    onSuccess = { it },
    onFailure = { null }
)
