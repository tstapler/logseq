package com.logseq.kmp.repository

import com.logseq.kmp.model.Page
import com.logseq.kmp.ui.LogseqViewModel
import com.logseq.kmp.ui.screens.SearchResultItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AliasSupportTest {
    private lateinit var pageRepository: InMemorySimplePageRepository
    private lateinit var searchRepository: InMemorySearchRepository
    
    @BeforeTest
    fun setup() {
        pageRepository = InMemorySimplePageRepository()
        searchRepository = InMemorySearchRepository(pageRepository, null)
    }
    
    @Test
    fun `test resolve page by alias`() = runTest {
        val now = Clock.System.now()
        val page = Page(
            id = 1L,
            uuid = "00000000-0000-0000-0000-000000000001",
            name = "Logseq",
            createdAt = now,
            updatedAt = now,
            properties = mapOf("alias" to "KMP, Desktop")
        )
        pageRepository.savePage(page)
        
        // Resolve by primary name
        val match1 = pageRepository.getPageByName("Logseq").first().getOrNull()
        assertNotNull(match1)
        assertEquals("00000000-0000-0000-0000-000000000001", match1.uuid)
        
        // Resolve by first alias
        val match2 = pageRepository.getPageByName("KMP").first().getOrNull()
        assertNotNull(match2)
        assertEquals("00000000-0000-0000-0000-000000000001", match2.uuid)
        
        // Resolve by second alias (case insensitive)
        val match3 = pageRepository.getPageByName("desktop").first().getOrNull()
        assertNotNull(match3)
        assertEquals("00000000-0000-0000-0000-000000000001", match3.uuid)
    }
    
    @Test
    fun `test search pages by alias`() = runTest {
        val now = Clock.System.now()
        val page = Page(
            id = 1L,
            uuid = "00000000-0000-0000-0000-000000000001",
            name = "Logseq",
            createdAt = now,
            updatedAt = now,
            properties = mapOf("alias" to "KMP, Desktop")
        )
        pageRepository.savePage(page)
        
        // Search by alias
        val results = searchRepository.searchPagesByTitle("KMP", 10).first().getOrNull()
        assertNotNull(results)
        assertTrue(results.any { it.uuid == "00000000-0000-0000-0000-000000000001" })
    }
}
