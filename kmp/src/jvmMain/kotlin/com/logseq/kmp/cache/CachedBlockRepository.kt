package com.logseq.kmp.cache

import com.logseq.kmp.model.Block
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.BlockWithDepth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * Cached wrapper around BlockRepository.
 * Provides progressive loading with LRU, TTL, and prefetch.
 */
class CachedBlockRepository(
    private val delegate: BlockRepository,
    private val cache: BlockCache
) : BlockRepository {

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> {
        return cache.getBlockByUuid(uuid)
    }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> {
        return cache.getBlockChildren(blockUuid)
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> {
        return cache.getBlockHierarchy(rootUuid)
    }

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> {
        return cache.getBlockAncestors(blockUuid)
    }

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> {
        return cache.getBlockParent(blockUuid)
    }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> {
        // For siblings, we use the delegate
        // Could cache this as well if needed
        return delegate.getBlockSiblings(blockUuid)
    }

    override fun getBlocksForPage(pageId: Long): Flow<Result<List<Block>>> {
        return delegate.getBlocksForPage(pageId)
    }

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> {
        return delegate.searchBlocksByContent(query, limit, offset)
    }

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> {
        // Naive implementation looping saveBlock to avoid changing BlockCache interface for now
        // In a real implementation, BlockCache should also support batch operations
        return try {
            blocks.forEach { cache.saveBlock(it) }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        return cache.saveBlock(block)
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        return cache.deleteBlock(blockUuid, deleteChildren)
    }

    override suspend fun moveBlock(
        blockUuid: String,
        newParentUuid: String?,
        newPosition: Int
    ): Result<Unit> {
        return cache.moveBlock(blockUuid, newParentUuid, newPosition)
    }

    override suspend fun indentBlock(blockUuid: String): Result<Unit> {
        return delegate.indentBlock(blockUuid)
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> {
        return delegate.outdentBlock(blockUuid)
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> {
        return delegate.moveBlockUp(blockUuid).also {
            cache.invalidateSiblings(blockUuid)
        }
    }

    override suspend fun moveBlockDown(blockUuid: String): Result<Unit> {
        return delegate.moveBlockDown(blockUuid).also {
            cache.invalidateSiblings(blockUuid)
        }
    }

    override suspend fun mergeBlocks(blockUuid: String, nextBlockUuid: String, separator: String): Result<Unit> {
        return delegate.mergeBlocks(blockUuid, nextBlockUuid, separator).also {
            cache.invalidateBlock(blockUuid)
            cache.invalidateBlock(nextBlockUuid)
            cache.invalidateSiblings(blockUuid)
        }
    }

    override suspend fun splitBlock(blockUuid: String, cursorPosition: Int): Result<Block> {
        return delegate.splitBlock(blockUuid, cursorPosition).also {
            cache.invalidateBlock(blockUuid)
            cache.invalidateSiblings(blockUuid)
        }
    }

    override fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        return delegate.getLinkedReferences(pageName)
    }

    override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        return delegate.getUnlinkedReferences(pageName)
    }

    /**
     * Get cache metrics.
     */
    fun getCacheMetrics(): CacheMetrics = cache.getMetrics()

    /**
     * Invalidate cache entry.
     */
    fun invalidateBlock(uuid: String) {
        cache.invalidateBlock(uuid)
    }

    /**
     * Clear all caches.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Start cache background processes.
     */
    fun start() {
        cache.start()
    }

    override suspend fun deleteBlocksForPage(pageId: Long): Result<Unit> {
        return delegate.deleteBlocksForPage(pageId).also {
            // cache.invalidatePage(pageId)
        }
    }

    override suspend fun clear() {
        delegate.clear()
        cache.clear()
    }
}
