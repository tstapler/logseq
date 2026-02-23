package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.Result.Companion.success

/**
 * SQLDelight implementation of PageRepository.
 * Uses the generated LogseqDatabaseQueries for all operations.
 */
class SqlDelightPageRepository(
    private val database: LogseqDatabase
) : PageRepository {

    private val queries = database.logseqDatabaseQueries

    override fun getPageByUuid(uuid: String): Flow<Result<Page?>> = flow {
        try {
            val page = queries.selectPageByUuid(uuid).executeAsOneOrNull()
            emit(success(page?.toModel()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getPageByName(name: String): Flow<Result<Page?>> = flow {
        try {
            val page = queries.selectPageByName(name).executeAsOneOrNull()
            emit(success(page?.toModel()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getPagesInNamespace(namespace: String): Flow<Result<List<Page>>> = flow {
        try {
            val pages = queries.selectPagesByNamespaceUnpaginated(namespace)
                .executeAsList()
                .map { it.toModel() }
            emit(success(pages))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getAllPages(): Flow<Result<List<Page>>> = flow {
        try {
            val pages = queries.selectAllPages()
                .executeAsList()
                .map { it.toModel() }
            emit(success(pages))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getRecentPages(limit: Int): Flow<Result<List<Page>>> = flow {
        try {
            val pages = queries.selectRecentlyUpdatedPages(limit.toLong())
                .executeAsList()
                .map { it.toModel() }
            emit(success(pages))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun savePage(page: Page): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            queries.insertPage(
                uuid = page.uuid,
                name = page.name,
                namespace = page.namespace,
                file_path = page.filePath,
                created_at = page.createdAt.toEpochMilliseconds(),
                updated_at = page.updatedAt.toEpochMilliseconds(),
                properties = page.properties.entries.joinToString(",") { "${it.key}:${it.value}" },
                version = page.version
            )
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renamePage(pageUuid: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val page = queries.selectPageByUuid(pageUuid).executeAsOneOrNull()
            if (page != null) {
                queries.updatePageName(newName, page.id)
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePage(pageUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val page = queries.selectPageByUuid(pageUuid).executeAsOneOrNull()
            if (page != null) {
                queries.deletePageById(page.id)
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun com.logseq.kmp.db.Pages.toModel(): Page {
        return Page(
            id = this.id,
            uuid = this.uuid,
            name = this.name,
            namespace = this.namespace,
            filePath = this.file_path,
            createdAt = Instant.fromEpochMilliseconds(this.created_at),
            updatedAt = Instant.fromEpochMilliseconds(this.updated_at),
            version = this.version,
            properties = this.properties?.split(",")?.associate {
                val parts = it.split(":", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            } ?: emptyMap()
        )
    }
}
