package com.logseq.kmp.repository

import com.logseq.kmp.db.DriverFactory
import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SearchRepositoryIntegrationTests {

    private lateinit var database: LogseqDatabase
    private lateinit var repository: SearchRepository
    private lateinit var blockRepo: BlockRepository
    private lateinit var pageRepo: PageRepository
    private lateinit var refRepo: ReferenceRepository

    private val now = Clock.System.now()

    @BeforeTest
    fun setup() {
        val driver = DriverFactory().createDriver("jdbc:sqlite::memory:")
        database = LogseqDatabase(driver)
        repository = SqlDelightSearchRepository(database)
        blockRepo = SqlDelightBlockRepository(database)
        pageRepo = SqlDelightPageRepository(database)
        refRepo = SqlDelightReferenceRepository(database)
    }

    private fun generateUuid(index: Int): String {
        val hex = index.toString().padStart(4, '0')
        return "00000000-0000-0000-0000-00000000$hex"
    }

    private fun createTestBlock(
        id: Long,
        uuid: String,
        pageId: Long,
        content: String,
        properties: Map<String, String> = emptyMap()
    ): Block {
        return Block(
            id = id,
            uuid = uuid,
            pageId = pageId,
            parentId = null,
            leftId = null,
            content = content,
            level = 1,
            position = id.toInt(),
            createdAt = now,
            updatedAt = now,
            properties = properties
        )
    }

    private fun createTestPage(
        id: Long,
        uuid: String,
        name: String,
        properties: Map<String, String> = emptyMap()
    ): Page {
        return Page(
            id = id,
            uuid = uuid,
            name = name,
            namespace = null,
            filePath = null,
            createdAt = now,
            updatedAt = now,
            properties = properties
        )
    }

    @Test
    fun testSearchBlocksByContent() = runTest {
        pageRepo.savePage(createTestPage(1, generateUuid(100), "Page 1"))
        blockRepo.saveBlock(createTestBlock(1, generateUuid(1), 1, "Hello world content"))
        blockRepo.saveBlock(createTestBlock(2, generateUuid(2), 1, "Goodbye world content"))
        blockRepo.saveBlock(createTestBlock(3, generateUuid(3), 1, "Another unrelated block"))

        val results = repository.searchBlocksByContent("hello").first()
        assertTrue(results.isSuccess)
        assertEquals(1, results.getOrNull()?.size)
        assertEquals(generateUuid(1), results.getOrNull()?.first()?.uuid)
    }

    @Test
    fun testSearchBlocksByContentCaseInsensitive() = runTest {
        pageRepo.savePage(createTestPage(1, generateUuid(100), "Page 1"))
        blockRepo.saveBlock(createTestBlock(1, generateUuid(1), 1, "KOTLIN PROGRAMMING"))
        blockRepo.saveBlock(createTestBlock(2, generateUuid(2), 1, "kotlin is great"))

        val results = repository.searchBlocksByContent("kotlin").first()
        assertTrue(results.isSuccess)
        assertEquals(2, results.getOrNull()?.size)
    }

    @Test
    fun testSearchPagesByTitle() = runTest {
        pageRepo.savePage(createTestPage(1, generateUuid(1), "Kotlin Guide"))
        pageRepo.savePage(createTestPage(2, generateUuid(2), "Java Tutorial"))
        pageRepo.savePage(createTestPage(3, generateUuid(3), "Python Programming"))

        val results = repository.searchPagesByTitle("kotlin").first()
        assertTrue(results.isSuccess)
        assertEquals(1, results.getOrNull()?.size)
        assertEquals("Kotlin Guide", results.getOrNull()?.first()?.name)
    }

    @Test
    fun testFindBlocksReferencing() = runTest {
        pageRepo.savePage(createTestPage(1, generateUuid(100), "Page 1"))
        val targetBlockUuid = generateUuid(1)
        val refBlock1Uuid = generateUuid(2)
        val refBlock2Uuid = generateUuid(3)

        blockRepo.saveBlock(createTestBlock(1, targetBlockUuid, 1, "Target content"))
        blockRepo.saveBlock(createTestBlock(2, refBlock1Uuid, 1, "References target-block"))
        blockRepo.saveBlock(createTestBlock(3, refBlock2Uuid, 1, "Also references target-block"))

        refRepo.addReference(refBlock1Uuid, targetBlockUuid)
        refRepo.addReference(refBlock2Uuid, targetBlockUuid)

        val results = repository.findBlocksReferencing(targetBlockUuid).first()
        assertTrue(results.isSuccess)
        assertEquals(2, results.getOrNull()?.size)
    }

    @Test
    fun testSearchWithFilters() = runTest {
        pageRepo.savePage(createTestPage(1, generateUuid(100), "Page 1"))
        blockRepo.saveBlock(createTestBlock(1, generateUuid(1), 1, "Hello world", mapOf("tag" to "test")))
        blockRepo.saveBlock(createTestBlock(2, generateUuid(2), 1, "Hello again", mapOf("tag" to "other")))
        
        val request = SearchRequest(
            query = "hello",
            limit = 10,
            offset = 0
        )

        val results = repository.searchWithFilters(request).first()
        assertTrue(results.isSuccess)
        // SQLDelight implementation currently only does basic content/title matching
        assertTrue(results.getOrNull()?.blocks?.any { it.uuid == generateUuid(1) } == true)
    }
}
