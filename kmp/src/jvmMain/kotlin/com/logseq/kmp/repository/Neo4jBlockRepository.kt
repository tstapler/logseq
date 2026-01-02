package com.logseq.kmp.repository

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import org.neo4j.driver.Driver
import org.neo4j.driver.Session
import org.neo4j.driver.Values
import kotlin.Result.Companion.success
import kotlin.Result.Companion.failure

/**
 * Neo4j embedded implementation of BlockRepository.
 * Uses Neo4j's native graph database with Cypher queries.
 */
class Neo4jBlockRepository(private val driver: Driver) : BlockRepository {

    override fun getBlockByUuid(uuid: String): Flow<Result<Block?>> = flow {
        try {
            driver.session().use { session ->
                val result = session.run(
                    """
                    MATCH (b:Block {uuid: $uuid})
                    RETURN b.id, b.uuid, b.pageId, b.parentId, b.leftId,
                           b.content, b.level, b.position, b.createdAt, b.updatedAt, b.properties
                    """,
                    Values.parameters("uuid", uuid)
                )

                val block = if (result.hasNext()) {
                    result.single().toBlock()
                } else null

                emit(success(block))
            }
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockChildren(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            driver.session().use { session ->
                val result = session.run(
                    """
                    MATCH (parent:Block {uuid: $blockUuid})<-[:PARENT_OF]-(child:Block)
                    RETURN child.id, child.uuid, child.pageId, child.parentId, child.leftId,
                           child.content, child.level, child.position, child.createdAt, child.updatedAt, child.properties
                    ORDER BY child.position
                    """,
                    Values.parameters("blockUuid", blockUuid)
                )

                val blocks = result.list { record -> record.toBlock() }
                emit(success(blocks))
            }
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockHierarchy(rootUuid: String): Flow<Result<List<BlockWithDepth>>> = flow {
        try {
            driver.session().use { session ->
                val result = session.run(
                    """
                    MATCH path = (root:Block {uuid: $rootUuid})<-[:PARENT_OF*0..]-(descendant:Block)
                    WITH descendant, length(path) - 1 as depth
                    RETURN descendant.id, descendant.uuid, descendant.pageId, descendant.parentId, descendant.leftId,
                           descendant.content, descendant.level, descendant.position, descendant.createdAt, descendant.updatedAt, descendant.properties, depth
                    ORDER BY depth, descendant.position
                    """,
                    Values.parameters("rootUuid", rootUuid)
                )

                val hierarchy = result.list { record ->
                    BlockWithDepth(record.toBlock(), record.get("depth").asInt())
                }

                emit(success(hierarchy))
            }
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockAncestors(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            driver.session().use { session ->
                val result = session.run(
                    """
                    MATCH (child:Block {uuid: $blockUuid})<-[:PARENT_OF*1..]-(ancestor:Block)
                    WITH ancestor, length(path) as distance
                    RETURN ancestor.id, ancestor.uuid, ancestor.pageId, ancestor.parentId, ancestor.leftId,
                           ancestor.content, ancestor.level, ancestor.position, ancestor.createdAt, ancestor.updatedAt, ancestor.properties
                    ORDER BY distance DESC
                    """,
                    Values.parameters("blockUuid", blockUuid)
                )

                val ancestors = result.list { record -> record.toBlock() }
                emit(success(ancestors))
            }
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockParent(blockUuid: String): Flow<Result<Block?>> = flow {
        try {
            driver.session().use { session ->
                val result = session.run(
                    """
                    MATCH (child:Block {uuid: $blockUuid})<-[:PARENT_OF]-(parent:Block)
                    RETURN parent.id, parent.uuid, parent.pageId, parent.parentId, parent.leftId,
                           parent.content, parent.level, parent.position, parent.createdAt, parent.updatedAt, parent.properties
                    """,
                    Values.parameters("blockUuid", blockUuid)
                )

                val parent = if (result.hasNext()) {
                    result.single().toBlock()
                } else null

                emit(success(parent))
            }
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override fun getBlockSiblings(blockUuid: String): Flow<Result<List<Block>>> = flow {
        try {
            driver.session().use { session ->
                val result = session.run(
                    """
                    MATCH (block:Block {uuid: $blockUuid})<-[:PARENT_OF]-(parent:Block)-[:PARENT_OF]->(sibling:Block)
                    WHERE sibling.uuid <> $blockUuid
                    RETURN sibling.id, sibling.uuid, sibling.pageId, sibling.parentId, sibling.leftId,
                           sibling.content, sibling.level, sibling.position, sibling.createdAt, sibling.updatedAt, sibling.properties
                    ORDER BY sibling.position
                    """,
                    Values.parameters("blockUuid", blockUuid)
                )

                val siblings = result.list { record -> record.toBlock() }
                emit(success(siblings))
            }
        } catch (e: Exception) {
            emit(failure(e))
        }
    }

    override suspend fun saveBlock(block: Block): Result<Unit> {
        return try {
            driver.session().use { session ->
                session.run(
                    """
                    MERGE (b:Block {uuid: $uuid})
                    SET b.id = $id, b.pageId = $pageId, b.parentId = $parentId, b.leftId = $leftId,
                        b.content = $content, b.level = $level, b.position = $position,
                        b.createdAt = $createdAt, b.updatedAt = $updatedAt, b.properties = $properties
                    """,
                    Values.parameters(
                        "uuid", block.uuid,
                        "id", block.id,
                        "pageId", block.pageId,
                        "parentId", block.parentId,
                        "leftId", block.leftId,
                        "content", block.content,
                        "level", block.level,
                        "position", block.position,
                        "createdAt", block.createdAt.toEpochMilliseconds(),
                        "updatedAt", block.updatedAt.toEpochMilliseconds(),
                        "properties", block.properties.toString()
                    )
                )
            }
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
        return try {
            driver.session().use { session ->
                val query = if (deleteChildren) {
                    """
                    MATCH path = (block:Block {uuid: $blockUuid})<-[:PARENT_OF*0..]-(descendant:Block)
                    DETACH DELETE descendant
                    """
                } else {
                    """
                    MATCH (block:Block {uuid: $blockUuid})<-[:PARENT_OF]-(child:Block)
                    MATCH (block)<-[:PARENT_OF]-(parent:Block)
                    CREATE (parent)-[:PARENT_OF]->(child)
                    DETACH DELETE block
                    """
                }

                session.run(query, Values.parameters("blockUuid", blockUuid))
            }
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }

    override suspend fun moveBlock(blockUuid: String, newParentUuid: String?, newPosition: Int): Result<Unit> {
        return try {
            driver.session().use { session ->
                session.run(
                    """
                    MATCH (block:Block {uuid: $blockUuid})
                    OPTIONAL MATCH (block)<-[:PARENT_OF]-(oldParent:Block)
                    OPTIONAL MATCH (newParent:Block {uuid: $newParentUuid})
                    // Remove old relationship
                    CALL {
                        WITH oldParent, block
                        OPTIONAL MATCH (oldParent)-[r:PARENT_OF]->(block)
                        DELETE r
                    }
                    // Add new relationship
                    CALL {
                        WITH newParent, block
                        WHERE newParent IS NOT NULL
                        CREATE (newParent)-[:PARENT_OF]->(block)
                    }
                    SET block.position = $newPosition
                    """,
                    Values.parameters(
                        "blockUuid", blockUuid,
                        "newParentUuid", newParentUuid,
                        "newPosition", newPosition
                    )
                )
            }
            success(Unit)
        } catch (e: Exception) {
            failure(e)
        }
    }
}

/**
 * Extension function to convert Neo4j Record to Block
 */
private fun org.neo4j.driver.Record.toBlock(): Block {
    val node = get("descendant").takeIf { it != null } ?: get("child").takeIf { it != null }
        ?: get("ancestor").takeIf { it != null } ?: get("parent").takeIf { it != null }
        ?: get("block").takeIf { it != null } ?: get("b").takeIf { it != null }
        ?: get("sibling").takeIf { it != null } ?: throw IllegalStateException("No block node found in record")

    return Block(
        id = node.get("id").asLong(),
        uuid = node.get("uuid").asString(),
        pageId = node.get("pageId").asLong(),
        parentId = node.get("parentId").takeIf { !it.isNull }?.asLong(),
        leftId = node.get("leftId").takeIf { !it.isNull }?.asLong(),
        content = node.get("content").asString(),
        level = node.get("level").asInt(),
        position = node.get("position").asInt(),
        createdAt = Instant.fromEpochMilliseconds(node.get("createdAt").asLong()),
        updatedAt = Instant.fromEpochMilliseconds(node.get("updatedAt").asLong()),
        properties = node.get("properties").takeIf { !it.isNull }?.asString()?.let { parseJsonProperties(it) } ?: emptyMap()
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
<parameter name="filePath">kmp/src/jvmMain/kotlin/com/logseq/kmp/repository/Neo4jBlockRepository.kt