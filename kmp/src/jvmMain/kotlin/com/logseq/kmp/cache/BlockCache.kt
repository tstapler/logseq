package com.logseq.kmp.cache

import com.logseq.kmp.model.Block
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.BlockWithDepth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.Result.Companion.success
import kotlin.random.Random

/**
 * Block cache with LRU, TTL, and prefetch capabilities.
 * Wraps a BlockRepository to provide progressive loading.
 */
class BlockCache(
    private val config: CacheConfig,
    private val delegate: BlockRepository
) {
    private val blockCache = LRUCache<BlockCache.Key, CachedBlock>(config, "blocks")
    private val childrenIndex = ConcurrentHashMap<Long, MutableList<Long>>()
    private val hierarchyCache = LRUCache<String, CachedHierarchy>(config, "hierarchies")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val metrics = MutableStateFlow(CacheMetrics())

    sealed class Key {
        data class ByUuid(val uuid: String) : Key()
        data class ById(val id: Long) : Key()
    }

    fun start() {
        blockCache.start()
        hierarchyCache.start()
    }

    fun stop() {
        blockCache.stop()
        hierarchyCache.stop()
        scope.cancel()
    }

    /**
     * Get block by UUID with cache.
     */
    fun getBlockByUuid(uuid: String): Flow<Result<Block?>> = flow {
        try {
            val cached = blockCache.get(Key.ByUuid(uuid))
            if (cached != null) {
                metrics.value = metrics.value.withBlockHit()
                emit(success(cached.block))
            } else {
                metrics.value = metrics.value.withBlockMiss()
                delegate.getBlockByUuid(uuid).collect { result ->
                    result.getOrNull()?.let { block ->
                        val cachedBlock = CachedBlock(
                            block = block,
                            childrenIds = emptyList()
                        )
                        blockCache.put(Key.ByUuid(uuid), cachedBlock)
                        block.id.let { id ->
                            blockCache.put(Key.ById(id), cachedBlock)
                        }
                        if (config.enablePrefetch && config.prefetchDepth > 0) {
                            prefetchBlockChildren(block.id, 1)
                        }
                    }
                    emit(result)
                }
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get block children with cache and prefetch.
     */
    fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            var parentBlock: Block? = null
            var parentId: Long? = null

            blockCache.get(Key.ByUuid(blockUuid))?.let { cached ->
                parentBlock = cached.block
                parentId = cached.block.id
            }

            if (parentBlock == null) {
                parentBlock = delegate.getBlockByUuid(blockUuid).first().getOrNull()
                parentId = parentBlock?.id
            }

            if (parentBlock == null || parentId == null) {
                emit(success(emptyList()))
                return@flow
            }

            val currentParentId = parentId!!

            // Check children index cache
            val cachedChildrenIds = childrenIndex[currentParentId]
            if (cachedChildrenIds != null) {
                val cachedChildren = cachedChildrenIds.mapNotNull { id ->
                    blockCache.get(Key.ById(id))?.block
                }
                if (cachedChildren.size == cachedChildrenIds.size) {
                    metrics.value = metrics.value.withBlockHit()
                    emit(success(cachedChildren))
                    return@flow
                }
            }

            // Cache miss - load from delegate
            metrics.value = metrics.value.withBlockMiss()
            delegate.getBlockChildren(blockUuid).collect { result ->
                result.getOrNull()?.let { children ->
                    val childrenIds = mutableListOf<Long>()
                    val cachedBlocks = children.map { child ->
                        childrenIds.add(child.id)
                        val cached = CachedBlock(
                            block = child,
                            childrenIds = emptyList(),
                            parentId = currentParentId
                        )
                        blockCache.put(Key.ByUuid(child.uuid), cached)
                        blockCache.put(Key.ById(child.id), cached)
                        child
                    }

                    // Update children index
                    childrenIndex[currentParentId] = childrenIds.toMutableList()

                    // Prefetch grandchildren
                    if (config.enablePrefetch && config.prefetchDepth > 1) {
                        children.forEach { child ->
                            scope.launch {
                                prefetchBlockChildren(child.id, 2)
                            }
                        }
                    }

                    emit(success(cachedBlocks))
                } ?: emit(result)
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get block hierarchy with cache.
     */
    fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> = flow {
        try {
            val cached = hierarchyCache.get(rootUuid)
            if (cached != null && !isHierarchyExpired(cached.timestamp)) {
                metrics.value = metrics.value.withHierarchyHit()
                emit(success(cached.blocks))
            } else {
                metrics.value = metrics.value.withHierarchyMiss()
                delegate.getBlockHierarchy(rootUuid).collect { result ->
                    result.getOrNull()?.let { hierarchy ->
                        val cachedHierarchy = CachedHierarchy(
                            rootUuid = rootUuid,
                            blocks = hierarchy
                        )
                        hierarchyCache.put(rootUuid, cachedHierarchy)

                        // Cache individual blocks
                        hierarchy.forEach { (block, _) ->
                            blockCache.put(Key.ByUuid(block.uuid), CachedBlock(block))
                            block.id.let { id -> blockCache.put(Key.ById(id), CachedBlock(block)) }
                        }

                        emit(success(hierarchy))
                    } ?: emit(result)
                }
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get block parent with cache.
     */
    fun getBlockParent(blockUuid: String): Flow<Result<Block?>> = flow {
        try {
            val cached = blockCache.get(Key.ByUuid(blockUuid))
            if (cached != null && cached.parentId != null) {
                val parent = blockCache.get(Key.ById(cached.parentId))
                if (parent != null) {
                    metrics.value = metrics.value.withBlockHit()
                    emit(success(parent.block))
                    return@flow
                }
            }

            metrics.value = metrics.value.withBlockMiss()
            delegate.getBlockParent(blockUuid).collect { emit(it) }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get block ancestors with cache.
     */
    fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            // Try to build from cache
            val cached = blockCache.get(Key.ByUuid(blockUuid))
            if (cached != null) {
                val ancestors = mutableListOf<Block>()
                var currentParentId: Long? = cached.parentId
                while (currentParentId != null) {
                    val parent = blockCache.get(Key.ById(currentParentId))
                    if (parent != null) {
                        ancestors.add(parent.block)
                        currentParentId = parent.parentId
                    } else {
                        break
                    }
                }
                if (ancestors.isNotEmpty()) {
                    metrics.value = metrics.value.withBlockHit()
                    emit(success(ancestors.reversed()))
                    return@flow
                }
            }

            metrics.value = metrics.value.withBlockMiss()
            delegate.getBlockAncestors(blockUuid).collect { emit(it) }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Save block - invalidates cache.
     */
    suspend fun saveBlock(block: Block): Result<Unit> {
        invalidateBlock(block.uuid)
        invalidateBlockHierarchy(block.uuid)
        return delegate.saveBlock(block)
    }

    /**
     * Delete block - invalidates cache.
     */
    suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        invalidateBlockHierarchy(blockUuid)
        childrenIndex.remove(blockUuid.hashCode().toLong())
        return delegate.deleteBlock(blockUuid, deleteChildren)
    }

    /**
     * Move block - invalidates affected hierarchies.
     */
    suspend fun moveBlock(
        blockUuid: String,
        newParentUuid: String?,
        newPosition: Int
    ): Result<Unit> {
        invalidateBlockHierarchy(blockUuid)
        newParentUuid?.let { invalidateBlockHierarchy(it) }
        return delegate.moveBlock(blockUuid, newParentUuid, newPosition)
    }

    /**
     * Invalidate a specific block from cache.
     */
    fun invalidateBlock(uuid: String) {
        val cached = blockCache.get(Key.ByUuid(uuid))
        cached?.let {
            blockCache.remove(Key.ByUuid(uuid))
            blockCache.remove(Key.ById(it.block.id))
            it.block.parentId?.let { pid ->
                childrenIndex[pid]?.remove(it.block.id)
            }
        }
    }

    /**
     * Invalidate siblings (children of the same parent).
     * Useful when reordering blocks.
     */
    fun invalidateSiblings(blockUuid: String) {
        val cached = blockCache.get(Key.ByUuid(blockUuid))
        cached?.block?.parentId?.let { pid ->
            childrenIndex.remove(pid)
        }
        // Also invalidate the block itself to ensure fresh state
        invalidateBlock(blockUuid)
    }

    /**
     * Invalidate a hierarchy cache entry.
     */
    fun invalidateBlockHierarchy(uuid: String) {
        hierarchyCache.remove(uuid)
    }

    /**
     * Clear all caches.
     */
    fun clear() {
        blockCache.clear()
        hierarchyCache.clear()
        childrenIndex.clear()
    }

    /**
     * Get cache metrics.
     */
    fun getMetrics(): CacheMetrics = metrics.value

    private fun prefetchBlockChildren(blockId: Long, depth: Int) {
        if (depth <= 0 || !config.enablePrefetch) return

        scope.launch {
            try {
                val block = blockCache.get(Key.ById(blockId))
                if (block != null) {
                    val childrenIds = childrenIndex[blockId]
                    if (childrenIds == null) {
                        // Load children silently
                        val dummyUuid = "prefetch-${Random.nextLong()}"
                        delegate.getBlockChildren(block.block.uuid).first()
                    }
                    metrics.value = metrics.value.withPrefetch()
                }
            } catch (_: Exception) {
                // Silently ignore prefetch errors
            }
        }
    }

    private fun isHierarchyExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > config.hierarchyTtlMs
    }
}
