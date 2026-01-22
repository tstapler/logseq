package com.logseq.kmp.ui.screens

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.SearchRepository
import com.logseq.kmp.repository.SearchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }

        searchJob = scope.launch {
            delay(300) // Debounce
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val request = SearchRequest(query = query, limit = 20)
                searchRepository.searchWithFilters(request).collect { result ->
                    val searchResult = result.getOrNull()
                    if (searchResult != null) {
                        val items = mutableListOf<SearchResultItem>()
                        
                        // Add Pages
                        if (searchResult.pages.isNotEmpty()) {
                            items.add(SearchResultItem.Header("Pages"))
                            items.addAll(searchResult.pages.map { SearchResultItem.PageItem(it) })
                        }
                        
                        // Add Blocks
                        if (searchResult.blocks.isNotEmpty()) {
                            items.add(SearchResultItem.Header("Blocks"))
                            items.addAll(searchResult.blocks.map { SearchResultItem.BlockItem(it) })
                        }
                        
                        _uiState.update { 
                            it.copy(
                                results = items,
                                isLoading = false
                            ) 
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Search failed") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class SearchResultItem {
    data class Header(val title: String) : SearchResultItem()
    data class PageItem(val page: Page) : SearchResultItem()
    data class BlockItem(val block: Block) : SearchResultItem()
}
