package com.logseq.kmp.ui.screens

import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.SimplePageRepository
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

    class FakePageRepository : SimplePageRepository {
        val pages = mutableListOf<Page>()

        override fun getAllPages(): Flow<Result<List<Page>>> = flowOf(Result.success(pages))
        override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> = flowOf(Result.success(emptyList()))
        override fun getFavoritePages(): Flow<Result<List<Page>>> = flowOf(Result.success(emptyList()))
        
        override fun getJournalPages(limit: Int, offset: Int): Flow<Result<List<Page>>> {
            val journals = pages
                .filter { it.isJournal }
                .sortedByDescending { it.journalDate }
                .drop(offset)
                .take(limit)
            return flowOf(Result.success(journals))
        }

        override fun getPageByUuid(uuid: String): Flow<Result<Page?>> = flowOf(Result.success(null))
        override fun getPageByName(name: String): Flow<Result<Page?>> = flowOf(Result.success(null))
        override suspend fun savePage(page: Page): Result<Unit> {
            pages.add(page)
            return Result.success(Unit)
        }
        override suspend fun deletePage(pageUuid: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleFavorite(pageUuid: String): Result<Unit> = Result.success(Unit)
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
                    id = i.toLong(),
                    uuid = generateFakeUuid(i),
                    name = "2026-01-${i.toString().padStart(2, '0')}",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    isJournal = true,
                    journalDate = date
                )
            )
        }

        val viewModel = JournalsViewModel(repo, CoroutineScope(Dispatchers.Unconfined))
        
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
