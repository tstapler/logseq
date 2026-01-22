package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.outliner.TreeOperations
import com.logseq.kmp.logging.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.Result.Companion.success

/**
 * Datalog-style in-memory repository that mirrors Datascript behavior.
 * Uses Datalog query patterns similar to Logseq's Clojure implementation.
 */
class DatascriptBlockRepository : BlockRepository {
    private val logger = Logger("BlockRepo")

    private val blocks = MutableStateFlow<Map<String, Block>>(emptyMap())

    /**
     * Datalog-style index for fast lookups
     */
    private val byUuid = MutableStateFlow<Map<String, Block>>(emptyMap())
    private val byPageId = MutableStateFlow<Map<Long, List<Block>>>(emptyMap())
    private val byParentId = MutableStateFlow<Map<Long?, List<Block>>>(emptyMap())

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> {
        return blocks.map { map ->
            success(map[uuid])
        }
    }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> {
        return byParentId.map { map ->
            val block = blocks.value[blockUuid]
            if (block == null) {
                success(emptyList())
            } else {
                success(map[block.id]?.sortedBy { it.position } ?: emptyList())
            }
        }
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> {
        return blocks.map { map ->
            val result = mutableListOf<BlockWithDepth>()
            collectHierarchy(map, rootUuid, 0, result)
            success(result)
        }
    }

    private fun collectHierarchy(
        allBlocks: Map<String, Block>,
        uuid: String,
        depth: Int,
        result: MutableList<BlockWithDepth>
    ) {
        val block = allBlocks[uuid] ?: return
        result.add(BlockWithDepth(block, depth))
        val children = byParentId.value[block.id]?.sortedBy { it.position } ?: emptyList()
        children.forEach { child ->
            collectHierarchy(allBlocks, child.uuid, depth + 1, result)
        }
    }

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val ancestors = mutableListOf<Block>()
            var currentUuid: String? = blockUuid
            while (currentUuid != null) {
                val block = map[currentUuid] ?: break
                if (block.parentId != null) {
                    val parent = map.values.find { it.id == block.parentId }
                    if (parent != null) {
                        ancestors.add(parent)
                        currentUuid = parent.uuid
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            success(ancestors.reversed())
        }
    }

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> {
        return blocks.map { map ->
            val block = map[blockUuid] ?: return@map success(null)
            val parent = if (block.parentId != null) {
                map.values.find { it.id == block.parentId }
            } else null
            success(parent)
        }
    }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val block = map[blockUuid] ?: return@map success(emptyList())
            val siblings = if (block.parentId != null) {
                map.values.filter { it.parentId == block.parentId && it.uuid != blockUuid }
            } else {
                map.values.filter { it.parentId == null && it.uuid != blockUuid }
            }
            success(siblings.sortedBy { it.position })
        }
    }

    override fun getBlocksForPage(pageId: Long): Flow<Result<List<Block>>> {
        return byPageId.map { map ->
            val blocks = map[pageId] ?: emptyList()
            success(blocks.sortedBy { it.position })
        }
    }

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> {
        return try {
            val updateMap = blocks.associateBy { it.uuid }
            batchUpdateBlocks(updateMap)
            success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save batch blocks", e)
            Result.failure(e)
        }
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        return try {
            val current = blocks.value.toMutableMap()
            current[block.uuid] = block
            blocks.value = current

            // Update indexes
            val uuidIndex = byUuid.value.toMutableMap()
            uuidIndex[block.uuid] = block
            byUuid.value = uuidIndex

            val pageIndex = byPageId.value.toMutableMap()
            val existingForPage = pageIndex[block.pageId]?.toMutableList() ?: mutableListOf()
            existingForPage.removeAll { it.uuid == block.uuid }
            existingForPage.add(block)
            pageIndex[block.pageId] = existingForPage
            byPageId.value = pageIndex

            val parentIndex = byParentId.value.toMutableMap()
            val existingForParent = parentIndex[block.parentId]?.toMutableList() ?: mutableListOf()
            existingForParent.removeAll { it.uuid == block.uuid }
            existingForParent.add(block)
            parentIndex[block.parentId] = existingForParent
            byParentId.value = parentIndex

            // logger.debug("Saved block ${block.uuid} to page ${block.pageId}")
            success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save block ${block.uuid}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        logger.info("Deleting block $blockUuid (children=$deleteChildren)")
        return try {
            val current = blocks.value.toMutableMap()
            if (!current.containsKey(blockUuid)) return success(Unit)

            if (deleteChildren) {
                val uuidsToDelete = mutableListOf(blockUuid)
                var index = 0
                while (index < uuidsToDelete.size) {
                    val currentUuid = uuidsToDelete[index]
                    val blockData = current[currentUuid] ?: continue
                    val children = current.values.filter { it.parentId == blockData.id }
                    children.forEach { child ->
                        uuidsToDelete.add(child.uuid)
                    }
                    index++
                }
                uuidsToDelete.forEach { deleteFromIndexes(it, current) }
            } else {
                deleteFromIndexes(blockUuid, current)
            }
            blocks.value = current
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deleteFromIndexes(uuid: String, blocksMap: Map<String, Block>) {
        val block = blocksMap[uuid] ?: return

        val uuidIndex = byUuid.value.toMutableMap()
        uuidIndex.remove(uuid)
        byUuid.value = uuidIndex

        val pageIndex = byPageId.value.toMutableMap()
        pageIndex[block.pageId]?.let { list ->
            pageIndex[block.pageId] = list.filter { it.uuid != uuid }
        }
        byPageId.value = pageIndex

        val parentIndex = byParentId.value.toMutableMap()
        parentIndex[block.parentId]?.let { list ->
            parentIndex[block.parentId] = list.filter { it.uuid != uuid }
        }
        byParentId.value = parentIndex
    }

    override suspend fun moveBlock(
        blockUuid: String,
        newParentUuid: String?,
        newPosition: Int
    ): Result<Unit> {
        return try {
            val currentBlocks = blocks.value
            val block = currentBlocks[blockUuid] ?: return success(Unit)
            val newParentId = newParentUuid?.let { currentBlocks[it]?.id }

            if (block.parentId == newParentId && block.position == newPosition) {
                return success(Unit)
            }

            val oldParentId = block.parentId
            val oldSiblings = currentBlocks.values
                .filter { it.parentId == oldParentId && it.uuid != blockUuid }
                .sortedBy { it.position }

            val newSiblings = if (oldParentId == newParentId) {
                oldSiblings.toMutableList().apply { add(newPosition.coerceIn(0, size), block) }
            } else {
                currentBlocks.values
                    .filter { it.parentId == newParentId }
                    .sortedBy { it.position }
                    .toMutableList().apply { add(newPosition.coerceIn(0, size), block) }
            }

            val updatedBlocks = mutableMapOf<String, Block>()

            // Update moved block and its descendants
            val newLevel = if (newParentId == null) 0 else (currentBlocks.values.find { it.id == newParentId }?.level ?: -1) + 1
            val levelOffset = newLevel - block.level
            val hierarchy = mutableListOf<BlockWithDepth>()
            collectHierarchy(currentBlocks, block.uuid, block.level, hierarchy)

            hierarchy.forEach { (b, _) ->
                updatedBlocks[b.uuid] = b.copy(
                    parentId = if (b.uuid == blockUuid) newParentId else b.parentId,
                    level = b.level + levelOffset
                )
            }

            // Update siblings in old parent
            if (oldParentId != newParentId) {
                TreeOperations.reorderSiblings(oldSiblings).forEach { updatedBlocks[it.uuid] = it }
            }

            // Update siblings in new parent
            TreeOperations.reorderSiblings(newSiblings).forEach {
                // Sibling reordering might include the moved block, so we merge carefully
                val existing = updatedBlocks[it.uuid]
                updatedBlocks[it.uuid] = existing?.copy(position = it.position, leftId = it.leftId) ?: it
            }

            batchUpdateBlocks(updatedBlocks)
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun indentBlock(blockUuid: String): Result<Unit> {
        return try {
            val currentBlocks = blocks.value
            val block = currentBlocks[blockUuid] ?: return success(Unit)
            val siblings = currentBlocks.values
                .filter { it.parentId == block.parentId }
                .sortedBy { it.position }

            val index = siblings.indexOfFirst { it.id == block.id }
            if (index <= 0) return success(Unit)
            
            val newParent = siblings[index - 1]
            val newParentChildren = currentBlocks.values
                .filter { it.parentId == newParent.id }
                .sortedBy { it.position }

            val result = TreeOperations.indent(block, siblings, newParentChildren.lastOrNull())
            
            if (result != null) {
                val updates = result.associateBy { it.uuid }.toMutableMap()
                
                // We need to fix positions (integer indexes) for two groups:
                // 1. The remaining siblings in the old parent
                val remainingSiblings = siblings.filter { it.id != block.id }.toMutableList()
                // Update the neighbor if it was changed in 'result'
                result.forEach { updated -> 
                    val idx = remainingSiblings.indexOfFirst { it.id == updated.id }
                    if (idx != -1) remainingSiblings[idx] = updated
                }
                
                // 2. The new siblings in the new parent (block + existing children)
                // The moved block is now the last child
                val movedBlock = result.find { it.id == block.id }!!
                val newSiblings = newParentChildren + movedBlock
                
                // Reorder and collect updates
                TreeOperations.reorderSiblings(remainingSiblings).forEach { updates[it.uuid] = it }
                TreeOperations.reorderSiblings(newSiblings).forEach { updates[it.uuid] = it }

                batchUpdateBlocks(updates)
                success(Unit)
            } else {
                success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> {
        return try {
            val currentBlocks = blocks.value
            val block = currentBlocks[blockUuid] ?: return success(Unit)
            if (block.parentId == null) return success(Unit)

            val parent = currentBlocks.values.find { it.id == block.parentId }
            
            // Siblings of the block (children of the parent)
            val siblings = currentBlocks.values
                .filter { it.parentId == block.parentId }
                .sortedBy { it.position }

            // Siblings of the parent (where the block will move to)
            val parentSiblings = currentBlocks.values
                .filter { it.parentId == parent?.parentId }
                .sortedBy { it.position }

            val result = TreeOperations.outdent(block, parent, siblings, parentSiblings)
            if (result != null) {
                // In this simplified repository, we just save the blocks.
                // Since this is an in-memory mock, we batch update.
                // Note: The logic for calculating the new 'position' is slightly off in the moveBlock call below
                // because TreeOperations now returns a list of blocks with UPDATED relations (leftId/parentId).
                // It does NOT update 'position' (an integer index).
                // Ideally, we should just save the updated blocks directly.
                
                val updateMap = result.associateBy { it.uuid }
                batchUpdateBlocks(updateMap)
                success(Unit)
            } else {
                success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> {
        return try {
            val currentBlocks = blocks.value
            val block = currentBlocks[blockUuid] ?: return success(Unit)
            val siblings = currentBlocks.values
                .filter { it.parentId == block.parentId }
                .sortedBy { it.position }

            val result = TreeOperations.moveUp(block, siblings)
            if (result != null) {
                val updates = result.associateBy { it.uuid }.toMutableMap()
                
                // Apply the swap to our local list of siblings to reorder positions
                val updatedSiblings = siblings.map { existing ->
                    updates[existing.uuid] ?: existing
                }.sortedBy { it.position }.toMutableList()
                
                // Find indexes
                val idx1 = updatedSiblings.indexOfFirst { it.id == result[0].id }
                val idx2 = updatedSiblings.indexOfFirst { it.id == result[1].id }
                
                // Swap in list
                if (idx1 != -1 && idx2 != -1) {
                     val tmp = updatedSiblings[idx1]
                     updatedSiblings[idx1] = updatedSiblings[idx2]
                     updatedSiblings[idx2] = tmp
                }
                
                // Re-assign positions
                TreeOperations.reorderSiblings(updatedSiblings).forEach { updates[it.uuid] = it }
                
                batchUpdateBlocks(updates)
                success(Unit)
            } else {
                success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveBlockDown(blockUuid: String): Result<Unit> {
        return try {
            val currentBlocks = blocks.value
            val block = currentBlocks[blockUuid] ?: return success(Unit)
            val siblings = currentBlocks.values
                .filter { it.parentId == block.parentId }
                .sortedBy { it.position }

            val result = TreeOperations.moveDown(block, siblings)
            if (result != null) {
                val updates = result.associateBy { it.uuid }.toMutableMap()
                
                val updatedSiblings = siblings.map { existing ->
                    updates[existing.uuid] ?: existing
                }.toMutableList()

                // Swap in list (naive approach: find by ID and swap)
                val b1 = result[0]
                val b2 = result[1]
                
                val idx1 = updatedSiblings.indexOfFirst { it.id == b1.id }
                val idx2 = updatedSiblings.indexOfFirst { it.id == b2.id }
                
                 if (idx1 != -1 && idx2 != -1) {
                     // Swap
                     val temp = updatedSiblings[idx1]
                     updatedSiblings[idx1] = updatedSiblings[idx2]
                     updatedSiblings[idx2] = temp
                }

                TreeOperations.reorderSiblings(updatedSiblings).forEach { updates[it.uuid] = it }
                
                batchUpdateBlocks(updates)
                success(Unit)
            } else {
                success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun batchUpdateBlocks(updatedBlocks: Map<String, Block>) {
        val current = blocks.value.toMutableMap()
        updatedBlocks.forEach { (uuid, block) -> current[uuid] = block }
        blocks.value = current

        // Refresh all indexes
        val allBlocks = current.values
        byUuid.value = current
        byPageId.value = allBlocks.groupBy { it.pageId }
        byParentId.value = allBlocks.groupBy { it.parentId }
    }

    override fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
        return blocks.map { map ->
            val linkedBlocks = map.values.filter { block ->
                wikiLinkPattern.containsMatchIn(block.content)
            }
            success(linkedBlocks.sortedBy { it.pageId })
        }
    }

    override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
        val plainTextPattern = "\\b${Regex.escape(pageName)}\\b".toRegex(RegexOption.IGNORE_CASE)
        return blocks.map { map ->
            val unlinkedBlocks = map.values.filter { block ->
                // Contains the page name as plain text but NOT as a wiki link
                plainTextPattern.containsMatchIn(block.content) &&
                    !wikiLinkPattern.containsMatchIn(block.content)
            }
            success(unlinkedBlocks.sortedBy { it.pageId })
        }
    }

    override fun searchBlocksByContent(query: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val matchingBlocks = map.values.filter { block ->
                block.content.contains(query, ignoreCase = true)
            }
            success(matchingBlocks.sortedBy { it.pageId })
        }
    }
}
