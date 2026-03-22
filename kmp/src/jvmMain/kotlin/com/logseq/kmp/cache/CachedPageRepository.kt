package com.logseq.kmp.cache

import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.PageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * Cached wrapper around PageRepository.
 * Provides progressive loading with LRU, TTL, and namespace indexing.
 */
class CachedPageRepository(
    private val delegate: PageRepository,
    private val cache: PageCache
) : PageRepository {

    override fun getPageById(id: Long): Flow<Result<Page?>> {
        return delegate.getPageById(id)
    }

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> {
        return cache.getPageByUuid(uuid)
    }

    override fun getPageByName(name: String): Flow<Result<Page?>> {
        return cache.getPageByName(name)
    }

    override fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>> {
        return cache.getPagesInNamespace(namespace)
    }

    override fun getAllPages(): Flow<Result<List<Page>>> {
        return cache.getAllPages()
    }

    override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> {
        return cache.getRecentPages(limit)
    }

    override fun getJournalPages(limit: Int, offset: Int): Flow<Result<List<Page>>> {
        return delegate.getJournalPages(limit, offset)
    }

    override suspend fun savePage(page: Page): Result<Long> {
        return cache.savePage(page)
    }

    override suspend fun toggleFavorite(pageUuid: String): Result<Unit> {
        return delegate.toggleFavorite(pageUuid).also {
            cache.invalidatePage(pageUuid)
        }
    }

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> {
        return cache.renamePage(pageUuid, newName)
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        return cache.deletePage(pageUuid)
    }

    override fun countPages(): Flow<Result<Long>> {
        return delegate.countPages()
    }

    override suspend fun clear() {
        delegate.clear()
        cache.clear()
    }

    /**
     * Get cache metrics.
     */
    fun getCacheMetrics(): CacheMetrics = cache.getMetrics()

    /**
     * Invalidate cache entry.
     */
    fun invalidatePage(uuid: String) {
        cache.invalidatePage(uuid)
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

    /**
     * Stop cache background processes.
     */
    fun stop() {
        cache.stop()
    }
}
