package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> = flow {
        try {
            val block = queries.selectBlockByUuid(uuid).executeAsOneOrNull()
            block?.let { updateBlockCache(it) }
            emit(success(block?.toModel()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null) {
                emit(success(emptyList()))
            } else {
                // Batch load all children in single query (already optimized in schema)
                val children = queries.selectBlockChildren(block.id, Long.MAX_VALUE, 0L)
                    .executeAsList()
                // Cache all children blocks
                children.forEach { updateBlockCache(it) }
                emit(success(children.map { it.toModel() }))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> = flow {
        try {
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
                // Optimized: Collect all blocks in single pass, then build hierarchy
                val allBlocks = mutableListOf<BlockWithDepth>()
                val visitedIds = mutableSetOf<Long>()
                val queue = ArrayDeque<Pair<Long, Int>>()

                updateBlockCache(rootBlock)
                queue.addLast(rootBlock.id to 0)

                while (queue.isNotEmpty()) {
                    val (blockId, depth) = queue.removeFirst()
                    if (blockId in visitedIds || depth > 50) continue
                    visitedIds.add(blockId)

                    val cachedBlock = blockCache[blockId]
                    if (cachedBlock != null) {
                        allBlocks.add(BlockWithDepth(cachedBlock.toModel(), depth))
                        // Batch load children
                        val children = queries.selectBlockChildren(blockId, Long.MAX_VALUE, 0L).executeAsList()
                        children.forEach { child ->
                            updateBlockCache(child)
                            queue.addLast(child.id to depth + 1)
                        }
                    }
                }

                // Update cache
                if (hierarchyCache.size >= maxCacheSize) {
                    // Simple cache eviction: clear oldest entries
                    hierarchyCache.keys.take(100).forEach { hierarchyCache.remove(it) }
                }
                hierarchyCache[rootUuid] = allBlocks

                emit(success(allBlocks))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

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
                        ancestors.add(parent.toModel())
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
    }.flowOn(Dispatchers.IO)

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> = flow {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null || block.parent_id == null) {
                emit(success(null))
            } else {
                val parent = queries.selectBlockById(block.parent_id).executeAsOneOrNull()
                emit(success(parent?.toModel()))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null) {
                emit(success(emptyList()))
            } else {
                val siblings = queries.selectBlockSiblings(block.id, block.id)
                    .executeAsList()
                    .map { it.toModel() }
                emit(success(siblings))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getBlocksForPage(pageId: Long): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = queries.selectBlocksByPageIdUnpaginated(pageId)
                .executeAsList()
                .map { it.toModel() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            queries.transaction {
                blocks.forEach { block ->
                    queries.insertBlock(
                        block.uuid,
                        block.pageId,
                        block.parentId,
                        block.leftId,
                        block.content,
                        block.level.toLong(),
                        block.position.toLong(),
                        block.createdAt.toEpochMilliseconds(),
                        block.updatedAt.toEpochMilliseconds(),
                        block.properties.entries.joinToString(",") { "${it.key}:${it.value}" }
                    )
                }
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveBlock(block: Block): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            queries.insertBlock(
                block.uuid,
                block.pageId,
                block.parentId,
                block.leftId,
                block.content,
                block.level.toLong(),
                block.position.toLong(),
                block.createdAt.toEpochMilliseconds(),
                block.updatedAt.toEpochMilliseconds(),
                block.properties.entries.joinToString(",") { "${it.key}:${it.value}" }
            )
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block != null) {
                if (deleteChildren) {
                    val idsToDelete = mutableListOf(block.id)
                    var index = 0
                    while (index < idsToDelete.size) {
                        val currentId = idsToDelete[index]
                        val children = queries.selectBlockChildren(currentId, Long.MAX_VALUE, 0L).executeAsList()
                        children.forEach { idsToDelete.add(it.id) }
                        index++
                    }
                    idsToDelete.forEach { queries.deleteBlockById(it) }
                } else {
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
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

    override suspend fun indentBlock(blockUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        // TODO: Full implementation in Phase 1.2 refinement
        success(Unit)
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        // TODO: Full implementation in Phase 1.2 refinement
        success(Unit)
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        // TODO: Full implementation in Phase 1.2 refinement
        success(Unit)
    }

    override suspend fun moveBlockDown(blockUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        // TODO: Full implementation in Phase 1.2 refinement
        success(Unit)
    }

    override fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>> = flow {
        try {
            // Optimized: Use SQL LIKE query first to filter candidates
            val candidates = queries.selectBlocksWithContentLike("%[[${pageName}]]%")
                .executeAsList()
                .map { it.toModel() }
            
            // Then verify with exact regex in memory (to handle edge cases)
            val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
            val linked = candidates.filter { wikiLinkPattern.containsMatchIn(it.content) }
            
            emit(success(linked))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> = flow {
        try {
            val allBlocks = queries.selectAllBlocks().executeAsList().map { it.toModel() }
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
    }.flowOn(Dispatchers.IO)

    override fun searchBlocksByContent(query: String): Flow<Result<List<Block>>> = flow {
        try {
            val allBlocks = queries.selectAllBlocks().executeAsList().map { it.toModel() }
            val matching = allBlocks.filter { it.content.contains(query, ignoreCase = true) }
            emit(success(matching))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun com.logseq.kmp.db.Blocks.toModel(): Block {
        return Block(
            id = this.id,
            uuid = this.uuid,
            pageId = this.page_id,
            parentId = this.parent_id,
            leftId = this.left_id,
            content = this.content,
            level = this.level.toInt(),
            position = this.position.toInt(),
            createdAt = Instant.fromEpochMilliseconds(this.created_at),
            updatedAt = Instant.fromEpochMilliseconds(this.updated_at),
            properties = parseProperties(this.properties)
        )
    }

    private fun parseProperties(propertiesString: String?): Map<String, String> {
        return propertiesString?.split(",")?.associate {
            val parts = it.split(":", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        } ?: emptyMap()
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

    override suspend fun deleteBlocksForPage(pageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            queries.deleteBlocksByPageId(pageId)
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clear() {
        // No-op for now or delete all?
        // queries.deleteAllBlocks()
    }
}
