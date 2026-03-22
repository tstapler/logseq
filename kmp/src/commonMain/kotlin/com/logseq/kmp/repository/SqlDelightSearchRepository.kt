package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.coroutines.PlatformDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Instant
import kotlin.Result.Companion.success

/**
 * SQLDelight implementation of SearchRepository.
 */
class SqlDelightSearchRepository(
    private val database: LogseqDatabase
) : SearchRepository {

    private val queries = database.logseqDatabaseQueries

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> = flow {
        try {
            // Using SQL LIKE for now, can be optimized with FTS5 later
            val results = queries.selectBlocksWithContentLike("%$query%")
                .executeAsList()
                .drop(offset)
                .take(limit)
                .map { it.toBlockModel() }
            emit(success(results))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun searchPagesByTitle(query: String, limit: Int): Flow<Result<List<Page>>> = flow {
        try {
            val results = queries.selectPagesByNameLike("%$query%")
                .executeAsList()
                .take(limit)
                .map { it.toPageModel() }
            emit(success(results))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun findBlocksReferencing(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val results = queries.selectBlocksReferencing(blockUuid)
                .executeAsList()
                .map { it.toBlockModel() }
            emit(success(results))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun searchWithFilters(searchRequest: SearchRequest): Flow<Result<SearchResult>> = flow {
        // Basic implementation for now
        try {
            val blocks = queries.selectAllBlocks().executeAsList().map { it.toBlockModel() }
            val pages = queries.selectAllPages().executeAsList().map { it.toPageModel() }
            
            val filteredBlocks = blocks.filter { b -> 
                searchRequest.query?.let { q -> b.content.contains(q, ignoreCase = true) } ?: true 
            }
            
            val filteredPages = pages.filter { p ->
                searchRequest.query?.let { q -> p.name.contains(q, ignoreCase = true) } ?: true
            }

            emit(success(SearchResult(
                blocks = filteredBlocks.take(searchRequest.limit),
                pages = filteredPages.take(searchRequest.limit),
                totalCount = filteredBlocks.size + filteredPages.size,
                hasMore = false
            )))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    private fun com.logseq.kmp.db.Blocks.toBlockModel(): Block {
        return Block(
            id = this.id,
            uuid = this.uuid,
            pageId = this.page_id,
            parentId = this.parent_id,
            leftId = this.left_id,
            content = this.content,
            level = this.level.toInt(),
            position = this.position.toInt(),
            createdAt = Instant.fromEpochMilliseconds(this.created_at),
            updatedAt = Instant.fromEpochMilliseconds(this.updated_at),
            version = this.version,
            properties = this.properties?.split(",")?.mapNotNull {
                val parts = it.split(":", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }?.toMap() ?: emptyMap()
        )
    }

    private fun com.logseq.kmp.db.Pages.toPageModel(): Page {
        return Page(
            id = this.id,
            uuid = this.uuid,
            name = this.name,
            namespace = this.namespace,
            filePath = this.file_path,
            createdAt = Instant.fromEpochMilliseconds(this.created_at),
            updatedAt = Instant.fromEpochMilliseconds(this.updated_at),
            version = this.version,
            properties = emptyMap(),
            isJournal = this.is_journal == 1L,
            journalDate = this.journal_date?.let { kotlinx.datetime.LocalDate.parse(it) }
        )
    }
}
