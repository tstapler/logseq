package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import com.logseq.kmp.coroutines.PlatformDispatcher
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.Result.Companion.success
import kotlin.collections.mutableMapOf

/**
 * SQLDelight implementation of BlockRepository.
 * Uses the generated LogseqDatabaseQueries for all operations.
 * Optimized with local caching for hierarchical queries to avoid N+1 problem.
 */
class SqlDelightBlockRepository(
    private val database: LogseqDatabase
) : BlockRepository {

    private val queries = database.logseqDatabaseQueries

    // Local cache for hierarchical queries to avoid N+1 problem
    private val blockCache = mutableMapOf<Long, com.logseq.kmp.db.Blocks>()
    private val hierarchyCache = mutableMapOf<String, List<BlockWithDepth>>()
    private val ancestorsCache = mutableMapOf<String, List<Block>>()

    // Cache configuration
    private val maxCacheSize = 1000
    private val hierarchyTtlMs = 120_000L // 2 minutes

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> = 
        queries.selectBlockByUuid(uuid)
            .asFlow()
            .mapToOneOrNull(PlatformDispatcher.IO)
            .map { block ->
                block?.let { updateBlockCache(it) }
                success(block?.toBlockModel())
            }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> = flow {
        // We still need a manual flow here because we need to resolve UUID to ID first,
        // unless we join in the SQL. For now, let's keep it simple but reactive to the children table.
        val rootBlock = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
        if (rootBlock == null) {
            emit(success(emptyList()))
        } else {
            queries.selectBlockChildren(rootBlock.id, Long.MAX_VALUE, 0L)
                .asFlow()
                .mapToList(PlatformDispatcher.IO)
                .map { list -> 
                    list.forEach { updateBlockCache(it) }
                    success(list.map { it.toBlockModel() }) 
                }
                .collect { emit(it) }
        }
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> = flow {
        // Hierarchy is complex to make fully reactive with SQLDelight's current tools 
        // without a custom observer. For now, we'll keep it as a snapshot flow but 
        // we should trigger it manually when needed.
        try {
            // ... same logic as before ...
            // Check cache first
            val cached = hierarchyCache[rootUuid]
            if (cached != null && !isHierarchyCacheExpired(rootUuid)) {
                emit(success(cached))
                return@flow
            }

            val rootBlock = queries.selectBlockByUuid(rootUuid).executeAsOneOrNull()
            if (rootBlock == null) {
                emit(success(emptyList()))
            } else {
                // Optimized: BFS with batch child loading
                val allBlocks = mutableListOf<BlockWithDepth>()
                val visitedIds = mutableSetOf<Long>()
                val resultList = mutableListOf<BlockWithDepth>()
                
                var currentLevelIds = listOf(rootBlock.id)
                var currentDepth = 0
                
                // Track which blocks belong to which depth
                val blocksByDepth = mutableMapOf<Int, List<com.logseq.kmp.db.Blocks>>()
                blocksByDepth[0] = listOf(rootBlock)

                while (currentLevelIds.isNotEmpty()) {
                    val currentBlocks = blocksByDepth[currentDepth] ?: emptyList()
                    currentBlocks.forEach { block ->
                        if (block.id !in visitedIds) {
                            visitedIds.add(block.id)
                            updateBlockCache(block)
                            resultList.add(BlockWithDepth(block.toBlockModel(), currentDepth))
                        }
                    }

                    // Batch load children for all blocks in the current level
                    val children = queries.selectBlocksByParentIds(currentLevelIds).executeAsList()
                    if (children.isEmpty()) break
                    
                    currentDepth++
                    currentLevelIds = children.map { it.id }
                    blocksByDepth[currentDepth] = children
                    
                    if (currentDepth > 100) break // Prevent infinite loops
                }

                // Update cache
                if (hierarchyCache.size >= maxCacheSize) {
                    hierarchyCache.keys.take(100).forEach { hierarchyCache.remove(it) }
                }
                hierarchyCache[rootUuid] = resultList

                emit(success(resultList))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            // Check cache first
            val cached = ancestorsCache[blockUuid]
            if (cached != null) {
                emit(success(cached))
                return@flow
            }

            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null) {
                emit(success(emptyList()))
            } else {
                val ancestors = mutableListOf<Block>()
                var currentParentId: Long? = block.parent_id

                // Optimized: Walk up the tree using cached data when possible
                while (currentParentId != null) {
                    val cachedParent = blockCache[currentParentId]
                    val parent = cachedParent ?: queries.selectBlockById(currentParentId).executeAsOneOrNull()
                    if (parent != null) {
                        updateBlockCache(parent)
                        ancestors.add(parent.toBlockModel())
                        currentParentId = parent.parent_id
                    } else {
                        break
                    }
                }

                // Cache the result
                if (ancestorsCache.size >= maxCacheSize) {
                    ancestorsCache.keys.take(100).forEach { ancestorsCache.remove(it) }
                }
                ancestorsCache[blockUuid] = ancestors.reversed()

                emit(success(ancestors.reversed()))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> = flow {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null || block.parent_id == null) {
                emit(success(null))
            } else {
                val parent = queries.selectBlockById(block.parent_id).executeAsOneOrNull()
                emit(success(parent?.toBlockModel()))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null) {
                emit(success(emptyList()))
            } else {
                val siblings = queries.selectBlockSiblings(block.id, block.id)
                    .executeAsList()
                    .map { it.toBlockModel() }
                emit(success(siblings))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun getBlocksForPage(pageUuid: String): Flow<Result<List<Block>>> = 
        queries.selectBlocksByPageUuidUnpaginated(pageUuid)
            .asFlow()
            .mapToList(PlatformDispatcher.IO)
            .map { list -> success(list.map { it.toBlockModel() }) }

    private fun resolveUuidToId(uuid: String?): Long? {
        if (uuid == null) return null
        return queries.selectBlockIdByUuid(uuid).executeAsOneOrNull()?.id
    }

    private fun resolvePageUuidToId(uuid: String): Long {
        return queries.selectPageIdByUuid(uuid).executeAsOneOrNull()?.id ?: -1L
    }

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            queries.transaction {
                blocks.forEach { block ->
                    saveBlockInternal(block)
                }
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveBlock(block: Block): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            saveBlockInternal(block)
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveBlockInternal(block: Block) {
        queries.insertBlock(
            block.uuid,
            resolvePageUuidToId(block.pageUuid),
            resolveUuidToId(block.parentUuid),
            resolveUuidToId(block.leftUuid),
            block.content,
            block.level.toLong(),
            block.position.toLong(),
            block.createdAt.toEpochMilliseconds(),
            block.updatedAt.toEpochMilliseconds(),
            block.properties.entries.joinToString(",") { "${it.key}:${it.value}" },
            block.version
        )
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block != null) {
                if (deleteChildren) {
                    val idsToDelete = mutableListOf<Long>(block.id)
                    var index = 0
                    while (index < idsToDelete.size) {
                        val currentId = idsToDelete[index]
                        val children = queries.selectBlockChildren(currentId, Long.MAX_VALUE, 0L).executeAsList()
                        children.forEach { idsToDelete.add(it.id) }
                        index++
                    }

                    // Chain repair before deletion
                    val nextSibling = queries.selectBlockByLeftId(block.id).executeAsOneOrNull()
                    if (nextSibling != null) {
                        queries.updateBlockLeftId(block.left_id, nextSibling.id)
                    }

                    idsToDelete.forEach { queries.deleteBlockById(it) }
                } else {
                    // Chain repair before deletion
                    val nextSibling = queries.selectBlockByLeftId(block.id).executeAsOneOrNull()
                    if (nextSibling != null) {
                        queries.updateBlockLeftId(block.left_id, nextSibling.id)
                    }
                    queries.deleteBlockById(block.id)
                }

            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveBlock(
        blockUuid: String,
        newParentUuid: String?,
        newPosition: Int
    ): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block != null) {
                val newParentId = newParentUuid?.let {
                    queries.selectBlockByUuid(it).executeAsOneOrNull()?.id
                }
                queries.updateBlockParent(newParentId, block.id)
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun indentBlock(blockUuid: String): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            queries.transaction {
                val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
                    ?: return@transaction
                
                // 1. New parent is the previous sibling.
                val prevSibling = block.left_id?.let { queries.selectBlockById(it).executeAsOneOrNull() }
                if (prevSibling == null || prevSibling.parent_id != block.parent_id) {
                    return@transaction // No previous sibling at the same level, cannot indent
                }
                
                // 3. Chain Repair: The block that was to the right of the moved block 
                // must have its leftId updated to the moved block's old leftId.
                val nextSibling = queries.selectBlockByLeftId(block.id).executeAsOneOrNull()
                if (nextSibling != null) {
                    queries.updateBlockLeftId(block.left_id, nextSibling.id)
                    // No need to shift positions here as the block is being moved to a different parent
                }
                
                // 3. New hierarchy calculation
                // New parent is prevSibling.
                val lastChildOfNewParent = queries.selectLastChild(prevSibling.id).executeAsOneOrNull()
                val newLeftId = lastChildOfNewParent?.id ?: prevSibling.id
                val newPosition = (lastChildOfNewParent?.position ?: -1L) + 1L
                val newLevel = block.level + 1L
                
                // Update current block hierarchy in one shot
                queries.updateBlockHierarchy(prevSibling.id, newLeftId, newPosition, newLevel, block.id)
            }

            hierarchyCache.clear()
            ancestorsCache.clear()
            blockCache.clear()
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            queries.transaction {
                val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
                    ?: return@transaction
                
                val currentParentId = block.parent_id ?: return@transaction // Already at root
                val currentParent = queries.selectBlockById(currentParentId).executeAsOneOrNull()
                    ?: return@transaction
                
                // 1. New parent is the grandparent.
                val grandparentId = currentParent.parent_id
                
                // 3. Chain Repair: The block that was to the right of the moved block 
                // must have its leftId updated to the moved block's old leftId.
                val nextSibling = queries.selectBlockByLeftId(block.id).executeAsOneOrNull()
                if (nextSibling != null) {
                    queries.updateBlockLeftId(block.left_id, nextSibling.id)
                }
                
                // 3. New hierarchy calculation: New leftId is the old parent's ID.
                val newLeftId = currentParent.id
                val newPosition = currentParent.position + 1L
                val newLevel = block.level - 1L
                
                // Shift positions of siblings that come after the new position to make room
                val siblingsToShift = if (grandparentId == null) {
                    queries.selectRootBlocksByPageIdOrdered(block.page_id).executeAsList()
                } else {
                    queries.selectBlocksByParentIdOrdered(grandparentId).executeAsList()
                }
                siblingsToShift.forEach { sibling ->
                    if (sibling.position >= newPosition) {
                        queries.updateBlockPositionOnly(sibling.position + 1L, sibling.id)
                    }
                }

                // Repair new sibling chain: Any block that followed currentParent at the grandparent level 
                // now must follow the moved block.
                val blockFollowingOldParent = queries.selectBlockByLeftId(currentParent.id).executeAsOneOrNull()
                if (blockFollowingOldParent != null) {
                    queries.updateBlockLeftId(block.id, blockFollowingOldParent.id)
                }
                
                // Update current block hierarchy in one shot
                queries.updateBlockHierarchy(grandparentId, newLeftId, newPosition, newLevel, block.id)
            }

            hierarchyCache.clear()
            ancestorsCache.clear()
            blockCache.clear()
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
                ?: return@withContext success(Unit)

            val siblings = if (block.parent_id == null) {
                queries.selectRootBlocksByPageIdOrdered(block.page_id).executeAsList()
            } else {
                queries.selectBlocksByParentIdOrdered(block.parent_id).executeAsList()
            }

            val blockIndex = siblings.indexOfFirst { it.id == block.id }
            if (blockIndex <= 0) return@withContext success(Unit) // Already first

            val prevSibling = siblings[blockIndex - 1]
            val nextSibling = siblings.getOrNull(blockIndex + 1)

            queries.transaction {
                // Swap positions and leftIds
                // Current block (B) takes previous sibling's (A) leftId and position
                queries.updateBlockHierarchy(block.parent_id, prevSibling.left_id, prevSibling.position, block.level.toLong(), block.id)
                
                // Previous sibling (A) now follows current block (B)
                queries.updateBlockHierarchy(prevSibling.parent_id, block.id, block.position, prevSibling.level.toLong(), prevSibling.id)
                
                // If there was a next sibling (C) following B, it now follows A
                if (nextSibling != null) {
                    queries.updateBlockLeftId(prevSibling.id, nextSibling.id)
                }
            }

            blockCache.remove(block.id)
            blockCache.remove(prevSibling.id)
            nextSibling?.let { blockCache.remove(it.id) }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveBlockDown(blockUuid: String): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
                ?: return@withContext success(Unit)

            val siblings = if (block.parent_id == null) {
                queries.selectRootBlocksByPageIdOrdered(block.page_id).executeAsList()
            } else {
                queries.selectBlocksByParentIdOrdered(block.parent_id).executeAsList()
            }

            val blockIndex = siblings.indexOfFirst { it.id == block.id }
            if (blockIndex >= siblings.size - 1) return@withContext success(Unit) // Already last

            val nextSibling = siblings[blockIndex + 1]
            val afterNextSibling = siblings.getOrNull(blockIndex + 2)

            queries.transaction {
                // Swap positions and leftIds
                // Next sibling (B) takes current block's (A) leftId and position
                queries.updateBlockHierarchy(nextSibling.parent_id, block.left_id, block.position, nextSibling.level.toLong(), nextSibling.id)

                // Current block (A) now follows next sibling (B)
                queries.updateBlockHierarchy(block.parent_id, nextSibling.id, nextSibling.position, block.level.toLong(), block.id)
                
                // If there was a block (C) following B, it now follows A
                if (afterNextSibling != null) {
                    queries.updateBlockLeftId(block.id, afterNextSibling.id)
                }
            }

            blockCache.remove(block.id)
            blockCache.remove(nextSibling.id)
            afterNextSibling?.let { blockCache.remove(it.id) }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mergeBlocks(
        blockUuid: String,
        nextBlockUuid: String,
        separator: String
    ): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            queries.transaction {
                val blockA = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
                    ?: return@transaction
                val blockB = queries.selectBlockByUuid(nextBlockUuid).executeAsOneOrNull()
                    ?: return@transaction
                
                // 1. Update content of block A
                val mergedContent = blockA.content + separator + blockB.content
                queries.updateBlockContent(
                    mergedContent, 
                    System.currentTimeMillis(), 
                    blockA.id
                )
                
                // 2. Reparent all children of block B to block A
                val childrenOfB = queries.selectBlocksByParentIdOrdered(blockB.id).executeAsList()
                childrenOfB.forEach { child ->
                    // For each child, we need to update parent_id AND potentially recalculate position
                    // To keep it simple for now, we just append them to A's children
                    val lastChildOfA = queries.selectLastChild(blockA.id).executeAsOneOrNull()
                    val newPosition = (lastChildOfA?.position ?: -1L) + 1L
                    val newLeftId = lastChildOfA?.id ?: blockA.id
                    
                    queries.updateBlockHierarchy(blockA.id, newLeftId, newPosition, (blockA.level + 1L), child.id)
                    // If the child had descendants, their levels need to be shifted too
                    // but since they are now children of A (same level as before relative to B), 
                    // and A/B were siblings (same level), their absolute level stays same.
                }
                
                // 3. Chain repair for block B (B is being deleted)
                val blockAfterB = queries.selectBlockByLeftId(blockB.id).executeAsOneOrNull()
                if (blockAfterB != null) {
                    queries.updateBlockLeftId(blockB.left_id, blockAfterB.id)
                }
                
                // 4. Delete block B
                queries.deleteBlockById(blockB.id)
            }
            
            blockCache.remove(blockCache.filter { it.value.uuid == nextBlockUuid }.keys.firstOrNull() ?: -1L)
            hierarchyCache.clear()
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun splitBlock(
        blockUuid: String, 
        cursorPosition: Int
    ): Result<Block> = withContext(PlatformDispatcher.IO) {
        try {
            var newBlock: Block? = null
            queries.transaction {
                val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
                    ?: return@transaction
                
                val content = block.content
                val firstPart = content.substring(0, cursorPosition).trim()
                val secondPart = content.substring(cursorPosition).trim()
                
                // 1. Update original block
                queries.updateBlockContent(firstPart, System.currentTimeMillis(), block.id)
                
                // 2. Create new block
                val newUuid = java.util.UUID.randomUUID().toString()
                val newPosition = block.position + 1L
                
                // Shift siblings' positions
                val siblings = if (block.parent_id == null) {
                    queries.selectRootBlocksByPageIdOrdered(block.page_id).executeAsList()
                } else {
                    queries.selectBlocksByParentIdOrdered(block.parent_id).executeAsList()
                }
                
                siblings.forEach { sibling ->
                    if (sibling.position >= newPosition) {
                        queries.updateBlockPositionOnly(sibling.position + 1L, sibling.id)
                    }
                }
                
                // Repair chain: block that followed 'block' now follows 'newBlock'
                val nextSibling = queries.selectBlockByLeftId(block.id).executeAsOneOrNull()
                
                queries.insertBlock(
                    uuid = newUuid,
                    page_id = block.page_id,
                    parent_id = block.parent_id,
                    left_id = block.id,
                    content = secondPart,
                    level = block.level,
                    position = newPosition,
                    created_at = System.currentTimeMillis(),
                    updated_at = System.currentTimeMillis(),
                    properties = null,
                    version = 0L
                )
                
                val insertedBlock = queries.selectBlockByUuid(newUuid).executeAsOne()
                if (nextSibling != null) {
                    queries.updateBlockLeftId(insertedBlock.id, nextSibling.id)
                }
                
                newBlock = insertedBlock.toBlockModel()
            }
            
            hierarchyCache.clear()
            Result.success(newBlock ?: throw IllegalStateException("Failed to create new block during split"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shiftDescendantsLevel(rootId: Long, delta: Long) {
        val children = queries.selectBlocksByParentIdOrdered(rootId).executeAsList()
        children.forEach { child ->
            queries.updateBlockLevelOnly(child.level + delta, child.id)
            shiftDescendantsLevel(child.id, delta)
        }
    }

    override fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>> = flow {
        try {
            // Optimized: Use SQL LIKE query first to filter candidates
            val candidates = queries.selectBlocksWithContentLike("%[[${pageName}]]%")
                .executeAsList()
                .map { it.toBlockModel() }
            
            // Then verify with exact regex in memory (to handle edge cases)
            val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
            val linked = candidates.filter { wikiLinkPattern.containsMatchIn(it.content) }
            
            emit(success(linked))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> = flow {
        try {
            val allBlocks = queries.selectAllBlocks().executeAsList().map { it.toBlockModel() }
            val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
            val plainTextPattern = "\\b${Regex.escape(pageName)}\\b".toRegex(RegexOption.IGNORE_CASE)

            val unlinked = allBlocks.filter { block ->
                plainTextPattern.containsMatchIn(block.content) &&
                        !wikiLinkPattern.containsMatchIn(block.content)
            }
            emit(success(unlinked))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> = 
        queries.selectBlocksWithContentLike("%$query%")
            .asFlow()
            .mapToList(PlatformDispatcher.IO)
            .map { list -> 
                success(list.drop(offset).take(limit).map { it.toBlockModel() })
            }

    private fun resolveIdToUuid(id: Long?): String? {
        if (id == null) return null
        return queries.selectBlockByInternalId(id).executeAsOneOrNull()?.uuid
    }

    private fun resolvePageIdToUuid(id: Long): String {
        return queries.selectPageByInternalId(id).executeAsOneOrNull()?.uuid ?: ""
    }

    private fun com.logseq.kmp.db.Blocks.toBlockModel(): Block {
        return Block(
            uuid = this.uuid,
            pageUuid = resolvePageIdToUuid(this.page_id),
            parentUuid = resolveIdToUuid(this.parent_id),
            leftUuid = resolveIdToUuid(this.left_id),
            content = this.content,
            level = this.level.toInt(),
            position = this.position.toInt(),
            createdAt = Instant.fromEpochMilliseconds(this.created_at),
            updatedAt = Instant.fromEpochMilliseconds(this.updated_at),
            version = this.version,
            properties = parseProperties(this.properties)
        )
    }

    private fun parseProperties(propertiesString: String?): Map<String, String> {
        return propertiesString?.split(",")?.filter { it.isNotBlank() }?.associate {
            val parts = it.split(":", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }?.filter { it.key.isNotBlank() } ?: emptyMap()
    }

    private fun updateBlockCache(block: com.logseq.kmp.db.Blocks) {
        if (blockCache.size >= maxCacheSize) {
            // Evict oldest entries (simple strategy)
            blockCache.keys.take(100).forEach { blockCache.remove(it) }
        }
        blockCache[block.id] = block
    }

    private val hierarchyCacheTimestamps = mutableMapOf<String, Long>()

    private fun isHierarchyCacheExpired(rootUuid: String): Boolean {
        val timestamp = hierarchyCacheTimestamps[rootUuid] ?: return true
        return System.currentTimeMillis() - timestamp > hierarchyTtlMs
    }

    override suspend fun deleteBlocksForPage(pageUuid: String): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            queries.deleteBlocksByPageUuid(pageUuid)
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clear() = withContext(PlatformDispatcher.IO) {
        queries.deleteAllBlocks()
        blockCache.clear()
        hierarchyCache.clear()
        ancestorsCache.clear()
    }
}
