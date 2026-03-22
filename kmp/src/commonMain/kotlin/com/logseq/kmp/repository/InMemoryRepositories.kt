package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlin.Result.Companion.success

/**
 * In-memory implementation of BlockRepository for testing purposes.
 */
class InMemoryBlockRepository : BlockRepository {

    private val blocks = MutableStateFlow<Map<String, Block>>(emptyMap())

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> {
        return blocks.map { map ->
            success(map[uuid])
        }
    }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val parent = map[blockUuid]
            if (parent == null) {
                success(emptyList())
            } else {
                success(map.values.filter { it.parentId == parent.id }.sortedBy { it.position })
            }
        }
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> {
        return blocks.map { map ->
            val result = mutableListOf<BlockWithDepth>()
            collectHierarchy(map, rootUuid, 0, result)
            success(result)
        }
    }

    private fun collectHierarchy(
        allBlocks: Map<String, Block>,
        uuid: String,
        depth: Int,
        result: MutableList<BlockWithDepth>
    ) {
        val block = allBlocks[uuid] ?: return
        result.add(BlockWithDepth(block, depth))
        val children = allBlocks.values.filter { it.parentId == block.id }.sortedBy { it.position }
        children.forEach { child ->
            collectHierarchy(allBlocks, child.uuid, depth + 1, result)
        }
    }

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val ancestors = mutableListOf<Block>()
            var currentUuid: String? = blockUuid
            while (currentUuid != null) {
                val block = map[currentUuid] ?: break
                if (block.parentId != null) {
                    val parent = map.values.find { it.id == block.parentId }
                    if (parent != null) {
                        ancestors.add(parent)
                        currentUuid = parent.uuid
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            success(ancestors.reversed())
        }
    }

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> {
        return blocks.map { map ->
            val block = map[blockUuid] ?: return@map success(null)
            val parent = if (block.parentId != null) {
                map.values.find { it.id == block.parentId }
            } else null
            success(parent)
        }
    }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val block = map[blockUuid] ?: return@map success(emptyList())
            val siblings = if (block.parentId != null) {
                map.values.filter { it.parentId == block.parentId && it.uuid != blockUuid }
            } else {
                map.values.filter { it.parentId == null && it.uuid != blockUuid }
            }
            success(siblings.sortedBy { it.position })
        }
    }

    override fun getBlocksForPage(pageId: Long): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val pageBlocks = map.values.filter { it.pageId == pageId }.sortedBy { it.position }
            success(pageBlocks)
        }
    }

    override suspend fun deleteBlocksForPage(pageId: Long): Result<Unit> {
        val current = this.blocks.value.toMutableMap()
        val toRemove = current.values.filter { it.pageId == pageId }.map { it.uuid }
        toRemove.forEach { current.remove(it) }
        this.blocks.value = current
        return success(Unit)
    }

    override suspend fun clear() {
        blocks.value = emptyMap()
    }

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> {
        val current = this.blocks.value.toMutableMap()
        blocks.forEach { current[it.uuid] = it }
        this.blocks.value = current
        return success(Unit)
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        val current = blocks.value.toMutableMap()
        current[block.uuid] = block
        blocks.value = current
        return success(Unit)
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        val current = blocks.value.toMutableMap()
        val block = current[blockUuid] ?: return success(Unit)

        if (deleteChildren) {
            val uuidsToDelete = mutableListOf(blockUuid)
            var index = 0
            while (index < uuidsToDelete.size) {
                val currentUuid = uuidsToDelete[index]
                val childBlocks = current.values.filter { it.parentId == current[currentUuid]?.id }
                childBlocks.forEach { child ->
                    uuidsToDelete.add(child.uuid)
                }
                index++
            }
            uuidsToDelete.forEach { current.remove(it) }
        } else {
            current.remove(blockUuid)
        }
        blocks.value = current
        return success(Unit)
    }

    override suspend fun moveBlock(
        blockUuid: String,
        newParentUuid: String?,
        newPosition: Int
    ): Result<Unit> {
        val current = blocks.value.toMutableMap()
        val block = current[blockUuid] ?: return success(Unit)

        val updatedBlock = block.copy(
            parentId = newParentUuid?.let { uuid ->
                current[uuid]?.id
            },
            position = newPosition
        )
        current[blockUuid] = updatedBlock
        blocks.value = current
        return success(Unit)
    }

    override suspend fun indentBlock(blockUuid: String): Result<Unit> {
        val current = blocks.value
        val block = current[blockUuid] ?: return success(Unit)

        val siblings = current.values
            .filter { it.pageId == block.pageId && it.parentId == block.parentId }
            .sortedBy { it.position }

        val blockIndex = siblings.indexOfFirst { it.uuid == blockUuid }
        if (blockIndex <= 0) return success(Unit) // No previous sibling

        val prevSibling = siblings[blockIndex - 1]
        val prevSiblingChildren = current.values.filter { it.parentId == prevSibling.id }
        val newPosition = if (prevSiblingChildren.isEmpty()) 0 else (prevSiblingChildren.maxOfOrNull { it.position } ?: -1) + 1

        val updates = mutableMapOf<String, Block>()
        updates[block.uuid] = block.copy(parentId = prevSibling.id, level = block.level + 1, position = newPosition)
        adjustDescendantLevels(block.id, +1, current, updates)

        blocks.value = current + updates
        return success(Unit)
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> {
        val current = blocks.value
        val block = current[blockUuid] ?: return success(Unit)
        val parentId = block.parentId ?: return success(Unit) // Already at root

        val parent = current.values.find { it.id == parentId } ?: return success(Unit)
        val grandparentId = parent.parentId

        val grandparentChildren = current.values
            .filter { it.pageId == block.pageId && it.parentId == grandparentId }
            .sortedBy { it.position }

        val parentInGrandchildren = grandparentChildren.find { it.id == parentId }
        val newPosition = (parentInGrandchildren?.position ?: -1) + 1

        val updates = mutableMapOf<String, Block>()
        // Shift grandparent's children at or after newPosition to make room
        for (sibling in grandparentChildren) {
            if (sibling.position >= newPosition) {
                updates[sibling.uuid] = sibling.copy(position = sibling.position + 1)
            }
        }
        updates[block.uuid] = block.copy(parentId = grandparentId, level = parent.level, position = newPosition)
        adjustDescendantLevels(block.id, parent.level - block.level, current, updates)

        blocks.value = current + updates
        return success(Unit)
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> {
        val current = blocks.value
        val block = current[blockUuid] ?: return success(Unit)

        val siblings = current.values
            .filter { it.pageId == block.pageId && it.parentId == block.parentId }
            .sortedBy { it.position }

        val blockIndex = siblings.indexOfFirst { it.uuid == blockUuid }
        if (blockIndex <= 0) return success(Unit)

        val prevSibling = siblings[blockIndex - 1]
        blocks.value = current + mapOf(
            block.uuid to block.copy(position = prevSibling.position),
            prevSibling.uuid to prevSibling.copy(position = block.position)
        )
        return success(Unit)
    }

    override suspend fun moveBlockDown(blockUuid: String): Result<Unit> {
        val current = blocks.value
        val block = current[blockUuid] ?: return success(Unit)

        val siblings = current.values
            .filter { it.pageId == block.pageId && it.parentId == block.parentId }
            .sortedBy { it.position }

        val blockIndex = siblings.indexOfFirst { it.uuid == blockUuid }
        if (blockIndex >= siblings.size - 1) return success(Unit)

        val nextSibling = siblings[blockIndex + 1]
        blocks.value = current + mapOf(
            block.uuid to block.copy(position = nextSibling.position),
            nextSibling.uuid to nextSibling.copy(position = block.position)
        )
        return success(Unit)
    }

    override suspend fun mergeBlocks(blockUuid: String, nextBlockUuid: String, separator: String): Result<Unit> {
        val current = blocks.value.toMutableMap()
        val blockA = current[blockUuid] ?: return success(Unit)
        val blockB = current[nextBlockUuid] ?: return success(Unit)
        
        current[blockUuid] = blockA.copy(content = blockA.content + separator + blockB.content)
        current.remove(nextBlockUuid)
        blocks.value = current
        return success(Unit)
    }

    override suspend fun splitBlock(blockUuid: String, cursorPosition: Int): Result<Block> {
        val current = blocks.value.toMutableMap()
        val block = current[blockUuid] ?: return Result.failure(Exception("Block not found"))
        
        val firstPart = block.content.substring(0, cursorPosition).trim()
        val secondPart = block.content.substring(cursorPosition).trim()
        
        val updatedBlock = block.copy(content = firstPart)
        val newBlock = block.copy(
            uuid = java.util.UUID.randomUUID().toString(),
            id = (current.values.maxOfOrNull { it.id } ?: 0L) + 1,
            content = secondPart,
            position = block.position + 1
        )
        
        current[blockUuid] = updatedBlock
        current[newBlock.uuid] = newBlock
        blocks.value = current
        return Result.success(newBlock)
    }

    private fun adjustDescendantLevels(
        parentId: Long,
        delta: Int,
        snapshot: Map<String, Block>,
        updates: MutableMap<String, Block>
    ) {
        val children = snapshot.values.filter { it.parentId == parentId }
        for (child in children) {
            val base = updates[child.uuid] ?: child
            val newLevel = base.level + delta
            if (newLevel >= 0) {
                updates[child.uuid] = base.copy(level = newLevel)
                adjustDescendantLevels(child.id, delta, snapshot, updates)
            }
        }
    }

    override fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
        return blocks.map { map ->
            val linkedBlocks = map.values.filter { block ->
                wikiLinkPattern.containsMatchIn(block.content)
            }
            success(linkedBlocks.sortedBy { it.pageId })
        }
    }

    override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
        val plainTextPattern = "\\b${Regex.escape(pageName)}\\b".toRegex(RegexOption.IGNORE_CASE)
        return blocks.map { map ->
            val unlinkedBlocks = map.values.filter { block ->
                plainTextPattern.containsMatchIn(block.content) &&
                    !wikiLinkPattern.containsMatchIn(block.content)
            }
            success(unlinkedBlocks.sortedBy { it.pageId })
        }
    }

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val matchingBlocks = map.values.filter { block ->
                block.content.contains(query, ignoreCase = true)
            }
            success(matchingBlocks.sortedBy { it.pageId }.drop(offset).take(limit))
        }
    }
}

class InMemoryPageRepository : PageRepository {
    private val pages = MutableStateFlow<Map<String, Page>>(emptyMap())

    override fun getAllPages(): Flow<Result<List<Page>>> {
        return pages.map { map ->
            success(map.values.toList())
        }
    }

    override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> {
        return pages.map { map ->
            success(map.values.sortedByDescending { it.updatedAt }.take(limit))
        }
    }

    override fun getJournalPages(limit: Int, offset: Int): Flow<Result<List<Page>>> {
        return pages.map { map ->
            val journals = map.values
                .filter { it.isJournal && it.journalDate != null }
                .sortedByDescending { it.journalDate }
                .drop(offset)
                .take(limit)
            success(journals)
        }
    }

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> {
        return pages.map { map ->
            success(map[uuid])
        }
    }

    override fun getPageById(id: Long): Flow<Result<Page?>> {
        return pages.map { map ->
            success(map.values.find { it.id == id })
        }
    }

    override fun getPageByName(name: String): Flow<Result<Page?>> {
        return pages.map { map ->
            val page = map.values.find { page ->
                page.name.equals(name, ignoreCase = true) ||
                    page.properties["alias"]?.split(",")?.any { it.trim().equals(name, ignoreCase = true) } == true
            }
            success(page)
        }
    }

    override fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>> {
        return pages.map { map ->
            success(map.values.filter { it.namespace == namespace })
        }
    }

    override suspend fun savePage(page: Page): Result<Long> {
        val current = pages.value.toMutableMap()
        current[page.uuid] = page
        pages.value = current
        return success(page.id)
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        current.remove(pageUuid)
        pages.value = current
        return success(Unit)
    }

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        val page = current[pageUuid] ?: return Result.failure(Exception("Page not found"))
        current[pageUuid] = page.copy(name = newName, updatedAt = kotlinx.datetime.Clock.System.now())
        pages.value = current
        return success(Unit)
    }

    override suspend fun toggleFavorite(pageUuid: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        val page = current[pageUuid] ?: return Result.failure(Exception("Page not found"))
        current[pageUuid] = page.copy(isFavorite = !page.isFavorite)
        pages.value = current
        return success(Unit)
    }

    override fun countPages(): Flow<Result<Long>> {
        return pages.map { success(it.size.toLong()) }
    }

    override suspend fun clear() {
        pages.value = emptyMap()
    }
}

class InMemoryPropertyRepository : PropertyRepository {
    override fun getPropertiesForBlock(blockUuid: String): Flow<Result<List<Property>>> = flowOf(success(emptyList()))
    override fun getProperty(blockUuid: String, key: String): Flow<Result<Property?>> = flowOf(success(null))
    override suspend fun saveProperty(property: Property): Result<Unit> = success(Unit)
    override suspend fun deleteProperty(blockUuid: String, key: String): Result<Unit> = success(Unit)
    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> = flowOf(success(emptyList()))
    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> = flowOf(success(emptyList()))
}

class InMemoryReferenceRepository : ReferenceRepository {
    override fun getOutgoingReferences(blockUuid: String): Flow<Result<List<Block>>> = flowOf(success(emptyList()))
    override fun getIncomingReferences(blockUuid: String): Flow<Result<List<Block>>> = flowOf(success(emptyList()))
    override fun getAllReferences(blockUuid: String): Flow<Result<BlockReferences>> = flowOf(success(BlockReferences(emptyList(), emptyList())))
    override suspend fun addReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> = success(Unit)
    override suspend fun removeReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> = success(Unit)
    override fun getOrphanedBlocks(): Flow<Result<List<Block>>> = flowOf(success(emptyList()))
    override fun getMostConnectedBlocks(limit: Int): Flow<Result<List<BlockWithReferenceCount>>> = flowOf(success(emptyList()))
}

class InMemorySearchRepository(
    private val pageRepository: PageRepository? = null,
    private val blockRepository: BlockRepository? = null
) : SearchRepository {
    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> {
        if (blockRepository == null || query.isEmpty()) return flowOf(success(emptyList()))
        return blockRepository.searchBlocksByContent(query, limit, offset)
    }

    override fun searchPagesByTitle(query: String, limit: Int): Flow<Result<List<Page>>> {
        if (pageRepository == null || query.isEmpty()) return flowOf(success(emptyList()))
        return pageRepository.getAllPages().map { res ->
            res.map { pages ->
                pages.filter { it.name.contains(query, ignoreCase = true) || it.properties["alias"]?.contains(query, ignoreCase = true) == true }
                    .take(limit)
            }
        }
    }

    override fun findBlocksReferencing(blockUuid: String): Flow<Result<List<Block>>> = flowOf(success(emptyList()))
    
    override fun searchWithFilters(searchRequest: SearchRequest): Flow<Result<SearchResult>> {
        if (searchRequest.query == null) return flowOf(success(SearchResult(emptyList(), emptyList(), 0, false)))
        
        return searchPagesByTitle(searchRequest.query!!, searchRequest.limit).map { pagesRes ->
            val pages = pagesRes.getOrNull() ?: emptyList()
            // We'll just return pages for now in this fake search
            success(SearchResult(emptyList(), pages, pages.size, false))
        }
    }
}
