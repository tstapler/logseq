package com.logseq.kmp.ui.fixtures

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.platform.FileSystem
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.BlockWithDepth
import com.logseq.kmp.repository.BlockWithReferenceCount
import com.logseq.kmp.repository.PageRepository
import com.logseq.kmp.repository.BlockVersion
import com.logseq.kmp.repository.BlockRepositoryStatistics
import com.logseq.kmp.repository.ValidationReport
import com.logseq.kmp.repository.CacheStatistics
import com.logseq.kmp.platform.EncryptionManager
import com.logseq.kmp.repository.PropertyRepository
import com.logseq.kmp.repository.ReferenceRepository
import com.logseq.kmp.repository.BlockReferences
import com.logseq.kmp.repository.SearchRepository
import com.logseq.kmp.repository.SearchRequest
import com.logseq.kmp.repository.SearchResult
import com.logseq.kmp.repository.BlockWithReferenceCount as BlockWithRefCount
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
    private val blocksByPage: Map<String, List<Block>> = emptyMap()
) : BlockRepository {

    private val _blocks = MutableStateFlow(blocksByPage.values.flatten().associateBy { it.uuid })

    override fun getBlocksForPage(pageUuid: String): Flow<Result<List<Block>>> =
        _blocks.asStateFlow().map { all -> Result.success(all.values.filter { it.pageUuid == pageUuid }.sortedBy { it.position }) }

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> =
        _blocks.asStateFlow().map { Result.success(it[uuid]) }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> =
        _blocks.asStateFlow().map { all ->
            Result.success(all.values.filter { it.parentUuid == blockUuid }.sortedBy { it.position })
        }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> =
        flowOf(Result.success(emptyList()))

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> =
        flowOf(Result.success(emptyList()))

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> =
        _blocks.asStateFlow().map { all ->
            val block = all[blockUuid]
            Result.success(block?.parentUuid?.let { all[it] })
        }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> =
        _blocks.asStateFlow().map { all ->
            val block = all[blockUuid]
            val siblings = all.values.filter { it.pageUuid == block?.pageUuid && it.parentUuid == block?.parentUuid && it.uuid != blockUuid }
            Result.success(siblings.sortedBy { it.position })
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

    override suspend fun deleteBlocksForPage(pageUuid: String): Result<Unit> {
        _blocks.value = _blocks.value.filterValues { it.pageUuid != pageUuid }
        return Result.success(Unit)
    }

    override suspend fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int): Result<Unit> {
        val current = _blocks.value
        val block = current[blockUuid] ?: return Result.success(Unit)
        _blocks.value = current + (blockUuid to block.copy(parentUuid = newParentUuid, position = newPosition))
        return Result.success(Unit)
    }

    override suspend fun indentBlock(blockUuid: String): Result<Unit> {
        val current = _blocks.value
        val block = current[blockUuid] ?: return Result.success(Unit)

        val siblings = current.values
            .filter { it.pageUuid == block.pageUuid && it.parentUuid == block.parentUuid }
            .sortedBy { it.position }

        val blockIndex = siblings.indexOfFirst { it.uuid == blockUuid }
        if (blockIndex <= 0) return Result.success(Unit) // No previous sibling

        val prevSibling = siblings[blockIndex - 1]
        val prevSiblingChildren = current.values.filter { it.parentUuid == prevSibling.uuid }
        val newPosition = if (prevSiblingChildren.isEmpty()) 0 else prevSiblingChildren.maxOf { it.position } + 1

        val updates = mutableMapOf<String, Block>()
        updates[block.uuid] = block.copy(parentUuid = prevSibling.uuid, level = block.level + 1, position = newPosition)
        adjustDescendantLevels(block.uuid, +1, current, updates)

        _blocks.value = current + updates
        return Result.success(Unit)
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> {
        val current = _blocks.value
        val block = current[blockUuid] ?: return Result.success(Unit)
        val parentUuid = block.parentUuid ?: return Result.success(Unit) // Already at root

        val parent = current[parentUuid] ?: return Result.success(Unit)
        val grandparentUuid = parent.parentUuid

        val grandparentChildren = current.values
            .filter { it.pageUuid == block.pageUuid && it.parentUuid == grandparentUuid }
            .sortedBy { it.position }

        val parentInGrandchildren = grandparentChildren.find { it.uuid == parentUuid }
        val newPosition = (parentInGrandchildren?.position ?: -1) + 1

        val updates = mutableMapOf<String, Block>()
        for (sibling in grandparentChildren) {
            if (sibling.position >= newPosition) {
                updates[sibling.uuid] = sibling.copy(position = sibling.position + 1)
            }
        }
        updates[block.uuid] = block.copy(parentUuid = grandparentUuid, level = parent.level, position = newPosition)
        adjustDescendantLevels(block.uuid, parent.level - block.level, current, updates)

        _blocks.value = current + updates
        return Result.success(Unit)
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> {
        val current = _blocks.value
        val block = current[blockUuid] ?: return Result.success(Unit)

        val siblings = current.values
            .filter { it.pageUuid == block.pageUuid && it.parentUuid == block.parentUuid }
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
            .filter { it.pageUuid == block.pageUuid && it.parentUuid == block.parentUuid }
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
            content = secondPart,
            position = block.position + 1
        )
        
        current[blockUuid] = updatedBlock
        current[newBlock.uuid] = newBlock
        _blocks.value = current
        return Result.success(newBlock)
    }

    private fun adjustDescendantLevels(
        parentUuid: String,
        delta: Int,
        snapshot: Map<String, Block>,
        updates: MutableMap<String, Block>
    ) {
        val children = snapshot.values.filter { it.parentUuid == parentUuid }
        for (child in children) {
            val base = updates[child.uuid] ?: child
            val newLevel = base.level + delta
            if (newLevel >= 0) {
                updates[child.uuid] = base.copy(level = newLevel)
                adjustDescendantLevels(child.uuid, delta, snapshot, updates)
            }
        }
    }

    override suspend fun clear() { _blocks.value = emptyMap() }
    
    // Unimplemented members
    override fun getBlocksForPageHierarchy(pageUuid: String): Flow<Result<List<BlockWithDepth>>> = flowOf(Result.success(emptyList()))
    override suspend fun createBlocks(blocks: List<Block>): Result<Unit> = Result.success(Unit)
    override suspend fun updateBlocks(blocks: List<Block>): Result<Unit> = Result.success(Unit)
    override suspend fun deleteBlocks(blockUuids: List<String>): Result<Unit> = Result.success(Unit)
    override fun getBlockMetadata(blockUuid: String): Flow<Result<Map<String, String>>> = flowOf(Result.success(emptyMap()))
    override suspend fun updateBlockMetadata(blockUuid: String, metadata: Map<String, String>): Result<Unit> = Result.success(Unit)
    override suspend fun deleteBlockMetadata(blockUuid: String, key: String): Result<Unit> = Result.success(Unit)
    override fun getBlockProperties(blockUuid: String): Flow<Result<List<com.logseq.kmp.model.Property>>> = flowOf(Result.success(emptyList()))
    override fun getBlockProperty(blockUuid: String, key: String): Flow<Result<com.logseq.kmp.model.Property?>> = flowOf(Result.success(null))
    override suspend fun saveBlockProperty(property: com.logseq.kmp.model.Property): Result<Unit> = Result.success(Unit)
    override suspend fun deleteBlockProperty(blockUuid: String, key: String): Result<Unit> = Result.success(Unit)
    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
    override fun getBlockVersionHistory(blockUuid: String): Flow<Result<List<BlockVersion>>> = flowOf(Result.success(emptyList()))
    override fun getBlockVersion(blockUuid: String, version: Long): Flow<Result<BlockVersion?>> = flowOf(Result.success(null))
    override suspend fun createBlockVersion(blockUuid: String, changeDescription: String): Result<Unit> = Result.success(Unit)
    override suspend fun getStatistics(): Result<BlockRepositoryStatistics> = Result.success(BlockRepositoryStatistics(0, 0, 0, 0f, 0, kotlinx.datetime.Clock.System.now(), 0))
    override suspend fun optimize(): Result<Unit> = Result.success(Unit)
    override suspend fun validateIntegrity(): Result<ValidationReport> = Result.success(ValidationReport(true, emptyList(), emptyList(), emptyList()))
    override suspend fun setCachingEnabled(enabled: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun clearCache(): Result<Unit> = Result.success(Unit)
    override suspend fun getCacheStatistics(): Result<CacheStatistics> = Result.success(CacheStatistics(0, 0, 0f, 0, 0, 0))
    override suspend fun setEncryptionManager(encryptionManager: EncryptionManager): Result<Unit> = Result.success(Unit)
    override fun isEncrypted(): Boolean = false
    override fun getBlocksByContentPattern(pattern: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
}

class PopulatedFakeBlockRepository : FakeBlockRepository(
    blocksByPage = buildMap {
        TestFixtures.sampleJournalData().forEach { (page, blocks) -> put(page.uuid, blocks) }
        put(TestFixtures.samplePage().uuid, TestFixtures.samplePageBlocks())
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

    override fun getPageByName(name: String): Flow<Result<Page?>> =
        _pages.asStateFlow().map { Result.success(it.values.find { p -> p.name.equals(name, ignoreCase = true) }) }

    override suspend fun savePage(page: Page): Result<Unit> {
        _pages.value = _pages.value + (page.uuid to page)
        return Result.success(Unit)
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        _pages.value = _pages.value - pageUuid
        return Result.success(Unit)
    }

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> {
        val current = _pages.value
        val page = current[pageUuid] ?: return Result.failure(Exception("Page not found"))
        _pages.value = current + (pageUuid to page.copy(name = newName))
        return Result.success(Unit)
    }
    override suspend fun toggleFavorite(pageUuid: String): Result<Unit> {
        val current = _pages.value
        val page = current[pageUuid] ?: return Result.failure(Exception("Page not found"))
        _pages.value = current + (pageUuid to page.copy(isFavorite = !page.isFavorite))
        return Result.success(Unit)
    }
    override fun countPages(): Flow<Result<Long>> = _pages.asStateFlow().map { Result.success(it.size.toLong()) }
    override suspend fun clear() { _pages.value = emptyMap() }
}

class PopulatedFakePageRepository : FakePageRepository(
    initialPages = TestFixtures.sampleJournalPages() + TestFixtures.samplePage()
)
