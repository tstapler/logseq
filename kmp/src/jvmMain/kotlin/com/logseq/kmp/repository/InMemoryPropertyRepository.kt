package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.Result.Companion.success

class InMemoryPropertyRepository : PropertyRepository {

    private val properties = MutableStateFlow<Map<Long, Map<String, Property>>>(emptyMap())

    override fun getPropertiesForBlock(blockUuid: String): Flow<Result<List<Property>>> {
        val blockId = blockUuid.hashCode().toLong()
        return properties.map { map ->
            success(map[blockId]?.values?.toList() ?: emptyList())
        }
    }

    override fun getProperty(blockUuid: String, key: String): Flow<Result<Property?>> {
        val blockId = blockUuid.hashCode().toLong()
        return properties.map { map ->
            success(map[blockId]?.get(key))
        }
    }

    override suspend fun saveProperty(property: Property): Result<Unit> {
        val current = properties.value.toMutableMap()
        val blockProps = current.getOrPut(property.blockId) { mutableMapOf() }.toMutableMap()
        blockProps[property.key] = property
        val newMap = current.toMutableMap()
        newMap[property.blockId] = blockProps
        properties.value = newMap
        return success(Unit)
    }

    override suspend fun deleteProperty(blockUuid: String, key: String): Result<Unit> {
        val blockId = blockUuid.hashCode().toLong()
        val current = properties.value.toMutableMap()
        val blockProps = current[blockId]?.toMutableMap() ?: return success(Unit)
        blockProps.remove(key)
        val newMap = current.toMutableMap()
        newMap[blockId] = blockProps
        properties.value = newMap
        return success(Unit)
    }

    override fun getBlocksWithPropertyKey(key: String): Flow<Result<List<Block>>> {
        return kotlinx.coroutines.flow.flow { emit(success(emptyList())) }
    }

    override fun getBlocksWithPropertyValue(key: String, value: String): Flow<Result<List<Block>>> {
        return kotlinx.coroutines.flow.flow { emit(success(emptyList())) }
    }
}
