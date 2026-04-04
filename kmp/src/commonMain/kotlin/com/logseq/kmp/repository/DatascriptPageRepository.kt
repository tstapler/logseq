package com.logseq.kmp.repository

import com.logseq.kmp.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.Result.Companion.success

/**
 * Datalog-style in-memory repository for pages.
 */
class DatascriptPageRepository : PageRepository {

    private val pages = MutableStateFlow<Map<String, Page>>(emptyMap())

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> {
        return pages.map { map ->
            success(map[uuid])
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

    override fun getAllPages(): Flow<Result<List<Page>>> {
        return pages.map { map ->
            success(map.values.toList())
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

    override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> {
        return pages.map { map ->
            success(map.values.sortedByDescending { it.updatedAt }.take(limit))
        }
    }

    override suspend fun savePage(page: Page): Result<Unit> {
        val current = pages.value.toMutableMap()
        current[page.uuid] = page
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

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        val page = current[pageUuid] ?: return Result.failure(Exception("Page not found"))
        current[pageUuid] = page.copy(name = newName, updatedAt = kotlinx.datetime.Clock.System.now())
        pages.value = current
        return success(Unit)
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        current.remove(pageUuid)
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
