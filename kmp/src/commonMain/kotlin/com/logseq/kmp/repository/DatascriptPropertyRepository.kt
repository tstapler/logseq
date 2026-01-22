package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlin.Result.Companion.success

class DatascriptPropertyRepository : PropertyRepository {

    private val properties = MutableStateFlow<Map<Long, Map<String, Property>>>(emptyMap())

    private val blocks = MutableStateFlow<Map<String, Block>>(emptyMap())

    fun setBlocks(blocksMap: Map<String, Block>) {
        blocks.value = blocksMap
    }

    override fun getPropertiesForBlock(blockUuid: String): Flow<Result<List<Property>>> {
        return properties.map { map ->
            val blockId = blockUuid.hashCode().toLong()
            val props = map[blockId]?.values?.toList() ?: emptyList()
            success(props)
        }
    }

    override fun getProperty(blockUuid: String, key: String): Flow<Result<Property?>> {
        return properties.map { map ->
            val blockId = blockUuid.hashCode().toLong()
            val prop = map[blockId]?.get(key)
            success(prop)
        }
    }

    override suspend fun saveProperty(property: Property): Result<Unit> {
        return try {
            val current = properties.value.toMutableMap()
            val blockProps = current.getOrPut(property.blockId) { mutableMapOf() }.toMutableMap()
            blockProps[property.key] = property
            val newMap = current.toMutableMap()
            newMap[property.blockId] = blockProps
            properties.value = newMap
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProperty(blockUuid: String, key: String): Result<Unit> {
        return try {
            val blockId = blockUuid.hashCode().toLong()
            val current = properties.value.toMutableMap()
            val blockProps = current[blockId]?.toMutableMap() ?: return success(Unit)
            blockProps.remove(key)
            val newMap = current.toMutableMap()
            newMap[blockId] = blockProps
            properties.value = newMap
            success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> {
        return properties.map { map ->
            val blockIds = map.filter { it.value.containsKey(key) }.keys
            val allBlocks = blocks.value
            val blocksWithKey = blockIds.mapNotNull { blockId ->
                allBlocks.values.find { it.id == blockId }
            }
            success(blocksWithKey)
        }
    }

    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> {
        return properties.map { map ->
            val blockIds = map.filter { blockProps ->
                blockProps.value[key]?.value == value
            }.keys
            val allBlocks = blocks.value
            val blocksWithValue = blockIds.mapNotNull { blockId ->
                allBlocks.values.find { it.id == blockId }
            }
            success(blocksWithValue)
        }
    }
}
