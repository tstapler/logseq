package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.Result.Companion.success

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
                val properties = queries.selectPropertiesByBlockId(block.id)
                    .executeAsList()
                    .map { it.toModel() }
                emit(success(properties))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getProperty(blockUuid: String, key: String): Flow<Result<Property?>> = flow {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block == null) {
                emit(success(null))
            } else {
                val property = queries.selectPropertyByBlockIdAndKey(block.id, key)
                    .executeAsOneOrNull()
                emit(success(property?.toModel()))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun saveProperty(property: Property): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val exists = queries.existsProperty(property.blockId, property.key).executeAsOne() > 0
            if (exists) {
                queries.updateProperty(
                    value_ = property.value,
                    block_id = property.blockId,
                    key = property.key
                )
            } else {
                queries.insertProperty(
                    block_id = property.blockId,
                    key = property.key,
                    value_ = property.value,
                    created_at = property.createdAt.toEpochMilliseconds()
                )
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProperty(blockUuid: String, key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val block = queries.selectBlockByUuid(blockUuid).executeAsOneOrNull()
            if (block != null) {
                queries.deleteProperty(block.id, key)
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = queries.selectBlocksWithPropertyKey(key)
                .executeAsList()
                .map { it.toModel() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = queries.selectBlocksWithPropertyKeyValue(key, value)
                .executeAsList()
                .map { it.toModel() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun com.logseq.kmp.db.Properties.toModel(): Property {
        return Property(
            id = this.id,
            blockId = this.block_id,
            key = this.key,
            value = this.value_,
            createdAt = Instant.fromEpochMilliseconds(this.created_at)
        )
    }

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
            properties = this.properties?.split(",")?.associate {
                val parts = it.split(":", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            } ?: emptyMap()
        )
    }
}
