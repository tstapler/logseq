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
 * Uses FTS5 for block content search and SQL LIKE for page title search.
 * 
 * Updated to use UUID-native storage for all references.
 */
class SqlDelightSearchRepository(
    private val database: LogseqDatabase
) : SearchRepository {

    private val queries = database.logseqDatabaseQueries

    override fun searchBlocksByContent(query: String, limit: Int, offset: Int): Flow<Result<List<Block>>> = flow {
        try {
            val sanitized = sanitizeFtsQuery(query)
            if (sanitized.isEmpty()) {
                emit(success(emptyList()))
                return@flow
            }
            val results = queries.searchBlocksByContentFts(
                query = sanitized,
                limit = limit.toLong(),
                offset = offset.toLong()
            ).executeAsList().map { it.toBlockModel() }
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
        try {
            val query = searchRequest.query

            val blocks: List<Block> = if (!query.isNullOrBlank()) {
                val sanitized = sanitizeFtsQuery(query)
                if (sanitized.isNotEmpty()) {
                    try {
                        queries.searchBlocksByContentFts(
                            query = sanitized,
                            limit = searchRequest.limit.toLong(),
                            offset = searchRequest.offset.toLong()
                        ).executeAsList().map { it.toBlockModel() }
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else emptyList()
            } else emptyList()

            val pages: List<Page> = if (!query.isNullOrBlank()) {
                queries.selectPagesByNameLike("%$query%")
                    .executeAsList()
                    .take(searchRequest.limit)
                    .map { it.toPageModel() }
            } else emptyList()

            emit(success(SearchResult(
                blocks = blocks,
                pages = pages,
                totalCount = blocks.size + pages.size,
                hasMore = false
            )))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    /**
     * Strips FTS5 operator characters to prevent query syntax errors from user input.
     * The `*` prefix-match operator is appended by the SQL query itself.
     */
    private fun sanitizeFtsQuery(query: String): String =
        query.trim()
            .replace(Regex("""["()*:^~{}\[\]!]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun com.logseq.kmp.db.SearchBlocksByContentFts.toBlockModel(): Block {
        return Block(
            uuid = this.uuid,
            pageUuid = this.page_uuid,
            parentUuid = this.parent_uuid,
            leftUuid = this.left_uuid,
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

    private fun com.logseq.kmp.db.Blocks.toBlockModel(): Block {
        return Block(
            uuid = this.uuid,
            pageUuid = this.page_uuid,
            parentUuid = this.parent_uuid,
            leftUuid = this.left_uuid,
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
