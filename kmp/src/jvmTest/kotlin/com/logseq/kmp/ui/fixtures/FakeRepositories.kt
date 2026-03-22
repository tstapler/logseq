package com.logseq.kmp.ui.fixtures

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.platform.FileSystem
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.BlockWithDepth
import com.logseq.kmp.repository.BlockWithReferenceCount
import com.logseq.kmp.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeFileSystem : FileSystem {
    override fun getDefaultGraphPath(): String = "/tmp/test-graph"
    override fun expandTilde(path: String): String = path
    override fun readFile(path: String): String? = ""
    override fun writeFile(path: String, content: String): Boolean = true
    override fun listFiles(path: String): List<String> = emptyList()
    override fun listDirectories(path: String): List<String> = emptyList()
    override fun fileExists(path: String): Boolean = true
    override fun directoryExists(path: String): Boolean = true
    override fun createDirectory(path: String): Boolean = true
    override fun deleteFile(path: String): Boolean = true
    override fun pickDirectory(): String? = null
    override fun getLastModifiedTime(path: String): Long? = null
}

open class FakeBlockRepository(
    private val blocksByPage: Map<Long, List<Block>> = emptyMap()
) : BlockRepository {

    private val _blocks = MutableStateFlow(blocksByPage.values.flatten().associateBy { it.uuid })

    override fun getBlocksForPage(pageId: Long): Flow<Result<List<Block>>> =
        _blocks.asStateFlow().map { all -> Result.success(all.values.filter { it.pageId == pageId }) }

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> =
        _blocks.asStateFlow().map { Result.success(it[uuid]) }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> =
        _blocks.asStateFlow().map { all ->
            val parent = all[blockUuid]
            Result.success(all.values.filter { it.parentId == parent?.id })
        }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> =
        flowOf(Result.success(emptyList()))

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> =
        flowOf(Result.success(emptyList()))

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> =
        flowOf(Result.success(null))

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> =
        _blocks.asStateFlow().map { all ->
            val block = all[blockUuid]
            val siblings = all.values.filter { it.pageId == block?.pageId && it.parentId == block?.parentId && it.uuid != blockUuid }
            Result.success(siblings)
        }

    override fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>> =
        flowOf(Result.success(emptyList()))

    override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> =
        flowOf(Result.success(emptyList()))

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> =
        _blocks.asStateFlow().map { all ->
            val matching = all.values.filter { it.content.contains(query, ignoreCase = true) }
            Result.success(matching.drop(offset).take(limit))
        }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        _blocks.value = _blocks.value + (block.uuid to block)
        return Result.success(Unit)
    }

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> {
        _blocks.value = _blocks.value + blocks.associateBy { it.uuid }
        return Result.success(Unit)
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        _blocks.value = _blocks.value - blockUuid
        return Result.success(Unit)
    }

    override suspend fun deleteBlocksForPage(pageId: Long): Result<Unit> {
        _blocks.value = _blocks.value.filterValues { it.pageId != pageId }
        return Result.success(Unit)
    }

    override suspend fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int): Result<Unit> =
        Result.success(Unit)

    override suspend fun indentBlock(blockUuid: String): Result<Unit> {
        val current = _blocks.value
        val block = current[blockUuid] ?: return Result.success(Unit)

        val siblings = current.values
            .filter { it.pageId == block.pageId && it.parentId == block.parentId }
            .sortedBy { it.position }

        val blockIndex = siblings.indexOfFirst { it.uuid == blockUuid }
        if (blockIndex <= 0) return Result.success(Unit) // No previous sibling

        val prevSibling = siblings[blockIndex - 1]
        val prevSiblingChildren = current.values.filter { it.parentId == prevSibling.id }
        val newPosition = if (prevSiblingChildren.isEmpty()) 0 else prevSiblingChildren.maxOf { it.position } + 1

        val updates = mutableMapOf<String, Block>()
        updates[block.uuid] = block.copy(parentId = prevSibling.id, level = block.level + 1, position = newPosition)
        adjustDescendantLevels(block.id, +1, current, updates)

        _blocks.value = current + updates
        return Result.success(Unit)
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> {
        val current = _blocks.value
        val block = current[blockUuid] ?: return Result.success(Unit)
        val parentId = block.parentId ?: return Result.success(Unit) // Already at root

        val parent = current.values.find { it.id == parentId } ?: return Result.success(Unit)
        val grandparentId = parent.parentId

        val grandparentChildren = current.values
            .filter { it.pageId == block.pageId && it.parentId == grandparentId }
            .sortedBy { it.position }

        val parentInGrandchildren = grandparentChildren.find { it.id == parentId }
        val newPosition = (parentInGrandchildren?.position ?: -1) + 1

        val updates = mutableMapOf<String, Block>()
        for (sibling in grandparentChildren) {
            if (sibling.position >= newPosition) {
                updates[sibling.uuid] = sibling.copy(position = sibling.position + 1)
            }
        }
        updates[block.uuid] = block.copy(parentId = grandparentId, level = parent.level, position = newPosition)
        adjustDescendantLevels(block.id, parent.level - block.level, current, updates)

        _blocks.value = current + updates
        return Result.success(Unit)
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> {
        val current = _blocks.value
        val block = current[blockUuid] ?: return Result.success(Unit)

        val siblings = current.values
            .filter { it.pageId == block.pageId && it.parentId == block.parentId }
            .sortedBy { it.position }

        val blockIndex = siblings.indexOfFirst { it.uuid == blockUuid }
        if (blockIndex <= 0) return Result.success(Unit)

        val prevSibling = siblings[blockIndex - 1]
        _blocks.value = current + mapOf(
            block.uuid to block.copy(position = prevSibling.position),
            prevSibling.uuid to prevSibling.copy(position = block.position)
        )
        return Result.success(Unit)
    }

    override suspend fun moveBlockDown(blockUuid: String): Result<Unit> {
        val current = _blocks.value
        val block = current[blockUuid] ?: return Result.success(Unit)

        val siblings = current.values
            .filter { it.pageId == block.pageId && it.parentId == block.parentId }
            .sortedBy { it.position }

        val blockIndex = siblings.indexOfFirst { it.uuid == blockUuid }
        if (blockIndex >= siblings.size - 1) return Result.success(Unit)

        val nextSibling = siblings[blockIndex + 1]
        _blocks.value = current + mapOf(
            block.uuid to block.copy(position = nextSibling.position),
            nextSibling.uuid to nextSibling.copy(position = block.position)
        )
        return Result.success(Unit)
    }

    override suspend fun mergeBlocks(blockUuid: String, nextBlockUuid: String, separator: String): Result<Unit> {
        val current = _blocks.value.toMutableMap()
        val blockA = current[blockUuid] ?: return Result.success(Unit)
        val blockB = current[nextBlockUuid] ?: return Result.success(Unit)
        
        current[blockUuid] = blockA.copy(content = blockA.content + separator + blockB.content)
        current.remove(nextBlockUuid)
        _blocks.value = current
        return Result.success(Unit)
    }

    override suspend fun splitBlock(blockUuid: String, cursorPosition: Int): Result<Block> {
        val current = _blocks.value.toMutableMap()
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
        _blocks.value = current
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

    override suspend fun clear() { _blocks.value = emptyMap() }
}

class PopulatedFakeBlockRepository : FakeBlockRepository(
    blocksByPage = buildMap {
        TestFixtures.sampleJournalData().forEach { (page, blocks) -> put(page.id, blocks) }
        put(TestFixtures.samplePage().id, TestFixtures.samplePageBlocks())
    }
)

open class FakePageRepository(
    initialPages: List<Page> = emptyList()
) : PageRepository {
    private val _pages = MutableStateFlow(initialPages.associateBy { it.uuid })

    override fun getAllPages(): Flow<Result<List<Page>>> =
        _pages.asStateFlow().map { Result.success(it.values.toList()) }

    override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> =
        _pages.asStateFlow().map { Result.success(it.values.take(limit)) }

    override fun getJournalPages(limit: Int, offset: Int): Flow<Result<List<Page>>> =
        _pages.asStateFlow().map { all ->
            val journals = all.values
                .filter { it.isJournal }
                .sortedByDescending { it.journalDate }
                .drop(offset)
                .take(limit)
            Result.success(journals)
        }

    override fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>> =
        _pages.asStateFlow().map { all -> Result.success(all.values.filter { it.namespace == namespace }) }

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> =
        _pages.asStateFlow().map { Result.success(it[uuid]) }

    override fun getPageById(id: Long): Flow<Result<Page?>> =
        _pages.asStateFlow().map { Result.success(it.values.find { p -> p.id == id }) }

    override fun getPageByName(name: String): Flow<Result<Page?>> =
        _pages.asStateFlow().map { Result.success(it.values.find { p -> p.name == name }) }

    override suspend fun savePage(page: Page): Result<Unit> {
        _pages.value = _pages.value + (page.uuid to page)
        return Result.success(Unit)
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        _pages.value = _pages.value - pageUuid
        return Result.success(Unit)
    }

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> = Result.success(Unit)
    override suspend fun toggleFavorite(pageUuid: String): Result<Unit> = Result.success(Unit)
    override fun countPages(): Flow<Result<Long>> = _pages.asStateFlow().map { Result.success(it.size.toLong()) }
    override suspend fun clear() { _pages.value = emptyMap() }
}

class PopulatedFakePageRepository : FakePageRepository(
    initialPages = TestFixtures.sampleJournalPages() + TestFixtures.samplePage()
)
