package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.logging.Logger
import com.logseq.kmp.outliner.TreeOperations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.Result.Companion.success

/**
 * Datalog-style in-memory repository that mirrors Datascript behavior.
 * Uses Datalog query patterns similar to Logseq's Clojure implementation.
 */
class DatascriptBlockRepository : BlockRepository {
    private val logger = Logger("BlockRepo")
    private val writeMutex = Mutex()

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
            // Filter by BOTH parentId AND pageId to avoid mixing blocks from different pages
            val siblings = if (block.parentId != null) {
                map.values.filter { it.parentId == block.parentId && it.pageId == block.pageId && it.uuid != blockUuid }
            } else {
                map.values.filter { it.parentId == null && it.pageId == block.pageId && it.uuid != blockUuid }
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

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> {
        return byUuid.map { map ->
            val matching = map.values.filter { it.content.contains(query, ignoreCase = true) }
                .drop(offset)
                .take(limit)
            Result.success(matching)
        }
    }

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> {
        return writeMutex.withLock {
            try {
                val updateMap = blocks.associateBy { it.uuid }
                batchUpdateBlocks(updateMap)
                success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to save batch blocks", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        return writeMutex.withLock {
            try {
                val updateMap = mapOf(block.uuid to block)
                batchUpdateBlocks(updateMap)
                success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to save block ${block.uuid}", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        return writeMutex.withLock {
            try {
                val current = blocks.value.toMutableMap()
                if (!current.containsKey(blockUuid)) return@withLock success(Unit)

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
                    uuidsToDelete.forEach { current.remove(it) }
                } else {
                    current.remove(blockUuid)
                }
                
                blocks.value = current
                refreshIndexes(current)
                success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun moveBlock(
        blockUuid: String,
        newParentUuid: String?,
        newPosition: Int
    ): Result<Unit> {
        return writeMutex.withLock {
            try {
                val currentBlocks = blocks.value
                val block = currentBlocks[blockUuid] ?: return@withLock success(Unit)
                val newParentId = newParentUuid?.let { currentBlocks[it]?.id }

                if (block.parentId == newParentId && block.position == newPosition) {
                    return@withLock success(Unit)
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
                    val existing = updatedBlocks[it.uuid]
                    updatedBlocks[it.uuid] = existing?.copy(position = it.position, leftId = it.leftId) ?: it
                }

                batchUpdateBlocks(updatedBlocks)
                success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun indentBlock(blockUuid: String): Result<Unit> {
        return writeMutex.withLock {
            try {
                val currentBlocks = blocks.value
                val block = currentBlocks[blockUuid] ?: return@withLock success(Unit)
                // Filter by BOTH parentId AND pageId to avoid mixing blocks from different pages
                val siblings = currentBlocks.values
                    .filter { it.parentId == block.parentId && it.pageId == block.pageId }
                    .sortedBy { it.position }

                val index = siblings.indexOfFirst { it.id == block.id }
                if (index <= 0) return@withLock success(Unit)

                val newParent = siblings[index - 1]
                val newParentChildren = currentBlocks.values
                    .filter { it.parentId == newParent.id && it.pageId == block.pageId }
                    .sortedBy { it.position }

                val result = TreeOperations.indent(block, siblings, newParentChildren.lastOrNull())
                
                if (result != null) {
                    val updates = result.associateBy { it.uuid }.toMutableMap()
                    
                    val remainingSiblings = siblings.filter { it.id != block.id }.toMutableList()
                    result.forEach { updated -> 
                        val idx = remainingSiblings.indexOfFirst { it.id == updated.id }
                        if (idx != -1) remainingSiblings[idx] = updated
                    }
                    
                    val movedBlock = result.find { it.id == block.id }!!
                    val newSiblings = newParentChildren + movedBlock
                    
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
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> {
        return writeMutex.withLock {
            try {
                val currentBlocks = blocks.value
                val block = currentBlocks[blockUuid] ?: return@withLock success(Unit)
                if (block.parentId == null) return@withLock success(Unit)

                val parent = currentBlocks.values.find { it.id == block.parentId }

                // Filter by BOTH parentId AND pageId to avoid mixing blocks from different pages
                val siblings = currentBlocks.values
                    .filter { it.parentId == block.parentId && it.pageId == block.pageId }
                    .sortedBy { it.position }

                val parentSiblings = currentBlocks.values
                    .filter { it.parentId == parent?.parentId && it.pageId == block.pageId }
                    .sortedBy { it.position }

                val result = TreeOperations.outdent(block, parent, siblings, parentSiblings)
                if (result != null) {
                    val updates = result.associateBy { it.uuid }.toMutableMap()

                    // Get the outdented block from result
                    val movedBlock = result.find { it.id == block.id }!!

                    // Reorder old siblings (closing the gap where block was removed)
                    val remainingOldSiblings = siblings.filter { it.id != block.id }
                    TreeOperations.reorderSiblings(remainingOldSiblings).forEach { updates[it.uuid] = it }

                    // Reorder new siblings (parent's siblings + the moved block inserted after parent)
                    val parentIndex = parentSiblings.indexOfFirst { it.id == parent?.id }
                    val newSiblingsList = parentSiblings.toMutableList()
                    // Insert the moved block right after the parent
                    newSiblingsList.add(parentIndex + 1, movedBlock)
                    TreeOperations.reorderSiblings(newSiblingsList).forEach { updates[it.uuid] = it }

                    batchUpdateBlocks(updates)
                    success(Unit)
                } else {
                    success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> {
        return writeMutex.withLock {
            try {
                val currentBlocks = blocks.value
                val block = currentBlocks[blockUuid] ?: return@withLock success(Unit)
                val siblings = currentBlocks.values
                    .filter { it.parentId == block.parentId && it.pageId == block.pageId }
                    .sortedBy { it.position }
                    .toMutableList()

                val index = siblings.indexOfFirst { it.id == block.id }
                if (index <= 0) return@withLock success(Unit)

                // Swap in the list
                val prev = siblings[index - 1]
                siblings[index - 1] = siblings[index]
                siblings[index] = prev

                // Re-sequence everyone
                val updates = TreeOperations.reorderSiblings(siblings).associateBy { it.uuid }
                batchUpdateBlocks(updates)
                success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun moveBlockDown(blockUuid: String): Result<Unit> {
        return writeMutex.withLock {
            try {
                val currentBlocks = blocks.value
                val block = currentBlocks[blockUuid] ?: return@withLock success(Unit)
                val siblings = currentBlocks.values
                    .filter { it.parentId == block.parentId && it.pageId == block.pageId }
                    .sortedBy { it.position }
                    .toMutableList()

                val index = siblings.indexOfFirst { it.id == block.id }
                if (index < 0 || index >= siblings.size - 1) return@withLock success(Unit)

                // Swap in the list
                val next = siblings[index + 1]
                siblings[index + 1] = siblings[index]
                siblings[index] = next

                // Re-sequence everyone
                val updates = TreeOperations.reorderSiblings(siblings).associateBy { it.uuid }
                batchUpdateBlocks(updates)
                success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun mergeBlocks(blockUuid: String, nextBlockUuid: String, separator: String): Result<Unit> {
        return success(Unit)
    }

    override suspend fun splitBlock(blockUuid: String, cursorPosition: Int): Result<Block> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun deleteBlocksForPage(pageId: Long): Result<Unit> {
        return writeMutex.withLock {
            try {
                val current = blocks.value.toMutableMap()
                val toRemove = current.values.filter { it.pageId == pageId }.map { it.uuid }
                toRemove.forEach { current.remove(it) }
                blocks.value = current
                refreshIndexes(current)
                success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun clear() {
        writeMutex.withLock {
            blocks.value = emptyMap()
            byUuid.value = emptyMap()
            byPageId.value = emptyMap()
            byParentId.value = emptyMap()
        }
    }

    private fun batchUpdateBlocks(updatedBlocks: Map<String, Block>) {
        val current = blocks.value.toMutableMap()
        updatedBlocks.forEach { (uuid, block) -> current[uuid] = block }
        blocks.value = current
        refreshIndexes(current)
    }
    
    private fun refreshIndexes(currentBlocks: Map<String, Block>) {
        val allBlocks = currentBlocks.values
        byUuid.value = currentBlocks
        byPageId.value = allBlocks.groupBy { it.pageId }
        byParentId.value = allBlocks.groupBy { it.parentId }
    }
}
