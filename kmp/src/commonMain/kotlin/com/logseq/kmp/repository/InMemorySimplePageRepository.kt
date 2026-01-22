package com.logseq.kmp.repository

import com.logseq.kmp.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemorySimplePageRepository : SimplePageRepository {
    private val pages = MutableStateFlow<Map<String, Page>>(emptyMap())

    override fun getAllPages(): Flow<Result<List<Page>>> {
        return pages.map { map ->
            Result.success(map.values.toList())
        }
    }

    override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> {
        return pages.map { map ->
            Result.success(map.values.sortedByDescending { it.updatedAt }.take(limit))
        }
    }

    override fun getFavoritePages(): Flow<Result<List<Page>>> {
        return pages.map { map ->
            Result.success(map.values.filter { it.isFavorite })
        }
    }

    override fun getJournalPages(limit: Int, offset: Int): Flow<Result<List<Page>>> {
        return pages.map { map ->
            val journals = map.values
                .filter { it.isJournal && it.journalDate != null }
                .sortedByDescending { it.journalDate }
                .drop(offset)
                .take(limit)
            Result.success(journals)
        }
    }

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> {
        return pages.map { map ->
            Result.success(map[uuid])
        }
    }

    override fun getPageByName(name: String): Flow<Result<Page?>> {
        return pages.map { map ->
            // Case-insensitive search by page name
            Result.success(map.values.find { it.name.equals(name, ignoreCase = true) })
        }
    }

    override suspend fun savePage(page: Page): Result<Unit> {
        val current = pages.value.toMutableMap()
        current[page.uuid] = page
        pages.value = current
        return Result.success(Unit)
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        current.remove(pageUuid)
        pages.value = current
        return Result.success(Unit)
    }

    override suspend fun toggleFavorite(pageUuid: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        val page = current[pageUuid] ?: return Result.failure(Exception("Page not found"))
        current[pageUuid] = page.copy(isFavorite = !page.isFavorite)
        pages.value = current
        return Result.success(Unit)
    }

    override suspend fun clear() {
        pages.value = emptyMap()
    }
}
