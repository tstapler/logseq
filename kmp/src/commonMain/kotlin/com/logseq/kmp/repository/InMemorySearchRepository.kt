package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.Result.Companion.success

/**
 * In-memory implementation of SearchRepository for testing and fallback scenarios.
 * Provides full-text search capabilities with relevance scoring and highlighting.
 */
class InMemorySearchRepository(
    private val pageRepository: SimplePageRepository? = null,
    private val blockRepository: BlockRepository? = null
) : SearchRepository {

    // Storage for test helper methods
    private val blocksMap = mutableMapOf<String, Block>()
    private val pagesMap = mutableMapOf<String, Page>()
    private val references = mutableListOf<ReferenceEdge>()

    private data class ReferenceEdge(
        val fromUuid: String,
        val toUuid: String
    )

    // ===== TEST HELPER METHODS =====

    fun saveBlockForTest(block: Block) {
        blocksMap[block.uuid] = block
    }

    fun savePageForTest(page: Page) {
        pagesMap[page.uuid] = page
    }

    fun addReferenceForTest(fromUuid: String, toUuid: String) {
        if (references.none { it.fromUuid == fromUuid && it.toUuid == toUuid }) {
            references.add(ReferenceEdge(fromUuid, toUuid))
        }
    }

    fun clearForTest() {
        blocksMap.clear()
        pagesMap.clear()
        references.clear()
    }

    // ===== REPOSITORY IMPLEMENTATION =====

    override fun searchBlocksByContent(
        query: String,
        limit: Int,
        offset: Int
    ): Flow<Result<List<Block>>> = flow {
        if (query.isBlank()) {
            emit(success(emptyList()))
            return@flow
        }

        val results = blocksMap.values
            .filter { block ->
                block.content.contains(query, ignoreCase = true) ||
                    block.properties.values.any { it.contains(query, ignoreCase = true) }
            }
            .map { block ->
                block.copy(
                    content = highlightMatch(block.content, query)
                )
            }
            .sortedByDescending { calculateRelevance(it.content, query) }
            .drop(offset)
            .take(limit)

        emit(success(results))
    }.flowOn(Dispatchers.IO)

    override fun searchPagesByTitle(query: String, limit: Int): Flow<Result<List<Page>>> = flow {
        if (query.isBlank()) {
            emit(success(emptyList()))
            return@flow
        }

        val results = pagesMap.values
            .filter { page ->
                page.name.contains(query, ignoreCase = true)
            }
            .sortedByDescending { calculatePageRelevance(it.name, query) }
            .take(limit)

        emit(success(results))
    }.flowOn(Dispatchers.IO)

    override fun findBlocksReferencing(blockUuid: String): Flow<Result<List<Block>>> = flow {
        if (blockUuid.isBlank()) {
            emit(success(emptyList()))
            return@flow
        }

        // Find blocks that reference the given block UUID
        val results = blocksMap.values.filter { block ->
            references.any { it.fromUuid == block.uuid && it.toUuid == blockUuid }
        }

        emit(success(results))
    }.flowOn(Dispatchers.IO)

    override fun searchWithFilters(searchRequest: SearchRequest): Flow<Result<SearchResult>> = flow {
        var filteredBlocks = blocksMap.values.toList()
        var filteredPages = pagesMap.values.toList()

        // Apply query filter to blocks
        if (!searchRequest.query.isNullOrBlank()) {
            filteredBlocks = filteredBlocks.filter { block ->
                block.content.contains(searchRequest.query, ignoreCase = true) ||
                    block.properties.values.any { it.contains(searchRequest.query, ignoreCase = true) }
            }
        }

        // Apply query filter to pages
        if (!searchRequest.query.isNullOrBlank()) {
            filteredPages = filteredPages.filter { page ->
                page.name.contains(searchRequest.query, ignoreCase = true)
            }
        }

        // Apply property filters to blocks
        searchRequest.propertyFilters.forEach { (key, value) ->
            filteredBlocks = filteredBlocks.filter { block ->
                block.properties[key]?.contains(value, ignoreCase = true) == true
            }
        }

        // Apply property filters to pages
        searchRequest.propertyFilters.forEach { (key, value) ->
            filteredPages = filteredPages.filter { page ->
                page.properties[key]?.contains(value, ignoreCase = true) == true
            }
        }

        // Apply date range filter to blocks
        searchRequest.dateRange?.let { dateRange ->
            filteredBlocks = filteredBlocks.filter { block ->
                val updatedInRange = dateRange.startDate?.let { start ->
                    block.updatedAt >= start
                } ?: true
                val updatedBeforeEnd = dateRange.endDate?.let { end ->
                    block.updatedAt <= end
                } ?: true
                updatedInRange && updatedBeforeEnd
            }
        }

        // Apply page UUID filter
        searchRequest.pageUuid?.let { pageUuid ->
            val page = pagesMap.values.find { it.uuid == pageUuid }
            if (page != null) {
                filteredBlocks = filteredBlocks.filter { it.pageId == page.id }
            }
        }

        // Calculate relevance for blocks and sort
        val scoredBlocks = filteredBlocks.map { block ->
            val score = calculateRelevance(
                highlightMatch(block.content, searchRequest.query ?: ""),
                searchRequest.query ?: ""
            )
            block to score
        }
        val sortedBlocks = scoredBlocks
            .sortedByDescending { (_, score) -> score }
            .map { (block, _) -> block }

        // Apply pagination
        val totalCount = sortedBlocks.size + filteredPages.size
        val paginatedBlocks = sortedBlocks
            .drop(searchRequest.offset)
            .take(searchRequest.limit)

        val paginatedPages = filteredPages
            .drop(searchRequest.offset)
            .take(searchRequest.limit)

        val hasMore = (searchRequest.offset + searchRequest.limit) < totalCount

        emit(
            success(
                SearchResult(
                    blocks = paginatedBlocks,
                    pages = paginatedPages,
                    totalCount = totalCount,
                    hasMore = hasMore
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Calculate relevance score for block content matching.
     *
     * Scoring hierarchy:
     * - Exact match (case-insensitive) = 1.0
     * - Word prefix match = 0.7
     * - Contains match = 0.5
     * - Property match = 0.3
     */
    fun calculateRelevance(content: String, query: String): Float {
        if (query.isBlank()) return 0f

        val lowerContent = content.lowercase()
        val lowerQuery = query.lowercase()

        // Exact match
        if (lowerContent.contains(lowerQuery)) {
            // Check if it's an exact word match for higher score
            val wordPattern = Regex("""\b${Regex.escape(lowerQuery)}\b""")
            if (wordPattern.containsMatchIn(lowerContent)) {
                return 1.0f
            }
            return 0.5f
        }

        // Word prefix match
        val queryWords = lowerQuery.split(Regex("\\s+"))
        val contentWords = lowerContent.split(Regex("\\W+"))
        val prefixMatches = queryWords.count { queryWord ->
            contentWords.any { it.startsWith(queryWord) }
        }
        if (prefixMatches > 0) {
            return 0.7f * (prefixMatches.toFloat() / queryWords.size.coerceAtLeast(1))
        }

        return 0f
    }

    /**
     * Calculate relevance score for page title matching.
     *
     * Scoring hierarchy:
     * - Exact prefix match = 1.0
     * - Contains match = 0.7
     * - Properties match = 0.3
     */
    private fun calculatePageRelevance(name: String, query: String): Float {
        if (query.isBlank()) return 0f

        val lowerName = name.lowercase()
        val lowerQuery = query.lowercase()

        // Exact prefix match on name
        if (lowerName.startsWith(lowerQuery)) {
            return 1.0f
        }

        // Contains match on name
        if (lowerName.contains(lowerQuery)) {
            return 0.7f
        }

        return 0.3f
    }

    /**
     * Generate highlight markup around matching text.
     * Wraps matched text with <em> tags for display.
     */
    fun highlightMatch(content: String, query: String): String {
        if (query.isBlank() || content.isBlank()) {
            return content
        }

        val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        return pattern.replace(content) { match ->
            "<em>${match.value}</em>"
        }
    }
}
