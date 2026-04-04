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

    override fun getBlocksForPage(pageUuid: String): Flow<Result<List<Block>>> {
        return delegate.getBlocksForPage(pageUuid)
    }

    override fun getBlocksForPageHierarchy(pageUuid: String): Flow<Result<List<BlockWithDepth>>> {
        return delegate.getBlocksForPageHierarchy(pageUuid)
    }

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> {
        return delegate.searchBlocksByContent(query, limit, offset)
    }

    override fun getBlocksByContentPattern(pattern: String): Flow<Result<List<Block>>> {
        return delegate.getBlocksByContentPattern(pattern)
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

    override suspend fun deleteBlocksForPage(pageUuid: String): Result<Unit> {
        return delegate.deleteBlocksForPage(pageUuid).also {
            // Invalidate all blocks from this page in cache
            // Simplified: clear the cache if a whole page is deleted
            cache.clear()
        }
    }

    override suspend fun createBlocks(blocks: List<Block>): Result<Unit> {
        return delegate.createBlocks(blocks).also {
            blocks.forEach { cache.invalidateBlock(it.uuid) }
        }
    }

    override suspend fun updateBlocks(blocks: List<Block>): Result<Unit> {
        return delegate.updateBlocks(blocks).also {
            blocks.forEach { cache.invalidateBlock(it.uuid) }
        }
    }

    override suspend fun deleteBlocks(blockUuids: List<String>): Result<Unit> {
        return delegate.deleteBlocks(blockUuids).also {
            blockUuids.forEach { cache.invalidateBlock(it) }
        }
    }

    override fun getBlockMetadata(blockUuid: String): Flow<Result<Map<String, String>>> = delegate.getBlockMetadata(blockUuid)
    override suspend fun updateBlockMetadata(blockUuid: String, metadata: Map<String, String>): Result<Unit> = delegate.updateBlockMetadata(blockUuid, metadata)
    override suspend fun deleteBlockMetadata(blockUuid: String, key: String): Result<Unit> = delegate.deleteBlockMetadata(blockUuid, key)
    override fun getBlockProperties(blockUuid: String): Flow<Result<List<com.logseq.kmp.model.Property>>> = delegate.getBlockProperties(blockUuid)
    override fun getBlockProperty(blockUuid: String, key: String): Flow<Result<com.logseq.kmp.model.Property?>> = delegate.getBlockProperty(blockUuid, key)
    override suspend fun saveBlockProperty(property: com.logseq.kmp.model.Property): Result<Unit> = delegate.saveBlockProperty(property)
    override suspend fun deleteBlockProperty(blockUuid: String, key: String): Result<Unit> = delegate.deleteBlockProperty(blockUuid, key)
    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> = delegate.getBlocksWithPropertyKey(key)
    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> = delegate.getBlocksWithPropertyValue(key, value)
    override fun getBlockVersionHistory(blockUuid: String): Flow<Result<List<com.logseq.kmp.repository.BlockVersion>>> = delegate.getBlockVersionHistory(blockUuid)
    override fun getBlockVersion(blockUuid: String, version: Long): Flow<Result<com.logseq.kmp.repository.BlockVersion?>> = delegate.getBlockVersion(blockUuid, version)
    override suspend fun createBlockVersion(blockUuid: String, changeDescription: String): Result<Unit> = delegate.createBlockVersion(blockUuid, changeDescription)
    override suspend fun getStatistics(): Result<com.logseq.kmp.repository.BlockRepositoryStatistics> = delegate.getStatistics()
    override suspend fun optimize(): Result<Unit> = delegate.optimize()
    override suspend fun validateIntegrity(): Result<com.logseq.kmp.repository.ValidationReport> = delegate.validateIntegrity()
    override suspend fun setCachingEnabled(enabled: Boolean): Result<Unit> = delegate.setCachingEnabled(enabled)
    override suspend fun clearCache(): Result<Unit> {
        cache.clear()
        return delegate.clearCache()
    }
    override suspend fun getCacheStatistics(): Result<com.logseq.kmp.repository.CacheStatistics> = delegate.getCacheStatistics()
    override suspend fun setEncryptionManager(encryptionManager: com.logseq.kmp.platform.EncryptionManager): Result<Unit> = delegate.setEncryptionManager(encryptionManager)
    override fun isEncrypted(): Boolean = delegate.isEncrypted()

    override suspend fun clear() {
        delegate.clear()
        cache.clear()
    }
}
