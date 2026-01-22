package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.db.Pages
import com.logseq.kmp.db.SearchBlocksByContentFts
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.Result.Companion.success

/**
 * SQLDelight implementation of SearchRepository.
 * Uses FTS5 full-text search for efficient content searching.
 */
class SqlDelightSearchRepository(
    private val database: LogseqDatabase
) : SearchRepository {

    private val queries = database.logseqDatabaseQueries

    override fun searchBlocksByContent(
        query: String,
        limit: Int,
        offset: Int
    ): Flow<Result<List<Block>>> = flow {
        try {
            val results = queries.searchBlocksByContentFts(query, limit.toLong(), offset.toLong())
                .executeAsList()
            emit(success(results.map { it.toBlock() }))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun searchPagesByTitle(query: String, limit: Int): Flow<Result<List<Page>>> = flow {
        try {
            val results = queries.searchPagesByTitle(query, limit.toLong())
                .executeAsList()
            emit(success(results.map { it.toPage() }))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun findBlocksReferencing(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val results = queries.findBlocksReferencing(blockUuid)
                .executeAsList()
            emit(success(results.map { it.toModel() }))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun searchWithFilters(searchRequest: SearchRequest): Flow<Result<SearchResult>> = flow {
        try {
            val query = searchRequest.query
            
            // First, get the total count for pagination
            val totalCount = if (!query.isNullOrBlank()) {
                queries.searchBlocksCountFts(query).executeAsOne()
            } else {
                0L
            }

            // Get blocks matching the query (FTS search)
            val blocks = if (!query.isNullOrBlank()) {
                queries.searchBlocksByContentFts(
                    query,
                    searchRequest.limit.toLong(),
                    searchRequest.offset.toLong()
                ).executeAsList().map { it.toBlock() }
            } else {
                emptyList()
            }

            // Get pages matching the query
            val pages = if (!query.isNullOrBlank()) {
                queries.searchPagesByTitle(query, searchRequest.limit.toLong())
                    .executeAsList().map { it.toPage() }
            } else {
                emptyList()
            }

            // Calculate hasMore based on offset + limit vs totalCount
            val hasMore = (searchRequest.offset + searchRequest.limit) < totalCount

            emit(success(SearchResult(blocks, pages, totalCount.toInt(), hasMore)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Convert SearchBlocksByContentFts to Block model.
 * FTS results include a highlight column that is not used in the Block model.
 */
private fun SearchBlocksByContentFts.toBlock(): Block {
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
        properties = parseProperties(this.properties)
    )
}

/**
 * Convert Blocks to Block model.
 */
private fun com.logseq.kmp.db.Blocks.toModel(): Block {
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
        properties = parseProperties(this.properties)
    )
}

/**
 * Convert Pages to Page model.
 */
private fun Pages.toPage(): Page {
    return Page(
        id = this.id,
        uuid = this.uuid,
        name = this.name,
        namespace = this.namespace,
        filePath = this.file_path,
        createdAt = Instant.fromEpochMilliseconds(this.created_at),
        updatedAt = Instant.fromEpochMilliseconds(this.updated_at),
        properties = parseProperties(this.properties)
    )
}

/**
 * Parse properties string to Map.
 * Tries to parse as JSON, falls back to empty map.
 */
private fun parseProperties(propertiesString: String?): Map<String, String> {
    if (propertiesString.isNullOrBlank()) return emptyMap()
    return try {
        Json.decodeFromString<Map<String, String>>(propertiesString)
    } catch (e: Exception) {
        // Fallback or log error
        emptyMap()
    }
}
