package com.logseq.kmp.repository

import com.logseq.kmp.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.logseq.kmp.coroutines.PlatformDispatcher
import kotlin.Result.Companion.success

/**
 * Datalog-style in-memory repository for pages that mirrors Datascript behavior.
 * Uses Datalog query patterns similar to Logseq's Clojure implementation.
 * Cross-platform compatible - doesn't use JVM-specific Dispatchers.IO.
 */
class DatascriptPageRepository : PageRepository {

    private val pages = MutableStateFlow<Map<String, Page>>(emptyMap())

    /**
     * Datalog-style indexes for fast lookups
     */
    private val byUuid = MutableStateFlow<Map<String, Page>>(emptyMap())
    private val byName = MutableStateFlow<Map<String, Page>>(emptyMap())
    private val byNamespace = MutableStateFlow<Map<String, List<Page>>>(emptyMap())

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> {
        return pages.map { map ->
            success(map[uuid])
        }
    }

    override fun getPageByName(name: String): Flow<Result<Page?>> {
        return byName.map { map ->
            success(map[name])
        }
    }

    override fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>> {
        return byNamespace.map { map ->
            success(map[namespace] ?: emptyList())
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
        return try {
            val current = pages.value.toMutableMap()
            current[page.uuid] = page
            pages.value = current

            // Update indexes
            val uuidIndex = byUuid.value.toMutableMap()
            uuidIndex[page.uuid] = page
            byUuid.value = uuidIndex

            val nameIndex = byName.value.toMutableMap()
            nameIndex[page.name] = page
            byName.value = nameIndex

            val namespaceIndex = byNamespace.value.toMutableMap()
            if (page.namespace != null) {
                val existing = namespaceIndex[page.namespace]?.toMutableList() ?: mutableListOf()
                existing.removeAll { it.uuid == page.uuid }
                existing.add(page)
                namespaceIndex[page.namespace] = existing
            }
            byNamespace.value = namespaceIndex

            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> {
        return try {
            val page = pages.value[pageUuid] ?: return Result.failure(Exception("Page not found"))
            val newPage = page.copy(name = newName)
            
            // Update main storage
            val current = pages.value.toMutableMap()
            current[pageUuid] = newPage
            pages.value = current

            // Update indexes
            val uuidIndex = byUuid.value.toMutableMap()
            uuidIndex[pageUuid] = newPage
            byUuid.value = uuidIndex

            val nameIndex = byName.value.toMutableMap()
            nameIndex.remove(page.name)
            nameIndex[newName] = newPage
            byName.value = nameIndex

            // Note: Namespace update logic is handled by Service layer or savePage
            // Here we just update the name in the index
            
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> {
        return try {
            val page = pages.value[pageUuid] ?: return success(Unit)

            val current = pages.value.toMutableMap()
            current.remove(pageUuid)
            pages.value = current

            // Remove from indexes
            val uuidIndex = byUuid.value.toMutableMap()
            uuidIndex.remove(pageUuid)
            byUuid.value = uuidIndex

            val nameIndex = byName.value.toMutableMap()
            nameIndex.remove(page.name)
            byName.value = nameIndex

            val namespaceIndex = byNamespace.value.toMutableMap()
            page.namespace?.let { ns ->
                namespaceIndex[ns]?.let { list ->
                    namespaceIndex[ns] = list.filter { it.uuid != pageUuid }
                }
            }
            byNamespace.value = namespaceIndex

            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
