package com.logseq.kmp.repository

import com.logseq.kmp.model.Page
import kotlinx.coroutines.flow.Flow
import kotlin.Result

interface SimplePageRepository {
    fun getAllPages(): Flow<Result<List<Page>>>
    fun getRecentPages(limit: Int): Flow<Result<List<Page>>>
    fun getFavoritePages(): Flow<Result<List<Page>>>
    fun getJournalPages(limit: Int, offset: Int): Flow<Result<List<Page>>>
    fun getPageByUuid(uuid: String): Flow<Result<Page?>>
    fun getPageById(id: Long): Flow<Result<Page?>>
    fun getPageByName(name: String): Flow<Result<Page?>>
    suspend fun savePage(page: Page): Result<Unit>
    suspend fun deletePage(pageUuid: String): Result<Unit>
    suspend fun renamePage(pageUuid: String, newName: String): Result<Unit>
    suspend fun toggleFavorite(pageUuid: String): Result<Unit>
    suspend fun clear()
}
