package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.Result.Companion.success

/**
 * In-memory implementation of BlockRepository for testing purposes.
 */
class InMemoryBlockRepository : BlockRepository {

    private val blocks = MutableStateFlow<Map<String, Block>>(emptyMap())

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> {
        return blocks.map { map ->
            success(map[uuid])
        }
    }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val parent = map[blockUuid]
            if (parent == null) {
                success(emptyList())
            } else {
                success(map.values.filter { it.parentId == parent.id }.sortedBy { it.position })
            }
        }
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> {
        return blocks.map { map ->
            val result = mutableListOf<BlockWithDepth>()
            collectHierarchy(map, rootUuid, 0, result)
            success(result)
        }
    }

    private fun collectHierarchy(
        allBlocks: Map<String, Block>,
        uuid: String,
        depth: Int,
        result: MutableList<BlockWithDepth>
    ) {
        val block = allBlocks[uuid] ?: return
        result.add(BlockWithDepth(block, depth))
        val children = allBlocks.values.filter { it.parentId == block.id }.sortedBy { it.position }
        children.forEach { child ->
            collectHierarchy(allBlocks, child.uuid, depth + 1, result)
        }
    }

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val ancestors = mutableListOf<Block>()
            var currentUuid: String? = blockUuid
            while (currentUuid != null) {
                val block = map[currentUuid] ?: break
                if (block.parentId != null) {
                    val parent = map.values.find { it.id == block.parentId }
                    if (parent != null) {
                        ancestors.add(parent)
                        currentUuid = parent.uuid
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            success(ancestors.reversed())
        }
    }

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> {
        return blocks.map { map ->
            val block = map[blockUuid] ?: return@map success(null)
            val parent = if (block.parentId != null) {
                map.values.find { it.id == block.parentId }
            } else null
            success(parent)
        }
    }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val block = map[blockUuid] ?: return@map success(emptyList())
            val siblings = if (block.parentId != null) {
                map.values.filter { it.parentId == block.parentId && it.uuid != blockUuid }
            } else {
                map.values.filter { it.parentId == null && it.uuid != blockUuid }
            }
            success(siblings.sortedBy { it.position })
        }
    }

    override fun getBlocksForPage(pageId: Long): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val pageBlocks = map.values.filter { it.pageId == pageId }.sortedBy { it.position }
            success(pageBlocks)
        }
    }

    override suspend fun saveBlocks(blocks: List<Block>): Result<Unit> {
        val current = this.blocks.value.toMutableMap()
        blocks.forEach { current[it.uuid] = it }
        this.blocks.value = current
        return success(Unit)
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        val current = blocks.value.toMutableMap()
        current[block.uuid] = block
        blocks.value = current
        return success(Unit)
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        val current = blocks.value.toMutableMap()
        val block = current[blockUuid] ?: return success(Unit)

        if (deleteChildren) {
            val uuidsToDelete = mutableListOf(blockUuid)
            var index = 0
            while (index < uuidsToDelete.size) {
                val currentUuid = uuidsToDelete[index]
                val childBlocks = current.values.filter { it.parentId == current[currentUuid]?.id }
                childBlocks.forEach { child ->
                    uuidsToDelete.add(child.uuid)
                }
                index++
            }
            uuidsToDelete.forEach { current.remove(it) }
        } else {
            current.remove(blockUuid)
        }
        blocks.value = current
        return success(Unit)
    }

    override suspend fun moveBlock(
        blockUuid: String,
        newParentUuid: String?,
        newPosition: Int
    ): Result<Unit> {
        val current = blocks.value.toMutableMap()
        val block = current[blockUuid] ?: return success(Unit)

        val updatedBlock = block.copy(
            parentId = newParentUuid?.let { uuid ->
                current[uuid]?.id
            },
            position = newPosition
        )
        current[blockUuid] = updatedBlock
        blocks.value = current
        return success(Unit)
    }

    override suspend fun indentBlock(blockUuid: String): Result<Unit> {
        return success(Unit)
    }

    override suspend fun outdentBlock(blockUuid: String): Result<Unit> {
        return success(Unit)
    }

    override suspend fun moveBlockUp(blockUuid: String): Result<Unit> {
        return success(Unit)
    }

    override suspend fun moveBlockDown(blockUuid: String): Result<Unit> {
        return success(Unit)
    }

    override fun getLinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
        return blocks.map { map ->
            val linkedBlocks = map.values.filter { block ->
                wikiLinkPattern.containsMatchIn(block.content)
            }
            success(linkedBlocks.sortedBy { it.pageId })
        }
    }

    override fun getUnlinkedReferences(pageName: String): Flow<Result<List<Block>>> {
        val wikiLinkPattern = "\\[\\[${Regex.escape(pageName)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
        val plainTextPattern = "\\b${Regex.escape(pageName)}\\b".toRegex(RegexOption.IGNORE_CASE)
        return blocks.map { map ->
            val unlinkedBlocks = map.values.filter { block ->
                plainTextPattern.containsMatchIn(block.content) &&
                    !wikiLinkPattern.containsMatchIn(block.content)
            }
            success(unlinkedBlocks.sortedBy { it.pageId })
        }
    }

    override fun searchBlocksByContent(query: String): Flow<Result<List<Block>>> {
        return blocks.map { map ->
            val matchingBlocks = map.values.filter { block ->
                block.content.contains(query, ignoreCase = true)
            }
            success(matchingBlocks.sortedBy { it.pageId })
        }
    }

    override suspend fun deleteBlocksForPage(pageId: Long): Result<Unit> {
        val current = blocks.value.toMutableMap()
        val toRemove = current.values.filter { it.pageId == pageId }.map { it.uuid }
        toRemove.forEach { current.remove(it) }
        blocks.value = current
        return success(Unit)
    }

    override suspend fun clear() {
        blocks.value = emptyMap()
    }
}
