package com.logseq.kmp.ui.screens

import com.logseq.kmp.db.GraphLoader
import com.logseq.kmp.platform.FileSystem
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.*
import com.logseq.kmp.platform.EncryptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalsViewModelTest {

    class FakeFileSystem : FileSystem {
        override fun getDefaultGraphPath(): String = "/tmp/graph"
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

    class FakeBlockRepository : BlockRepository {
        override fun getBlocksForPage(pageUuid: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
        override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> = flowOf(Result.success(null))
        override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
        override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> = flowOf(Result.success(emptyList()))
        override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
        override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> = flowOf(Result.success(null))
        override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
        override fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
        override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
        override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
        override fun getBlocksByContentPattern(pattern: String): Flow<Result<List<Block>>> = flowOf(Result.success(emptyList()))
        override fun getBlocksForPageHierarchy(pageUuid: String): Flow<Result<List<BlockWithDepth>>> = flowOf(Result.success(emptyList()))
        
        override suspend fun saveBlock(block: Block): Result<Unit> = Result.success(Unit)
        override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> = Result.success(Unit)
        override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun deleteBlocksForPage(pageUuid: String): Result<Unit> = Result.success(Unit)
        override suspend fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int): Result<Unit> = Result.success(Unit)
        override suspend fun indentBlock(blockUuid: String): Result<Unit> = Result.success(Unit)
        override suspend fun outdentBlock(blockUuid: String): Result<Unit> = Result.success(Unit)
        override suspend fun moveBlockUp(blockUuid: String): Result<Unit> = Result.success(Unit)
        override suspend fun moveBlockDown(blockUuid: String): Result<Unit> = Result.success(Unit)
        override suspend fun mergeBlocks(blockUuid: String, nextBlockUuid: String, separator: String): Result<Unit> = Result.success(Unit)
        override suspend fun splitBlock(blockUuid: String, cursorPosition: Int): Result<Block> = Result.failure(NotImplementedError())
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
        override suspend fun getStatistics(): Result<BlockRepositoryStatistics> = Result.success(BlockRepositoryStatistics(0, 0, 0, 0f, 0, Clock.System.now(), 0))
        override suspend fun optimize(): Result<Unit> = Result.success(Unit)
        override suspend fun validateIntegrity(): Result<ValidationReport> = Result.success(ValidationReport(true, emptyList(), emptyList(), emptyList()))
        override suspend fun setCachingEnabled(enabled: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun clearCache(): Result<Unit> = Result.success(Unit)
        override suspend fun getCacheStatistics(): Result<CacheStatistics> = Result.success(CacheStatistics(0, 0, 0f, 0, 0, 0))
        override suspend fun setEncryptionManager(encryptionManager: EncryptionManager): Result<Unit> = Result.success(Unit)
        override fun isEncrypted(): Boolean = false
        override suspend fun clear() {}
    }

    class FakePageRepository : PageRepository {
        val pages = mutableListOf<Page>()

        override fun getAllPages(): Flow<Result<List<Page>>> = flowOf(Result.success(pages))
        override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> = flowOf(Result.success(emptyList()))
        
        override fun getJournalPages(limit: Int, offset: Int): Flow<Result<List<Page>>> {
            val journals = pages
                .filter { it.isJournal }
                .sortedByDescending { it.journalDate }
                .drop(offset)
                .take(limit)
            return flowOf(Result.success(journals))
        }

        override fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>> = flowOf(Result.success(emptyList()))
        override fun getPageByUuid(uuid: String): Flow<Result<Page?>> = flowOf(Result.success(pages.find { it.uuid == uuid }))
        override fun getPageByName(name: String): Flow<Result<Page?>> = flowOf(Result.success(pages.find { it.name.equals(name, ignoreCase = true) }))
        override suspend fun savePage(page: Page): Result<Unit> {
            pages.add(page)
            return Result.success(Unit)
        }
        override suspend fun deletePage(pageUuid: String): Result<Unit> = Result.success(Unit)
        override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleFavorite(pageUuid: String): Result<Unit> = Result.success(Unit)
        override fun countPages(): Flow<Result<Long>> = flowOf(Result.success(pages.size.toLong()))
        override suspend fun clear() { pages.clear() }
    }

    private fun generateFakeUuid(index: Int): String {
        val hex = index.toString(16).padStart(12, '0')
        return "00000000-0000-0000-0000-$hex"
    }

    @Test
    fun testLoadMore() = runTest {
        val repo = FakePageRepository()
        // Create 15 journal pages
        for (i in 1..15) {
            val date = LocalDate(2026, 1, i)
            repo.savePage(
                Page(
                    uuid = generateFakeUuid(i),
                    name = "2026-01-${i.toString().padStart(2, '0')}",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    isJournal = true,
                    journalDate = date
                )
            )
        }

        val blockRepo = FakeBlockRepository()
        val fileSystem = FakeFileSystem()
        val graphLoader = GraphLoader(fileSystem, repo, blockRepo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = JournalsViewModel(repo, blockRepo, graphLoader, scope)
        
        // Initial load (10 pages)
        assertEquals(10, viewModel.uiState.value.pages.size)
        
        // Load more (remaining 5 pages)
        viewModel.loadMore()
        assertEquals(15, viewModel.uiState.value.pages.size)
        
        // Load more again (no more pages)
        viewModel.loadMore()
        assertEquals(15, viewModel.uiState.value.pages.size)
    }
}
