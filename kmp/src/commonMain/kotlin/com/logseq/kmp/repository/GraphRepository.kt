package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Property
import com.logseq.kmp.platform.EncryptionManager
import kotlinx.coroutines.flow.Flow
import kotlin.Result

/**
 * Repository interfaces for graph database operations in Logseq.
 * Designed to support hierarchical block structures and reference relationships.
 */

// ===== CORE DOMAIN INTERFACES =====

/**
 * Repository for block operations with hierarchical support.
 * Handles the core hierarchical structure of Logseq's block system.
 */
interface BlockRepository {
    /**
     * Retrieve a single block by its UUID
     */
    fun getBlockByUuid(uuid: String): Flow<Result<Block?>>

    /**
     * Get all immediate children of a block (one level deep)
     */
    fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>>

    /**
     * Get complete hierarchy starting from a root block (recursive)
     * Returns all descendants with their depth in hierarchy
     */
    fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>>

    /**
     * Get all ancestors of a block (from immediate parent up to root)
     */
    fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>>

    /**
     * Get the immediate parent of a block
     */
    fun getBlockParent(blockUuid: String): Flow<Result<Block?>>

    /**
     * Get sibling blocks (blocks with same parent)
     */
    fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>>

    /**
     * Get all blocks for a specific page
     */
    fun getBlocksForPage(pageId: Long): Flow<Result<List<Block>>>

    /**
     * Save a new or updated block
     */
    suspend fun saveBlock(block: Block): Result<Unit>

    /**
     * Save multiple blocks in a batch operation
     */
    suspend fun saveBlocks(blocks: List<Block>): Result<Unit>

    /**
     * Delete a block and optionally its children
     */
    suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean = false): Result<Unit>

    /**
     * Move a block to a new parent and/or position
     */
    suspend fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int): Result<Unit>

    /**
     * Indent a block (move it to be a child of its preceding sibling)
     */
    suspend fun indentBlock(blockUuid: String): Result<Unit>

    /**
     * Outdent a block (move it to be a sibling of its parent)
     */
    suspend fun outdentBlock(blockUuid: String): Result<Unit>

    /**
     * Move a block up among its siblings
     */
    suspend fun moveBlockUp(blockUuid: String): Result<Unit>

    /**
     * Move a block down among its siblings
     */
    suspend fun moveBlockDown(blockUuid: String): Result<Unit>

    /**
     * Find all blocks that contain a wiki link to the given page name
     * (i.e., blocks containing [[Page Name]])
     */
    fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>>

    /**
     * Find all blocks that mention the page name as plain text
     * (not as a wiki link)
     */
    fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>>

    /**
     * Search blocks by content
     */
    fun searchBlocksByContent(query: String): Flow<Result<List<Block>>>
}

/**
 * Repository for page operations.
 * Pages are special blocks that serve as roots of block hierarchies.
 */
interface PageRepository {
    /**
     * Get a page by its UUID
     */
    fun getPageByUuid(uuid: String): Flow<Result<Page?>>

    /**
     * Get a page by its name/title
     */
    fun getPageByName(name: String): Flow<Result<Page?>>

    /**
     * Get all pages in a namespace
     */
    fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>>

    /**
     * Get all pages (with optional filtering)
     */
    fun getAllPages(): Flow<Result<List<Page>>>

    /**
     * Get recently modified pages
     */
    fun getRecentPages(limit: Int = 50): Flow<Result<List<Page>>>

    /**
     * Save a new or updated page
     */
    suspend fun savePage(page: Page): Result<Unit>

    /**
     * Rename a page
     * Updates the page name and associated indexes
     */
    suspend fun renamePage(pageUuid: String, newName: String): Result<Unit>

    /**
     * Delete a page
     */
    suspend fun deletePage(pageUuid: String): Result<Unit>
}

/**
 * Repository for property operations.
 * Properties are key-value metadata attached to blocks.
 */
interface PropertyRepository {
    /**
     * Get all properties for a specific block
     */
    fun getPropertiesForBlock(blockUuid: String): Flow<Result<List<Property>>>

    /**
     * Get a specific property by block UUID and key
     */
    fun getProperty(blockUuid: String, key: String): Flow<Result<Property?>>

    /**
     * Save a property (create or update)
     */
    suspend fun saveProperty(property: Property): Result<Unit>

    /**
     * Delete a property
     */
    suspend fun deleteProperty(blockUuid: String, key: String): Result<Unit>

    /**
     * Get all blocks that have a specific property key
     */
    fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>>

    /**
     * Get all blocks that have a specific property value
     */
    fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>>
}

// ===== REFERENCE & RELATIONSHIP INTERFACES =====

/**
 * Repository for managing block-to-block references.
 * References are the links between blocks that form Logseq's knowledge graph.
 */
interface ReferenceRepository {
    /**
     * Get all blocks referenced by a specific block
     */
    fun getOutgoingReferences(blockUuid: String): Flow<Result<List<Block>>>

    /**
     * Get all blocks that reference a specific block
     */
    fun getIncomingReferences(blockUuid: String): Flow<Result<List<Block>>>

    /**
     * Get all references (bidirectional) for a block
     */
    fun getAllReferences(blockUuid: String): Flow<Result<BlockReferences>>

    /**
     * Add a reference from one block to another
     */
    suspend fun addReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit>

    /**
     * Remove a reference between blocks
     */
    suspend fun removeReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit>

    /**
     * Get blocks that are not referenced by any other block (orphans)
     */
    fun getOrphanedBlocks(): Flow<Result<List<Block>>>

    /**
     * Get the most connected blocks (by reference count)
     */
    fun getMostConnectedBlocks(limit: Int = 20): Flow<Result<List<BlockWithReferenceCount>>>
}

// ===== SEARCH & QUERY INTERFACES =====

/**
 * Repository for search operations across the graph.
 * Supports full-text search and graph-based queries.
 */
interface SearchRepository {
    /**
     * Search blocks by content (full-text search)
     */
    fun searchBlocksByContent(query: String, limit: Int = 50, offset: Int = 0): Flow<Result<List<Block>>>

    /**
     * Search pages by title/name
     */
    fun searchPagesByTitle(query: String, limit: Int = 20): Flow<Result<List<Page>>>

    /**
     * Find blocks that reference specific content
     */
    fun findBlocksReferencing(blockUuid: String): Flow<Result<List<Block>>>

    /**
     * Advanced graph search with filters
     */
    fun searchWithFilters(searchRequest: SearchRequest): Flow<Result<SearchResult>>
}

// ===== DATA STRUCTURES =====

/**
 * Represents a block with its depth in a hierarchy
 */
data class BlockWithDepth(
    val block: Block,
    val depth: Int
)

/**
 * Represents bidirectional references for a block
 */
data class BlockReferences(
    val outgoing: List<Block>,  // blocks this block references
    val incoming: List<Block>   // blocks that reference this block
)

/**
 * Represents a block with its reference count
 */
data class BlockWithReferenceCount(
    val block: Block,
    val referenceCount: Int
)

/**
 * Search request with multiple filters
 */
data class SearchRequest(
    val query: String? = null,
    val pageUuid: String? = null,
    val propertyFilters: Map<String, String> = emptyMap(),
    val dateRange: DateRange? = null,
    val limit: Int = 50,
    val offset: Int = 0
)

/**
 * Date range for filtering
 */
data class DateRange(
    val startDate: kotlinx.datetime.Instant? = null,
    val endDate: kotlinx.datetime.Instant? = null
)

/**
 * Search result with metadata
 */
data class SearchResult(
    val blocks: List<Block>,
    val pages: List<Page>,
    val totalCount: Int,
    val hasMore: Boolean
)

// ===== BACKEND ENUMERATION =====

/**
 * Supported graph database backends for evaluation
 */
enum class GraphBackend {
    SQLDELIGHT,
    DATASCRIPT,
    KUZU,
    NEO4J,
    IN_MEMORY  // For testing and reference implementation
}

// ===== REPOSITORY FACTORY =====

/**
 * Factory for creating repository instances based on backend type
 */
interface RepositoryFactory {
    fun createBlockRepository(backend: GraphBackend, encryptionManager: EncryptionManager? = null): BlockRepository
    fun createPageRepository(backend: GraphBackend, encryptionManager: EncryptionManager? = null): PageRepository
    fun createPropertyRepository(backend: GraphBackend, encryptionManager: EncryptionManager? = null): PropertyRepository
    fun createReferenceRepository(backend: GraphBackend): ReferenceRepository
    fun createSearchRepository(backend: GraphBackend): SearchRepository
}
