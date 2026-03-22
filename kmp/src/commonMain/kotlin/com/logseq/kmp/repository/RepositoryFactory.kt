package com.logseq.kmp.repository

import com.logseq.kmp.db.DriverFactory
import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.db.createDatabase
import com.logseq.kmp.platform.EncryptionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Factory implementation for creating repository instances.
 * Supports cross-platform database initialization and backend switching.
 */
class RepositoryFactoryImpl(
    private val driverFactory: DriverFactory,
    private val jdbcUrl: String = "jdbc:sqlite:logseq.db"
) : RepositoryFactory {

    private val database: LogseqDatabase by lazy {
        createDatabase(driverFactory, jdbcUrl)
    }

    private val instances = mutableMapOf<String, Any>()

    override fun createBlockRepository(backend: GraphBackend, encryptionManager: EncryptionManager?): BlockRepository {
        val repo = when (backend) {
            GraphBackend.IN_MEMORY -> getOrCreateInstance("block_in_memory") {
                InMemoryBlockRepository()
            }
            GraphBackend.DATASCRIPT -> getOrCreateInstance("block_datascript") {
                DatascriptBlockRepository()
            }
            GraphBackend.SQLDELIGHT -> getOrCreateInstance("block_sqldelight") {
                SqlDelightBlockRepository(database)
            }
            else -> throw NotImplementedError("Backend $backend not implemented")
        }
        
        return if (encryptionManager != null) {
            EncryptedBlockRepository(repo, encryptionManager, "default")
        } else {
            repo
        }
    }

    override fun createPageRepository(backend: GraphBackend, encryptionManager: EncryptionManager?): PageRepository {
        val repo = when (backend) {
            GraphBackend.IN_MEMORY -> getOrCreateInstance("page_in_memory") {
                InMemoryPageRepository()
            }
            GraphBackend.DATASCRIPT -> getOrCreateInstance("page_datascript") {
                DatascriptPageRepository()
            }
            GraphBackend.SQLDELIGHT -> getOrCreateInstance("page_sqldelight") {
                SqlDelightPageRepository(database)
            }
            else -> throw NotImplementedError("Backend $backend not implemented")
        }

        return if (encryptionManager != null) {
            EncryptedPageRepository(repo, encryptionManager, "default")
        } else {
            repo
        }
    }

    override fun createPropertyRepository(backend: GraphBackend, encryptionManager: EncryptionManager?): PropertyRepository {
        val repo = when (backend) {
            GraphBackend.IN_MEMORY -> getOrCreateInstance("property_in_memory") {
                InMemoryPropertyRepository()
            }
            GraphBackend.DATASCRIPT -> getOrCreateInstance("property_datascript") {
                DatascriptPropertyRepository()
            }
            GraphBackend.SQLDELIGHT -> getOrCreateInstance("property_sqldelight") {
                SqlDelightPropertyRepository(database)
            }
            else -> throw NotImplementedError("Backend $backend not implemented")
        }

        return if (encryptionManager != null) {
            EncryptedPropertyRepository(repo, encryptionManager, "default")
        } else {
            repo
        }
    }

    override fun createReferenceRepository(backend: GraphBackend): ReferenceRepository {
        return when (backend) {
            GraphBackend.IN_MEMORY -> getOrCreateInstance("reference_in_memory") {
                InMemoryReferenceRepository()
            }
            GraphBackend.DATASCRIPT -> getOrCreateInstance("reference_datascript") {
                DatascriptReferenceRepository()
            }
            GraphBackend.SQLDELIGHT -> getOrCreateInstance("reference_sqldelight") {
                SqlDelightReferenceRepository(database)
            }
            else -> throw NotImplementedError("Backend $backend not implemented")
        }
    }

    override fun createSearchRepository(backend: GraphBackend): SearchRepository {
        return when (backend) {
            GraphBackend.IN_MEMORY -> getOrCreateInstance("search_in_memory") {
                InMemorySearchRepository(
                    createPageRepository(backend),
                    createBlockRepository(backend)
                )
            }
            GraphBackend.DATASCRIPT -> getOrCreateInstance("search_datascript") {
                InMemorySearchRepository(
                    createPageRepository(backend),
                    createBlockRepository(backend)
                )
            }
            GraphBackend.SQLDELIGHT -> getOrCreateInstance("search_sqldelight") {
                SqlDelightSearchRepository(database)
            }
            else -> throw NotImplementedError("Backend $backend not implemented")
        }
    }

    fun createRepositorySet(backend: GraphBackend): RepositorySet {
        return RepositorySet(
            blockRepository = createBlockRepository(backend),
            pageRepository = createPageRepository(backend),
            propertyRepository = createPropertyRepository(backend),
            referenceRepository = createReferenceRepository(backend),
            searchRepository = createSearchRepository(backend)
        )
    }

    private inline fun <reified T : Any> getOrCreateInstance(key: String, factory: () -> T): T {
        val existing = instances[key]
        if (existing != null) return existing as T
        val newInstance = factory()
        instances[key] = newInstance
        return newInstance
    }
}

data class RepositorySet(
    val blockRepository: BlockRepository,
    val pageRepository: PageRepository,
    val propertyRepository: PropertyRepository,
    val referenceRepository: ReferenceRepository,
    val searchRepository: SearchRepository
)

/**
 * Global access to repositories.
 */
object Repositories {
    private lateinit var factory: RepositoryFactoryImpl
    private var isInitialized = false

    fun initialize(driverFactory: DriverFactory, jdbcUrl: String = "jdbc:sqlite:logseq.db") {
        factory = RepositoryFactoryImpl(driverFactory, jdbcUrl)
        isInitialized = true
    }

    fun getFactory(): RepositoryFactory = factory

    fun block(backend: GraphBackend = GraphBackend.SQLDELIGHT): BlockRepository =
        factory.createBlockRepository(backend)

    fun page(backend: GraphBackend = GraphBackend.SQLDELIGHT): PageRepository =
        factory.createPageRepository(backend)

    fun property(backend: GraphBackend = GraphBackend.SQLDELIGHT): PropertyRepository =
        factory.createPropertyRepository(backend)

    fun reference(backend: GraphBackend = GraphBackend.SQLDELIGHT): ReferenceRepository =
        factory.createReferenceRepository(backend)

    fun search(backend: GraphBackend = GraphBackend.SQLDELIGHT): SearchRepository =
        factory.createSearchRepository(backend)
}
