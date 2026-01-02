package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlin.Result.Companion.success
import kotlin.Result.Companion.failure

/**
 * SQLDelight implementation of BlockRepository.
 * Uses recursive CTEs for hierarchical queries and optimized SQL operations.
 */
class SqlDelightBlockRepository(
    private val database: LogseqDatabase
) : BlockRepository {

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> = flow {
        try {
            val block = database.blockQueries.getBlockByUuid(uuid).executeAsOneOrNull()
                ?.toBlock()
            emit(success(block))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = database.blockQueries.getBlockChildren(blockUuid)
                .executeAsList()
                .map { it.toBlock() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> = flow {
        try {
            // Use the block_hierarchy view with recursive CTE
            val hierarchy = database.blockHierarchyQueries
                .getBlockHierarchy(rootUuid)
                .executeAsList()
                .map { BlockWithDepth(it.toBlock(), it.depth.toInt()) }
            emit(success(hierarchy))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            // Use the block_ancestry view
            val ancestors = database.blockAncestryQueries
                .getBlockAncestors(blockUuid)
                .executeAsList()
                .map { it.toBlock() }
            emit(success(ancestors))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> = flow {
        try {
            val parent = database.blockQueries.getBlockParent(blockUuid)
                .executeAsOneOrNull()
                ?.toBlock()
            emit(success(parent))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val siblings = database.blockQueries.getBlockSiblings(blockUuid)
                .executeAsList()
                .map { it.toBlock() }
            emit(success(siblings))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        return try {
            database.blockQueries.insertBlock(
                uuid = block.uuid,
                page_id = block.pageId, // Need to resolve UUID to ID
                parent_id = block.parentId, // Need to resolve UUID to ID or null
                left_id = block.leftId, // Need to resolve UUID to ID or null
                content = block.content,
                level = block.level.toLong(),
                position = block.position.toLong(),
                created_at = block.createdAt.toEpochMilliseconds(),
                updated_at = block.updatedAt.toEpochMilliseconds(),
                properties = block.properties.toString() // JSON serialization
            )
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        return try {
            if (deleteChildren) {
                // Delete recursively - this would need a more complex query
                database.blockQueries.deleteBlockWithChildren(blockUuid)
            } else {
                database.blockQueries.deleteBlock(blockUuid)
            }
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int): Result<Unit> {
        return try {
            database.blockQueries.moveBlock(
                uuid = blockUuid,
                new_parent_uuid = newParentUuid,
                new_position = newPosition.toLong()
            )
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }
}

/**
 * SQLDelight implementation of PageRepository.
 */
class SqlDelightPageRepository(
    private val database: LogseqDatabase
) : PageRepository {

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> = flow {
        try {
            val page = database.pageQueries.getPageByUuid(uuid).executeAsOneOrNull()
                ?.toPage()
            emit(success(page))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getPageByName(name: String): Flow<Result<Page?>> = flow {
        try {
            val page = database.pageQueries.getPageByName(name).executeAsOneOrNull()
                ?.toPage()
            emit(success(page))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>> = flow {
        try {
            val pages = database.pageQueries.getPagesInNamespace(namespace)
                .executeAsList()
                .map { it.toPage() }
            emit(success(pages))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getAllPages(): Flow<Result<List<Page>>> = flow {
        try {
            val pages = database.pageQueries.getAllPages()
                .executeAsList()
                .map { it.toPage() }
            emit(success(pages))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> = flow {
        try {
            val pages = database.pageQueries.getRecentPages(limit.toLong())
                .executeAsList()
                .map { it.toPage() }
            emit(success(pages))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override suspend fun savePage(page: Page): Result<Unit> {
        return try {
            database.pageQueries.insertPage(
                uuid = page.uuid,
                name = page.name,
                namespace = page.namespace,
                file_path = page.filePath,
                created_at = page.createdAt.toEpochMilliseconds(),
                updated_at = page.updatedAt.toEpochMilliseconds(),
                properties = page.properties.toString()
            )
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        return try {
            database.pageQueries.deletePage(pageUuid)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }
}

/**
 * SQLDelight implementation of PropertyRepository.
 */
class SqlDelightPropertyRepository(
    private val database: LogseqDatabase
) : PropertyRepository {

    override fun getPropertiesForBlock(blockUuid: String): Flow<Result<List<Property>>> = flow {
        try {
            val properties = database.propertyQueries.getPropertiesForBlock(blockUuid)
                .executeAsList()
                .map { it.toProperty() }
            emit(success(properties))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getProperty(blockUuid: String, key: String): Flow<Result<Property?>> = flow {
        try {
            val property = database.propertyQueries.getProperty(blockUuid, key)
                .executeAsOneOrNull()
                ?.toProperty()
            emit(success(property))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override suspend fun saveProperty(property: Property): Result<Unit> {
        return try {
            database.propertyQueries.insertProperty(
                block_id = property.blockId, // This should be the block's ID, not UUID
                key = property.key,
                value = property.value,
                created_at = property.createdAt.toEpochMilliseconds()
            )
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun deleteProperty(blockUuid: String, key: String): Result<Unit> {
        return try {
            database.propertyQueries.deleteProperty(blockUuid, key)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = database.propertyQueries.getBlocksWithPropertyKey(key)
                .executeAsList()
                .map { it.toBlock() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = database.propertyQueries.getBlocksWithPropertyValue(key, value)
                .executeAsList()
                .map { it.toBlock() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }
}

/**
 * SQLDelight implementation of ReferenceRepository.
 */
class SqlDelightReferenceRepository(
    private val database: LogseqDatabase
) : ReferenceRepository {

    override fun getOutgoingReferences(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = database.referenceQueries.getOutgoingReferences(blockUuid)
                .executeAsList()
                .map { it.toBlock() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getIncomingReferences(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = database.referenceQueries.getIncomingReferences(blockUuid)
                .executeAsList()
                .map { it.toBlock() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getAllReferences(blockUuid: String): Flow<Result<BlockReferences>> = flow {
        try {
            val outgoing = database.referenceQueries.getOutgoingReferences(blockUuid)
                .executeAsList()
                .map { it.toBlock() }
            val incoming = database.referenceQueries.getIncomingReferences(blockUuid)
                .executeAsList()
                .map { it.toBlock() }
            emit(success(BlockReferences(outgoing, incoming)))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override suspend fun addReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> {
        return try {
            database.referenceQueries.insertReference(
                from_block_uuid = fromBlockUuid,
                to_block_uuid = toBlockUuid,
                created_at = Clock.System.now().toEpochMilliseconds()
            )
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun removeReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> {
        return try {
            database.referenceQueries.deleteReference(fromBlockUuid, toBlockUuid)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override fun getOrphanedBlocks(): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = database.referenceQueries.getOrphanedBlocks()
                .executeAsList()
                .map { it.toBlock() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getMostConnectedBlocks(limit: Int): Flow<Result<List<BlockWithReferenceCount>>> = flow {
        try {
            val blocks = database.referenceQueries.getMostConnectedBlocks(limit.toLong())
                .executeAsList()
                .map { BlockWithReferenceCount(it.toBlock(), it.reference_count.toInt()) }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }
}

/**
 * SQLDelight implementation of SearchRepository.
 */
class SqlDelightSearchRepository(
    private val database: LogseqDatabase
) : SearchRepository {

    override fun searchBlocksByContent(query: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = database.searchQueries.searchBlocksByContent("%$query%")
                .executeAsList()
                .map { it.toBlock() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun searchPagesByTitle(query: String): Flow<Result<List<Page>>> = flow {
        try {
            val pages = database.searchQueries.searchPagesByTitle("%$query%")
                .executeAsList()
                .map { it.toPage() }
            emit(success(pages))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun findBlocksReferencing(query: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = database.searchQueries.findBlocksReferencing("%$query%")
                .executeAsList()
                .map { it.toBlock() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun searchWithFilters(searchRequest: SearchRequest): Flow<Result<SearchResult>> = flow {
        try {
            // This would need more complex queries based on filters
            val blocks = if (searchRequest.query != null) {
                database.searchQueries.searchBlocksByContent("%${searchRequest.query}%")
                    .executeAsList()
                    .map { it.toBlock() }
            } else {
                emptyList()
            }

            val pages = if (searchRequest.query != null) {
                database.searchQueries.searchPagesByTitle("%${searchRequest.query}%")
                    .executeAsList()
                    .map { it.toPage() }
            } else {
                emptyList()
            }

            val result = SearchResult(
                blocks = blocks.take(searchRequest.limit),
                pages = pages.take(searchRequest.limit),
                totalCount = blocks.size + pages.size,
                hasMore = (blocks.size + pages.size) > searchRequest.limit
            )

            emit(success(result))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }
}