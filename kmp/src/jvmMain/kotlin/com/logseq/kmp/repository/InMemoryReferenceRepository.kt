package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.Result.Companion.success

class InMemoryReferenceRepository : ReferenceRepository {

    private val references = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    private val blocks = MutableStateFlow<Map<String, Block>>(emptyMap())

    fun setBlocks(blocksMap: Map<String, Block>) {
        blocks.value = blocksMap
    }

    override fun getOutgoingReferences(blockUuid: String): Flow<Result<List<Block>>> {
        return references.map { refMap ->
            val referencedUuids = refMap[blockUuid] ?: emptySet()
            val allBlocks = blocks.value
            val referencedBlocks = referencedUuids.mapNotNull { allBlocks[it] }
            success(referencedBlocks)
        }
    }

    override fun getIncomingReferences(blockUuid: String): Flow<Result<List<Block>>> {
        return references.map { refMap ->
            val referencingBlocks = refMap.entries
                .filter { it.value.contains(blockUuid) }
                .mapNotNull { (fromUuid, _) -> blocks.value[fromUuid] }
            success(referencingBlocks)
        }
    }

    override fun getAllReferences(blockUuid: String): Flow<Result<BlockReferences>> {
        return references.map { refMap ->
            val outgoingUuids = refMap[blockUuid] ?: emptySet()
            val allBlocks = blocks.value
            val outgoing = outgoingUuids.mapNotNull { allBlocks[it] }
            val incoming = refMap.entries
                .filter { it.value.contains(blockUuid) }
                .mapNotNull { (fromUuid, _) -> allBlocks[fromUuid] }
            success(BlockReferences(outgoing, incoming))
        }
    }

    override suspend fun addReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = references.value.toMutableMap()
            val existing = current[fromBlockUuid]?.toMutableSet() ?: mutableSetOf()
            existing.add(toBlockUuid)
            current[fromBlockUuid] = existing
            references.value = current
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeReference(fromBlockUuid: String, toBlockUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = references.value.toMutableMap()
            val existing = current[fromBlockUuid]?.toMutableSet() ?: return@withContext success(Unit)
            existing.remove(toBlockUuid)
            if (existing.isEmpty()) {
                current.remove(fromBlockUuid)
            } else {
                current[fromBlockUuid] = existing
            }
            references.value = current
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getOrphanedBlocks(): Flow<Result<List<Block>>> {
        return references.map { refMap ->
            val allReferenced = refMap.values.flatten().toSet()
            val orphaned = blocks.value.values.filter { it.uuid !in allReferenced }
            success(orphaned)
        }
    }

    override fun getMostConnectedBlocks(limit: Int): Flow<Result<List<BlockWithReferenceCount>>> {
        return references.map { refMap ->
            val referenceCounts = mutableMapOf<String, Int>()
            refMap.forEach { (from, toSet) ->
                referenceCounts[from] = (referenceCounts[from] ?: 0) + toSet.size
                toSet.forEach { to ->
                    referenceCounts[to] = (referenceCounts[to] ?: 0) + 1
                }
            }
            val allBlocks = blocks.value
            val sorted = referenceCounts.entries
                .mapNotNull { (uuid, count) -> allBlocks[uuid]?.let { BlockWithReferenceCount(it, count) } }
                .sortedByDescending { it.referenceCount }
                .take(limit)
            success(sorted)
        }
    }
}
