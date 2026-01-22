package com.logseq.kmp.repository

import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.model.Block
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.Result.Companion.success

class SqlDelightReferenceRepository(
    private val database: LogseqDatabase
) : ReferenceRepository {

    private val queries = database.logseqDatabaseQueries

    override fun getOutgoingReferences(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = queries.selectReferencedBlocksByUuid(blockUuid)
                .executeAsList()
                .map { it.toModel() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getIncomingReferences(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = queries.selectReferencingBlocksByUuid(blockUuid)
                .executeAsList()
                .map { it.toModel() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getAllReferences(blockUuid: String): Flow<Result<BlockReferences>> = flow {
        try {
            val outgoing = queries.selectReferencedBlocksByUuid(blockUuid)
                .executeAsList()
                .map { it.toModel() }
            val incoming = queries.selectReferencingBlocksByUuid(blockUuid)
                .executeAsList()
                .map { it.toModel() }
            emit(success(BlockReferences(outgoing, incoming)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun addReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val exists = queries.existsReference(fromBlockUuid, toBlockUuid).executeAsOne() > 0
            if (!exists) {
            queries.insertReference(
                from_block_uuid = fromBlockUuid,
                to_block_uuid = toBlockUuid,
                created_at = Clock.System.now().toEpochMilliseconds()
            )
            }
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            queries.deleteReference(fromBlockUuid, toBlockUuid)
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getOrphanedBlocks(): Flow<Result<List<Block>>> = flow {
        try {
            val blocks = queries.selectOrphanedBlocks()
                .executeAsList()
                .map { it.toModel() }
            emit(success(blocks))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getMostConnectedBlocks(limit: Int): Flow<Result<List<BlockWithReferenceCount>>> = flow {
        try {
            val blocksWithCounts = mutableMapOf<String, Int>()
            queries.selectMostConnectedBlocks(limit.toLong())
                .executeAsList()
                .forEach { row ->
                    val incomingCount = queries.selectIncomingReferenceCount(row.uuid).executeAsOne()
                    val outgoingCount = queries.selectReferenceCount(row.uuid).executeAsOne()
                    val totalCount = incomingCount.toInt() + outgoingCount.toInt()
                    blocksWithCounts[row.uuid] = totalCount
                }

            val sorted = blocksWithCounts.entries
                .mapNotNull { (uuid, count) ->
                    val row = queries.selectBlockByUuid(uuid).executeAsOneOrNull()
                    row?.let {
                        BlockWithReferenceCount(
                            block = it.toModel(),
                            referenceCount = count
                        )
                    }
                }
                .sortedByDescending { it.referenceCount }
                .take(limit)

            emit(success(sorted))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

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
