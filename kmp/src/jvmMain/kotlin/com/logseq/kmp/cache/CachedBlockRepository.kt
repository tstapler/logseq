package com.logseq.kmp.cache

import com.logseq.kmp.model.Block
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.BlockWithDepth
import com.logseq.kmp.repository.DuplicateGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * Cached wrapper around BlockRepository.
 * Provides progressive loading with LRU, TTL, and prefetch.
 * Updated to use UUID-native storage.
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
        return delegate.getBlockSiblings(blockUuid)
    }

    override fun getBlocksForPage(pageUuid: String): Flow<Result<List<Block>>> {
        return delegate.getBlocksForPage(pageUuid)
    }

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> {
        return delegate.searchBlocksByContent(query, limit, offset)
    }

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> {
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

    override fun getLinkedReferences(pageName: String, limit: Int, offset: Int): Flow<Result<List<Block>>> {
        return delegate.getLinkedReferences(pageName, limit, offset)
    }

    override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        return delegate.getUnlinkedReferences(pageName)
    }

    override fun getUnlinkedReferences(pageName: String, limit: Int, offset: Int): Flow<Result<List<Block>>> {
        return delegate.getUnlinkedReferences(pageName, limit, offset)
    }

    fun getCacheMetrics(): CacheMetrics = cache.getMetrics()

    fun invalidateBlock(uuid: String) {
        cache.invalidateBlock(uuid)
    }

    fun clearCache() {
        cache.clear()
    }

    fun start() {
        cache.start()
    }

    override suspend fun deleteBlocksForPage(pageUuid: String): Result<Unit> {
        return delegate.deleteBlocksForPage(pageUuid)
    }

    override suspend fun clear() {
        delegate.clear()
        cache.clear()
    }

    override fun findDuplicateBlocks(limit: Int): Flow<Result<List<DuplicateGroup>>> =
        delegate.findDuplicateBlocks(limit)
}
