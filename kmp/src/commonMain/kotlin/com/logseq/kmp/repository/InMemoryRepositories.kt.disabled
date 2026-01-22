package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.Result.Companion.success
import kotlin.Result.Companion.failure

/**
 * In-memory reference implementation of all repository interfaces.
 * Provides a working baseline for testing repository contracts and performance benchmarking.
 *
 * Uses thread-safe collections to support concurrent access.
 */

// ===== SHARED DATA STRUCTURES =====

private data class BlockNode(
    val block: Block,
    val children: MutableList<String> = CopyOnWriteArrayList(), // UUIDs of child blocks
    var parentUuid: String? = null
)

private data class ReferenceEdge(
    val fromUuid: String,
    val toUuid: String
)

/**
 * In-memory graph database implementation
 */
class InMemoryGraphDatabase {
    // Thread-safe storage
    private val blocks = ConcurrentHashMap<String, BlockNode>()
    private val pages = ConcurrentHashMap<String, Page>()
    private val properties = ConcurrentHashMap<Pair<String, String>, Property>() // (blockUuid, key) -> Property
    private val references = CopyOnWriteArrayList<ReferenceEdge>()

    // ===== BLOCK OPERATIONS =====

    fun saveBlock(block: Block) {
        val existing = blocks[block.uuid]
        val children = existing?.children ?: CopyOnWriteArrayList()
        val parentUuid = existing?.parentUuid

        blocks[block.uuid] = BlockNode(block, children, parentUuid)
    }

    fun getBlock(uuid: String): Block? = blocks[uuid]?.block

    fun deleteBlock(uuid: String, deleteChildren: Boolean = false) {
        val node = blocks[uuid] ?: return

        // Remove from parent's children list
        node.parentUuid?.let { parentUuid ->
            blocks[parentUuid]?.children?.remove(uuid)
        }

        // Handle children
        if (deleteChildren) {
            // Recursively delete all children
            val childrenToDelete = ArrayList(node.children)
            for (childUuid in childrenToDelete) {
                deleteBlock(childUuid, true)
            }
        } else {
            // Re-parent children to this block's parent
            for (childUuid in node.children) {
                blocks[childUuid]?.parentUuid = node.parentUuid
                node.parentUuid?.let { parentUuid ->
                    blocks[parentUuid]?.children?.add(childUuid)
                }
            }
        }

        // Remove block and its properties
        blocks.remove(uuid)
        properties.keys.removeIf { it.first == uuid }

        // Remove references
        references.removeIf { it.fromUuid == uuid || it.toUuid == uuid }
    }

    fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int) {
        val node = blocks[blockUuid] ?: return

        // Remove from old parent
        node.parentUuid?.let { oldParentUuid ->
            blocks[oldParentUuid]?.children?.remove(blockUuid)
        }

        // Add to new parent
        node.parentUuid = newParentUuid
        newParentUuid?.let { parentUuid ->
            val parentNode = blocks[parentUuid] ?: return
            parentNode.children.add(newPosition.coerceAtMost(parentNode.children.size), blockUuid)
        }
    }

    fun getBlockChildren(blockUuid: String): List<Block> {
        return blocks[blockUuid]?.children?.mapNotNull { blocks[it]?.block } ?: emptyList()
    }

    fun getBlockHierarchy(rootUuid: String): List<BlockWithDepth> {
        val result = mutableListOf<BlockWithDepth>()
        buildHierarchy(rootUuid, 0, result)
        return result
    }

    private fun buildHierarchy(uuid: String, depth: Int, result: MutableList<BlockWithDepth>) {
        val node = blocks[uuid] ?: return
        result.add(BlockWithDepth(node.block, depth))

        for (childUuid in node.children) {
            buildHierarchy(childUuid, depth + 1, result)
        }
    }

    fun getBlockAncestors(blockUuid: String): List<Block> {
        val result = mutableListOf<Block>()
        var currentUuid = blocks[blockUuid]?.parentUuid

        while (currentUuid != null) {
            val parentBlock = blocks[currentUuid]?.block
            if (parentBlock != null) {
                result.add(parentBlock)
                currentUuid = blocks[currentUuid]?.parentUuid
            } else {
                break
            }
        }

        return result
    }

    fun getBlockParent(blockUuid: String): Block? {
        return blocks[blockUuid]?.parentUuid?.let { blocks[it]?.block }
    }

    fun getBlockSiblings(blockUuid: String): List<Block> {
        val parentUuid = blocks[blockUuid]?.parentUuid ?: return emptyList()
        return blocks[parentUuid]?.children
            ?.filter { it != blockUuid }
            ?.mapNotNull { blocks[it]?.block }
            ?: emptyList()
    }

    // ===== PAGE OPERATIONS =====

    fun savePage(page: Page) {
        pages[page.uuid] = page
    }

    fun getPage(uuid: String): Page? = pages[uuid]

    fun getPageByName(name: String): Page? {
        return pages.values.find { it.name == name }
    }

    fun getPagesInNamespace(namespace: String): List<Page> {
        return pages.values.filter { it.namespace == namespace }
    }

    fun getAllPages(): List<Page> = pages.values.toList()

    fun getRecentPages(limit: Int = 50): List<Page> {
        return pages.values.sortedByDescending { it.updatedAt }.take(limit)
    }

    fun deletePage(pageUuid: String) {
        pages.remove(pageUuid)
        // Note: In a real implementation, we'd also need to handle associated blocks
    }

    // ===== PROPERTY OPERATIONS =====

    fun saveProperty(property: Property) {
        properties[property.blockId to property.key] = property
    }

    fun getPropertiesForBlock(blockUuid: String): List<Property> {
        return properties.filterKeys { it.first == blockUuid }.values.toList()
    }

    fun getProperty(blockUuid: String, key: String): Property? {
        return properties[blockUuid to key]
    }

    fun deleteProperty(blockUuid: String, key: String) {
        properties.remove(blockUuid to key)
    }

    fun getBlocksWithPropertyKey(key: String): List<Block> {
        return properties.filterKeys { it.second == key }
            .keys.mapNotNull { blocks[it.first]?.block }
    }

    fun getBlocksWithPropertyValue(key: String, value: String): List<Block> {
        return properties.filter { it.key.second == key && it.value.value == value }
            .keys.mapNotNull { blocks[it.first]?.block }
    }

    // ===== REFERENCE OPERATIONS =====

    fun addReference(fromUuid: String, toUuid: String) {
        if (references.none { it.fromUuid == fromUuid && it.toUuid == toUuid }) {
            references.add(ReferenceEdge(fromUuid, toUuid))
        }
    }

    fun removeReference(fromUuid: String, toUuid: String) {
        references.removeIf { it.fromUuid == fromUuid && it.toUuid == toUuid }
    }

    fun getOutgoingReferences(blockUuid: String): List<Block> {
        return references.filter { it.fromUuid == blockUuid }
            .mapNotNull { blocks[it.toUuid]?.block }
    }

    fun getIncomingReferences(blockUuid: String): List<Block> {
        return references.filter { it.toUuid == blockUuid }
            .mapNotNull { blocks[it.fromUuid]?.block }
    }

    fun getAllReferences(blockUuid: String): BlockReferences {
        return BlockReferences(
            outgoing = getOutgoingReferences(blockUuid),
            incoming = getIncomingReferences(blockUuid)
        )
    }

    fun getOrphanedBlocks(): List<Block> {
        val referencedUuids = references.flatMap { listOf(it.fromUuid, it.toUuid) }.toSet()
        return blocks.values.filter { !referencedUuids.contains(it.block.uuid) }
            .map { it.block }
    }

    fun getMostConnectedBlocks(limit: Int = 20): List<BlockWithReferenceCount> {
        val referenceCounts = mutableMapOf<String, Int>()

        references.forEach { ref ->
            referenceCounts[ref.fromUuid] = referenceCounts.getOrDefault(ref.fromUuid, 0) + 1
            referenceCounts[ref.toUuid] = referenceCounts.getOrDefault(ref.toUuid, 0) + 1
        }

        return referenceCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { (uuid, count) ->
                blocks[uuid]?.block?.let { BlockWithReferenceCount(it, count) }
            }
    }

    // ===== UTILITY METHODS =====

    fun clear() {
        blocks.clear()
        pages.clear()
        properties.clear()
        references.clear()
    }

    fun getStats(): DatabaseStats {
        return DatabaseStats(
            blockCount = blocks.size,
            pageCount = pages.size,
            propertyCount = properties.size,
            referenceCount = references.size
        )
    }
}

data class DatabaseStats(
    val blockCount: Int,
    val pageCount: Int,
    val propertyCount: Int,
    val referenceCount: Int
)

// ===== REPOSITORY IMPLEMENTATIONS =====

class InMemoryBlockRepository : BlockRepository {
    private val db = InMemoryGraphDatabase()

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> = flow {
        emit(success(db.getBlock(uuid)))
    }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> = flow {
        emit(success(db.getBlockChildren(blockUuid)))
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> = flow {
        emit(success(db.getBlockHierarchy(rootUuid)))
    }

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> = flow {
        emit(success(db.getBlockAncestors(blockUuid)))
    }

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> = flow {
        emit(success(db.getBlockParent(blockUuid)))
    }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> = flow {
        emit(success(db.getBlockSiblings(blockUuid)))
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        return try {
            db.saveBlock(block)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        return try {
            db.deleteBlock(blockUuid, deleteChildren)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int): Result<Unit> {
        return try {
            db.moveBlock(blockUuid, newParentUuid, newPosition)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }
}

class InMemoryPageRepository : PageRepository {
    private val db = InMemoryGraphDatabase()

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> = flow {
        emit(success(db.getPage(uuid)))
    }

    override fun getPageByName(name: String): Flow<Result<Page?>> = flow {
        emit(success(db.getPageByName(name)))
    }

    override fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>> = flow {
        emit(success(db.getPagesInNamespace(namespace)))
    }

    override fun getAllPages(): Flow<Result<List<Page>>> = flow {
        emit(success(db.getAllPages()))
    }

    override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> = flow {
        emit(success(db.getRecentPages(limit)))
    }

    override suspend fun savePage(page: Page): Result<Unit> {
        return try {
            db.savePage(page)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        return try {
            db.deletePage(pageUuid)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }
}

class InMemoryPropertyRepository : PropertyRepository {
    private val db = InMemoryGraphDatabase()

    override fun getPropertiesForBlock(blockUuid: String): Flow<Result<List<Property>>> = flow {
        emit(success(db.getPropertiesForBlock(blockUuid)))
    }

    override fun getProperty(blockUuid: String, key: String): Flow<Result<Property?>> = flow {
        emit(success(db.getProperty(blockUuid, key)))
    }

    override suspend fun saveProperty(property: Property): Result<Unit> {
        return try {
            db.saveProperty(property)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun deleteProperty(blockUuid: String, key: String): Result<Unit> {
        return try {
            db.deleteProperty(blockUuid, key)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> = flow {
        emit(success(db.getBlocksWithPropertyKey(key)))
    }

    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> = flow {
        emit(success(db.getBlocksWithPropertyValue(key, value)))
    }
}

class InMemoryReferenceRepository : ReferenceRepository {
    private val db = InMemoryGraphDatabase()

    override fun getOutgoingReferences(blockUuid: String): Flow<Result<List<Block>>> = flow {
        emit(success(db.getOutgoingReferences(blockUuid)))
    }

    override fun getIncomingReferences(blockUuid: String): Flow<Result<List<Block>>> = flow {
        emit(success(db.getIncomingReferences(blockUuid)))
    }

    override fun getAllReferences(blockUuid: String): Flow<Result<BlockReferences>> = flow {
        emit(success(db.getAllReferences(blockUuid)))
    }

    override suspend fun addReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> {
        return try {
            db.addReference(fromBlockUuid, toBlockUuid)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun removeReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> {
        return try {
            db.removeReference(fromBlockUuid, toBlockUuid)
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override fun getOrphanedBlocks(): Flow<Result<List<Block>>> = flow {
        emit(success(db.getOrphanedBlocks()))
    }

    override fun getMostConnectedBlocks(limit: Int): Flow<Result<List<BlockWithReferenceCount>>> = flow {
        emit(success(db.getMostConnectedBlocks(limit)))
    }
}

class InMemorySearchRepository : SearchRepository {
    private val db = InMemoryGraphDatabase()

    override fun searchBlocksByContent(query: String): Flow<Result<List<Block>>> = flow {
        val blocks = db.blocks.values.map { it.block }
            .filter { it.content.contains(query, ignoreCase = true) }
        emit(success(blocks))
    }

    override fun searchPagesByTitle(query: String): Flow<Result<List<Page>>> = flow {
        val pages = db.pages.values
            .filter { it.name.contains(query, ignoreCase = true) }
        emit(success(pages))
    }

    override fun findBlocksReferencing(query: String): Flow<Result<List<Block>>> = flow {
        // Simple implementation - in practice would need more sophisticated logic
        val blocks = db.blocks.values.map { it.block }
            .filter { block ->
                db.getOutgoingReferences(block.uuid)
                    .any { ref -> ref.content.contains(query, ignoreCase = true) }
            }
        emit(success(blocks))
    }

    override fun searchWithFilters(searchRequest: SearchRequest): Flow<Result<SearchResult>> = flow {
        val blocks = mutableListOf<Block>()
        val pages = mutableListOf<Page>()

        // Apply filters (simplified implementation)
        if (searchRequest.query != null) {
            blocks.addAll(db.blocks.values.map { it.block }
                .filter { it.content.contains(searchRequest.query!!, ignoreCase = true) })

            pages.addAll(db.pages.values
                .filter { it.name.contains(searchRequest.query!!, ignoreCase = true) })
        }

        // Apply other filters as needed...

        val result = SearchResult(
            blocks = blocks.take(searchRequest.limit),
            pages = pages.take(searchRequest.limit),
            totalCount = blocks.size + pages.size,
            hasMore = (blocks.size + pages.size) > searchRequest.limit
        )

        emit(success(result))
    }
}