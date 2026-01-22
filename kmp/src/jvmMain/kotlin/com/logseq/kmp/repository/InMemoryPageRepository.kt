package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.Result.Companion.success

/**
 * In-memory implementation of PageRepository for testing purposes.
 */
class InMemoryPageRepository : PageRepository {

    private val pages = MutableStateFlow<Map<String, Page>>(emptyMap())

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> {
        return pages.map { map ->
            success(map[uuid])
        }
    }

    override fun getPageByName(name: String): Flow<Result<Page?>> {
        return pages.map { map ->
            success(map.values.find { it.name == name })
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

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        val page = current[pageUuid] ?: return Result.failure(Exception("Page not found"))
        current[pageUuid] = page.copy(name = newName)
        pages.value = current
        return success(Unit)
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        val current = pages.value.toMutableMap()
        current.remove(pageUuid)
        pages.value = current
        return success(Unit)
    }
}
