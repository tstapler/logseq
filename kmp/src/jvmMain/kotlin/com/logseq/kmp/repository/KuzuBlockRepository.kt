package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import java.sql.Connection
import java.sql.DriverManager
import kotlin.Result.Companion.success
import kotlin.Result.Companion.failure

/**
 * Kuzu implementation of all repository interfaces.
 * Uses Kuzu's native graph database capabilities for optimal performance.
 */
class KuzuBlockRepository(private val connection: Connection) : BlockRepository {

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> = flow {
        try {
            val stmt = connection.prepareStatement("""
                MATCH (b:Block {uuid: ?})
                RETURN b.id, b.uuid, b.page_id, b.parent_id, b.left_id,
                       b.content, b.level, b.position, b.created_at, b.updated_at, b.properties
            """)
            stmt.setString(1, uuid)

            val result = stmt.executeQuery()
            val block = if (result.next()) {
                result.toBlock()
            } else null

            emit(success(block))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val stmt = connection.prepareStatement("""
                MATCH (parent:Block {uuid: ?})<-[:PARENT_OF]-(child:Block)
                RETURN child.id, child.uuid, child.page_id, child.parent_id, child.left_id,
                       child.content, child.level, child.position, child.created_at, child.updated_at, child.properties
                ORDER BY child.position
            """)
            stmt.setString(1, blockUuid)

            val result = stmt.executeQuery()
            val blocks = mutableListOf<Block>()

            while (result.next()) {
                blocks.add(result.toBlock())
            }

            emit(success(blocks))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> = flow {
        try {
            // Use Cypher's variable-length path matching for hierarchy traversal
            val stmt = connection.prepareStatement("""
                MATCH path = (root:Block {uuid: ?})<-[:PARENT_OF*0..]-(descendant:Block)
                WITH descendant, length(path) - 1 as depth
                RETURN descendant.id, descendant.uuid, descendant.page_id, descendant.parent_id, descendant.left_id,
                       descendant.content, descendant.level, descendant.position, descendant.created_at, descendant.updated_at, descendant.properties, depth
                ORDER BY depth, descendant.position
            """)
            stmt.setString(1, rootUuid)

            val result = stmt.executeQuery()
            val hierarchy = mutableListOf<BlockWithDepth>()

            while (result.next()) {
                hierarchy.add(BlockWithDepth(result.toBlock(), result.getInt("depth")))
            }

            emit(success(hierarchy))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val stmt = connection.prepareStatement("""
                MATCH (child:Block {uuid: ?})<-[:PARENT_OF*1..]-(ancestor:Block)
                RETURN ancestor.id, ancestor.uuid, ancestor.page_id, ancestor.parent_id, ancestor.left_id,
                       ancestor.content, ancestor.level, ancestor.position, ancestor.created_at, ancestor.updated_at, ancestor.properties
                ORDER BY length(path) DESC
            """)
            stmt.setString(1, blockUuid)

            val result = stmt.executeQuery()
            val ancestors = mutableListOf<Block>()

            while (result.next()) {
                ancestors.add(result.toBlock())
            }

            emit(success(ancestors))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> = flow {
        try {
            val stmt = connection.prepareStatement("""
                MATCH (child:Block {uuid: ?})<-[:PARENT_OF]-(parent:Block)
                RETURN parent.id, parent.uuid, parent.page_id, parent.parent_id, parent.left_id,
                       parent.content, parent.level, parent.position, parent.created_at, parent.updated_at, parent.properties
            """)
            stmt.setString(1, blockUuid)

            val result = stmt.executeQuery()
            val parent = if (result.next()) {
                result.toBlock()
            } else null

            emit(success(parent))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            val stmt = connection.prepareStatement("""
                MATCH (block:Block {uuid: ?})<-[:PARENT_OF]-(parent:Block)-[:PARENT_OF]->(sibling:Block)
                WHERE sibling.uuid <> ?
                RETURN sibling.id, sibling.uuid, sibling.page_id, sibling.parent_id, sibling.left_id,
                       sibling.content, sibling.level, sibling.position, sibling.created_at, sibling.updated_at, sibling.properties
                ORDER BY sibling.position
            """)
            stmt.setString(1, blockUuid)
            stmt.setString(2, blockUuid)

            val result = stmt.executeQuery()
            val siblings = mutableListOf<Block>()

            while (result.next()) {
                siblings.add(result.toBlock())
            }

            emit(success(siblings))
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        return try {
            val stmt = connection.prepareStatement("""
                MERGE (b:Block {uuid: ?})
                SET b.id = ?, b.page_id = ?, b.parent_id = ?, b.left_id = ?,
                    b.content = ?, b.level = ?, b.position = ?, b.created_at = ?, b.updated_at = ?, b.properties = ?
            """)

            stmt.setString(1, block.uuid)
            stmt.setLong(2, block.id)
            stmt.setLong(3, block.pageId)
            block.parentId?.let { stmt.setLong(4, it) } ?: stmt.setObject(4, null)
            block.leftId?.let { stmt.setLong(5, it) } ?: stmt.setObject(5, null)
            stmt.setString(6, block.content)
            stmt.setInt(7, block.level)
            stmt.setInt(8, block.position)
            stmt.setLong(9, block.createdAt.toEpochMilliseconds())
            stmt.setLong(10, block.updatedAt.toEpochMilliseconds())
            stmt.setString(11, block.properties.toString()) // JSON

            stmt.executeUpdate()

            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        return try {
            val query = if (deleteChildren) {
                // Delete block and all descendants
                """
                MATCH path = (block:Block {uuid: ?})<-[:PARENT_OF*0..]-(descendant:Block)
                DETACH DELETE descendant
                """
            } else {
                // Delete block and re-parent children
                """
                MATCH (block:Block {uuid: ?})<-[:PARENT_OF]-(child:Block)
                MATCH (block)<-[:PARENT_OF]-(parent:Block)
                CREATE (parent)-[:PARENT_OF]->(child)
                DETACH DELETE block
                """
            }

            val stmt = connection.prepareStatement(query)
            stmt.setString(1, blockUuid)
            stmt.executeUpdate()

            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int): Result<Unit> {
        return try {
            val stmt = connection.prepareStatement("""
                MATCH (block:Block {uuid: ?})
                OPTIONAL MATCH (block)<-[:PARENT_OF]-(oldParent:Block)
                OPTIONAL MATCH (newParent:Block {uuid: ?})
                // Remove old parent relationship
                CALL {
                    WITH oldParent, block
                    OPTIONAL MATCH (oldParent)-[r:PARENT_OF]->(block)
                    DELETE r
                }
                // Add new parent relationship
                CALL {
                    WITH newParent, block
                    WHERE newParent IS NOT NULL
                    CREATE (newParent)-[:PARENT_OF]->(block)
                }
                SET block.position = ?
            """)

            stmt.setString(1, blockUuid)
            newParentUuid?.let { stmt.setString(2, it) } ?: stmt.setObject(2, null)
            stmt.setInt(3, newPosition)

            stmt.executeUpdate()
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }
}

/**
 * Extension function to convert ResultSet to Block
 */
private fun java.sql.ResultSet.toBlock(): Block {
    return Block(
        id = getLong("id"),
        uuid = getString("uuid"),
        pageId = getLong("page_id"),
        parentId = getLong("parent_id").takeIf { !wasNull() },
        leftId = getLong("left_id").takeIf { !wasNull() },
        content = getString("content"),
        level = getInt("level"),
        position = getInt("position"),
        createdAt = Instant.fromEpochMilliseconds(getLong("created_at")),
        updatedAt = Instant.fromEpochMilliseconds(getLong("updated_at")),
        properties = getString("properties")?.let { parseJsonProperties(it) } ?: emptyMap()
    )
}

/**
 * Simple JSON property parsing (simplified for demo)
 */
private fun parseJsonProperties(json: String): Map<String, String> {
    return try {
        json.removeSurrounding("{", "}")
            .split(",")
            .associate { pair ->
                val (key, value) = pair.split(":", limit = 2)
                key.removeSurrounding("\"") to value.removeSurrounding("\"")
            }
    } catch (e: Exception) {
        emptyMap()
    }
}</content>
<parameter name="filePath">kmp/src/jvmMain/kotlin/com/logseq/kmp/repository/KuzuBlockRepository.kt