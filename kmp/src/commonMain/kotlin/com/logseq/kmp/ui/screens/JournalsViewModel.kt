package com.logseq.kmp.ui.screens

import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.SimplePageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JournalsViewModel(
    private val pageRepository: SimplePageRepository,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(JournalsUiState())
    val uiState: StateFlow<JournalsUiState> = _uiState.asStateFlow()

    private var currentOffset = 0
    private val pageSize = 10
    private var isLoading = false
    private var hasMore = true

    init {
        loadMore()
    }

    fun loadMore() {
        if (isLoading || !hasMore) return

        isLoading = true
        scope.launch {
            try {
                val result = pageRepository.getJournalPages(pageSize, currentOffset).first()
                val newPages = result.getOrNull() ?: emptyList()

                if (newPages.isEmpty()) {
                    hasMore = false
                } else {
                    _uiState.update { currentState ->
                        currentState.copy(
                            pages = currentState.pages + newPages
                        )
                    }
                    currentOffset += newPages.size
                    // If we got fewer pages than requested, we reached the end
                    if (newPages.size < pageSize) {
                        hasMore = false
                    }
                }
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
    
    fun refresh() {
        currentOffset = 0
        hasMore = true
        _uiState.update { it.copy(pages = emptyList()) }
        loadMore()
    }
}

data class JournalsUiState(
    val pages: List<Page> = emptyList()
)
