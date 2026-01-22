package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SearchRepositoryIntegrationTests {

    private val now = Clock.System.now()

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
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "Hello world content"))
        repository.saveBlockForTest(createTestBlock(2, generateUuid(2), 1, "Goodbye world content"))
        repository.saveBlockForTest(createTestBlock(3, generateUuid(3), 1, "Another unrelated block"))

        val results = repository.searchBlocksByContent("hello").first()
        assertTrue(results.isSuccess)
        assertEquals(1, results.getOrNull()?.size)
        assertEquals(generateUuid(1), results.getOrNull()?.first()?.uuid)
    }

    @Test
    fun testSearchBlocksByContentCaseInsensitive() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "KOTLIN PROGRAMMING"))
        repository.saveBlockForTest(createTestBlock(2, generateUuid(2), 1, "kotlin is great"))

        val results = repository.searchBlocksByContent("kotlin").first()
        assertTrue(results.isSuccess)
        assertEquals(2, results.getOrNull()?.size)
    }

    @Test
    fun testSearchBlocksByContentEmptyQuery() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "Some content"))

        val results = repository.searchBlocksByContent("").first()
        assertTrue(results.isSuccess)
        assertTrue(results.getOrNull()?.isEmpty() ?: false)
    }

    @Test
    fun testSearchBlocksByContentNoResults() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "Some content"))

        val results = repository.searchBlocksByContent("nonexistent").first()
        assertTrue(results.isSuccess)
        assertTrue(results.getOrNull()?.isEmpty() ?: false)
    }

    @Test
    fun testSearchBlocksByContentPagination() = runTest {
        val repository = InMemorySearchRepository()

        for (i in 1..10) {
            repository.saveBlockForTest(createTestBlock(i.toLong(), generateUuid(i), 1, "Searchable content $i"))
        }

        val results = repository.searchBlocksByContent("searchable", limit = 5, offset = 0).first()
        assertTrue(results.isSuccess)
        assertEquals(5, results.getOrNull()?.size)

        val offsetResults = repository.searchBlocksByContent("searchable", limit = 5, offset = 5).first()
        assertTrue(offsetResults.isSuccess)
        assertEquals(5, offsetResults.getOrNull()?.size)
    }

    @Test
    fun testSearchPagesByTitle() = runTest {
        val repository = InMemorySearchRepository()

        repository.savePageForTest(createTestPage(1, generateUuid(1), "Kotlin Guide"))
        repository.savePageForTest(createTestPage(2, generateUuid(2), "Java Tutorial"))
        repository.savePageForTest(createTestPage(3, generateUuid(3), "Python Programming"))

        val results = repository.searchPagesByTitle("kotlin").first()
        assertTrue(results.isSuccess)
        assertEquals(1, results.getOrNull()?.size)
        assertEquals("Kotlin Guide", results.getOrNull()?.first()?.name)
    }

    @Test
    fun testSearchPagesByTitleNoResults() = runTest {
        val repository = InMemorySearchRepository()

        repository.savePageForTest(createTestPage(1, generateUuid(1), "Some Page"))

        val results = repository.searchPagesByTitle("nonexistent").first()
        assertTrue(results.isSuccess)
        assertTrue(results.getOrNull()?.isEmpty() ?: false)
    }

    @Test
    fun testSearchPagesByTitlePagination() = runTest {
        val repository = InMemorySearchRepository()

        for (i in 1..10) {
            repository.savePageForTest(createTestPage(i.toLong(), generateUuid(i), "Page $i"))
        }

        val results = repository.searchPagesByTitle("page", limit = 3).first()
        assertTrue(results.isSuccess)
        assertEquals(3, results.getOrNull()?.size)
    }

    @Test
    fun testFindBlocksReferencing() = runTest {
        val repository = InMemorySearchRepository()

        val targetBlockUuid = generateUuid(1)
        val refBlock1Uuid = generateUuid(2)
        val refBlock2Uuid = generateUuid(3)

        repository.saveBlockForTest(createTestBlock(1, targetBlockUuid, 1, "Target content"))
        repository.saveBlockForTest(createTestBlock(2, refBlock1Uuid, 1, "References target-block"))
        repository.saveBlockForTest(createTestBlock(3, refBlock2Uuid, 1, "Also references target-block"))
        repository.saveBlockForTest(createTestBlock(4, generateUuid(4), 1, "No references"))

        repository.addReferenceForTest(refBlock1Uuid, targetBlockUuid)
        repository.addReferenceForTest(refBlock2Uuid, targetBlockUuid)

        val results = repository.findBlocksReferencing(targetBlockUuid).first()
        assertTrue(results.isSuccess)
        assertEquals(2, results.getOrNull()?.size)
    }

    @Test
    fun testSearchWithFilters() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "Hello world", mapOf("tag" to "test")))
        repository.saveBlockForTest(createTestBlock(2, generateUuid(2), 1, "Hello again", mapOf("tag" to "other")))
        repository.saveBlockForTest(createTestBlock(3, generateUuid(3), 1, "Goodbye", mapOf("tag" to "test")))

        val request = SearchRequest(
            query = "hello",
            propertyFilters = mapOf("tag" to "test"),
            limit = 10,
            offset = 0
        )

        val results = repository.searchWithFilters(request).first()
        assertTrue(results.isSuccess)
        assertEquals(1, results.getOrNull()?.blocks?.size)
        assertEquals(generateUuid(1), results.getOrNull()?.blocks?.first()?.uuid)
    }

    @Test
    fun testSearchWithEmptyRequest() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "Content"))
        repository.savePageForTest(createTestPage(1, generateUuid(1), "Page"))

        val request = SearchRequest(
            query = null,
            limit = 10,
            offset = 0
        )

        val results = repository.searchWithFilters(request).first()
        assertTrue(results.isSuccess)
    }

    @Test
    fun testHighlightGeneration() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "This is a test content"))

        val results = repository.searchBlocksByContent("test").first()
        assertTrue(results.isSuccess)
        val content = results.getOrNull()?.first()?.content
        assertTrue(content?.contains("<em>test</em>") == true)
    }

    @Test
    fun testRelevanceScoring() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "kotlin programming"))
        repository.saveBlockForTest(createTestBlock(2, generateUuid(2), 1, "I love kotlin"))
        repository.saveBlockForTest(createTestBlock(3, generateUuid(3), 1, "Something about programming"))

        val results = repository.searchBlocksByContent("kotlin").first()
        assertTrue(results.isSuccess)

        val resultList = results.getOrNull()!!
        assertEquals(generateUuid(1), resultList[0].uuid)
    }

    @Test
    fun testClearForTest() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "Content"))
        repository.savePageForTest(createTestPage(1, generateUuid(1), "Page"))
        repository.addReferenceForTest(generateUuid(1), generateUuid(1))

        assertEquals(1, repository.searchBlocksByContent("content").first().getOrNull()?.size)

        repository.clearForTest()

        assertEquals(0, repository.searchBlocksByContent("content").first().getOrNull()?.size)
        assertEquals(0, repository.searchPagesByTitle("page").first().getOrNull()?.size)
    }

    @Test
    fun testPropertySearch() = runTest {
        val repository = InMemorySearchRepository()

        repository.saveBlockForTest(createTestBlock(1, generateUuid(1), 1, "Content without tag", mapOf("task" to "important")))
        repository.saveBlockForTest(createTestBlock(2, generateUuid(2), 1, "Content without tag", mapOf("task" to "urgent")))

        val results = repository.searchBlocksByContent("important").first()
        assertTrue(results.isSuccess)
        assertEquals(1, results.getOrNull()?.size)
        assertEquals(generateUuid(1), results.getOrNull()?.first()?.uuid)
    }
}
