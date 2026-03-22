package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Property
import com.logseq.kmp.coroutines.PlatformDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.Result.Companion.success

/**
 * SQLDelight implementation of PropertyRepository.
 */
class SqlDelightPropertyRepository(
    private val database: LogseqDatabase
) : PropertyRepository {

    private val queries = database.logseqDatabaseQueries

    override fun getPropertiesForBlock(blockUuid: String): Flow<Result<List<Property>>> = flow {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null) {
                emit(success(emptyList()))
            } else {
                val properties = parseProperties(block.id, block.properties)
                emit(success(properties))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun getProperty(blockUuid: String, key: String): Flow<Result<Property?>> = flow {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null) {
                emit(success(null))
            } else {
                val property = parseProperties(block.id, block.properties).find { it.key == key }
                emit(success(property))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override suspend fun saveProperty(property: Property): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            val block = queries.selectBlockById(property.blockId).executeAsOneOrNull()
            if (block != null) {
                val existing = parseProperties(block.id, block.properties).associate { it.key to it.value }.toMutableMap()
                existing[property.key] = property.value
                val updatedString = existing.entries.joinToString(",") { "${it.key}:${it.value}" }
                queries.updateBlockProperties(updatedString, block.id)
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProperty(blockUuid: String, key: String): Result<Unit> = withContext(PlatformDispatcher.IO) {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block != null) {
                val existing = parseProperties(block.id, block.properties).associate { it.key to it.value }.toMutableMap()
                existing.remove(key)
                val updatedString = existing.entries.joinToString(",") { "${it.key}:${it.value}" }
                queries.updateBlockProperties(updatedString, block.id)
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> = flow {
        try {
            val results = queries.selectAllBlocks().executeAsList()
                .filter { it.properties?.contains(key) == true }
                .map { it.toBlockModel() }
            emit(success(results))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> = flow {
        try {
            val results = queries.selectAllBlocks().executeAsList()
                .filter { it.properties?.contains("$key:$value") == true }
                .map { it.toBlockModel() }
            emit(success(results))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(PlatformDispatcher.IO)

    private fun parseProperties(blockId: Long, propertiesString: String?): List<Property> {
        return propertiesString?.split(",")?.mapNotNull {
            val parts = it.split(":", limit = 2)
            if (parts.size == 2) {
                Property(0L, blockId, parts[0], parts[1], kotlinx.datetime.Clock.System.now())
            } else null
        } ?: emptyList()
    }

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
            properties = emptyMap()
        )
    }
}
