package com.logseq.kmp.repository

import com.logseq.kmp.cache.BlockCache
import com.logseq.kmp.cache.CacheConfig
import com.logseq.kmp.cache.CachedBlockRepository
import com.logseq.kmp.cache.CachedPageRepository
import com.logseq.kmp.cache.PageCache
import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.platform.EncryptionManager
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

class RepositoryFactoryImpl(
    private val useInMemoryDb: Boolean = true,
    private val dbFilePath: String = "logseq_benchmark.db",
    private val cacheConfig: CacheConfig = CacheConfig()
) : RepositoryFactory {

    private val database: LogseqDatabase by lazy {
        createDatabase()
    }

    private var blockCache: BlockCache? = null
    private var pageCache: PageCache? = null

    private fun getBlockCache(): BlockCache {
        return blockCache ?: BlockCache(cacheConfig, SqlDelightBlockRepository(database)).also {
            it.start()
            blockCache = it
        }
    }

    private fun getPageCache(): PageCache {
        return pageCache ?: PageCache(cacheConfig, SqlDelightPageRepository(database)).also {
            it.start()
            pageCache = it
        }
    }

    private fun createDatabase(): LogseqDatabase {
        val jdbcUrl = if (useInMemoryDb) {
            "jdbc:sqlite::memory:"
        } else {
            val dbFile = File(dbFilePath)
            if (dbFile.exists()) {
                dbFile.delete()
            }
            "jdbc:sqlite:$dbFilePath"
        }

        val driver = JdbcSqliteDriver(jdbcUrl)

        if (!useInMemoryDb) {
            configureDriver(driver)
        }

        LogseqDatabase.Schema.create(driver)
        return LogseqDatabase(driver)
    }

    private fun configureDriver(driver: JdbcSqliteDriver) {
        driver.execute(null, "PRAGMA journal_mode=WAL", 0)
        driver.execute(null, "PRAGMA synchronous=NORMAL", 0)
        driver.execute(null, "PRAGMA cache_size=-64000", 0)
        driver.execute(null, "PRAGMA temp_store=MEMORY", 0)
        driver.execute(null, "PRAGMA mmap_size=268435456", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        driver.execute(null, "PRAGMA journal_size_limit=67108864", 0)
    }

    override fun createBlockRepository(backend: GraphBackend, encryptionManager: EncryptionManager?): BlockRepository {
        val repo = when (backend) {
            GraphBackend.IN_MEMORY -> InMemoryBlockRepository()
            GraphBackend.DATASCRIPT -> DatascriptBlockRepository()
            GraphBackend.SQLDELIGHT -> {
                val cache = getBlockCache()
                CachedBlockRepository(SqlDelightBlockRepository(database), cache)
            }
            GraphBackend.KUZU -> throw NotImplementedError("Kuzu not configured")
            GraphBackend.NEO4J -> throw NotImplementedError("Neo4j not configured")
        }
        
        return if (encryptionManager != null) {
            EncryptedBlockRepository(repo, encryptionManager, "default") // TODO: pass graphId
        } else {
            repo
        }
    }

    override fun createPageRepository(backend: GraphBackend, encryptionManager: EncryptionManager?): PageRepository {
        val repo = when (backend) {
            GraphBackend.IN_MEMORY -> InMemoryPageRepository()
            GraphBackend.DATASCRIPT -> DatascriptPageRepository()
            GraphBackend.SQLDELIGHT -> {
                val cache = getPageCache()
                CachedPageRepository(SqlDelightPageRepository(database), cache)
            }
            GraphBackend.KUZU -> throw NotImplementedError("Kuzu not configured")
            GraphBackend.NEO4J -> throw NotImplementedError("Neo4j not configured")
        }

        return if (encryptionManager != null) {
            EncryptedPageRepository(repo, encryptionManager, "default")
        } else {
            repo
        }
    }

    override fun createPropertyRepository(backend: GraphBackend, encryptionManager: EncryptionManager?): PropertyRepository {
        val repo = when (backend) {
            GraphBackend.IN_MEMORY -> InMemoryPropertyRepository()
            GraphBackend.DATASCRIPT -> DatascriptPropertyRepository()
            GraphBackend.SQLDELIGHT -> SqlDelightPropertyRepository(database)
            GraphBackend.KUZU -> throw NotImplementedError("Kuzu not configured")
            GraphBackend.NEO4J -> throw NotImplementedError("Neo4j not configured")
        }

        return if (encryptionManager != null) {
            EncryptedPropertyRepository(repo, encryptionManager, "default")
        } else {
            repo
        }
    }

    override fun createReferenceRepository(backend: GraphBackend): ReferenceRepository {
        return when (backend) {
            GraphBackend.IN_MEMORY -> InMemoryReferenceRepository()
            GraphBackend.DATASCRIPT -> DatascriptReferenceRepository()
            GraphBackend.SQLDELIGHT -> SqlDelightReferenceRepository(database)
            GraphBackend.KUZU -> throw NotImplementedError("Kuzu not configured")
            GraphBackend.NEO4J -> throw NotImplementedError("Neo4j not configured")
        }
    }

    override fun createSearchRepository(backend: GraphBackend): SearchRepository {
        return when (backend) {
            GraphBackend.IN_MEMORY -> InMemorySearchRepository()
            GraphBackend.DATASCRIPT -> InMemorySearchRepository()
            GraphBackend.SQLDELIGHT -> InMemorySearchRepository()
            GraphBackend.KUZU -> throw NotImplementedError("Kuzu not configured")
            GraphBackend.NEO4J -> throw NotImplementedError("Neo4j not configured")
        }
    }

    /**
     * Get cache metrics for SQLDelight backend.
     */
    fun getCacheMetrics(): Pair<com.logseq.kmp.cache.CacheMetrics, com.logseq.kmp.cache.CacheMetrics>? {
        return blockCache?.let { bc ->
            pageCache?.let { pc ->
                bc.getMetrics() to pc.getMetrics()
            }
        }
    }

    /**
     * Clear all caches.
     */
    fun clearCaches() {
        blockCache?.clear()
        pageCache?.clear()
    }

    /**
     * Stop cache background processes.
     */
    fun stop() {
        blockCache?.stop()
        pageCache?.stop()
    }
}
