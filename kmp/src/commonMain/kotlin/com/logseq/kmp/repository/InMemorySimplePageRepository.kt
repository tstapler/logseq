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

    override fun getPageById(id: Long): Flow<Result<Page?>> {
        return pages.map { map ->
            Result.success(map.values.find { it.id == id })
        }
    }

    override fun getPageByName(name: String): Flow<Result<Page?>> {
        return pages.map { map ->
            // Case-insensitive search by page name or alias
            val page = map.values.find { page ->
                page.name.equals(name, ignoreCase = true) ||
                    page.properties["alias"]?.split(",")?.any { it.trim().equals(name, ignoreCase = true) } == true
            }
            Result.success(page)
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

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        val page = current[pageUuid] ?: return Result.failure(Exception("Page not found"))

        // Check for name collision
        val existing = current.values.find { it.name.equals(newName, ignoreCase = true) && it.uuid != pageUuid }
        if (existing != null) {
            return Result.failure(Exception("A page with name '$newName' already exists"))
        }

        current[pageUuid] = page.copy(
            name = newName,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
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
