package com.logseq.kmp.repository

import app.cash.sqldelight.db.SqlCursor
import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Property
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Extension functions for converting SQLDelight cursors to domain models
 */

/**
 * Convert SQL cursor to Block model
 */
fun SqlCursor.toBlock(): Block {
    return Block(
        id = getLong(0) ?: 0L, // id
        uuid = getString(1) ?: "", // uuid
        pageId = getLong(2) ?: 0L, // page_id
        parentId = getLong(3), // parent_id
        leftId = getLong(4), // left_id
        content = getString(5) ?: "", // content
        level = getLong(6)?.toInt() ?: 0, // level
        position = getLong(7)?.toInt() ?: 0, // position
        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(getLong(8) ?: 0L), // created_at
        updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(getLong(9) ?: 0L), // updated_at
        properties = getString(10)?.let { parseJsonProperties(it) } ?: emptyMap() // properties
    )
}

/**
 * Convert SQL cursor to Page model
 */
fun SqlCursor.toPage(): Page {
    return Page(
        id = getLong(0) ?: 0L, // id
        uuid = getString(1) ?: "", // uuid
        name = getString(2) ?: "", // name
        namespace = getString(3), // namespace
        filePath = getString(4), // file_path
        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(getLong(5) ?: 0L), // created_at
        updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(getLong(6) ?: 0L), // updated_at
        properties = getString(7)?.let { parseJsonProperties(it) } ?: emptyMap() // properties
    )
}

/**
 * Convert SQL cursor to Property model
 */
fun SqlCursor.toProperty(): Property {
    return Property(
        id = getLong(0) ?: 0L, // id
        blockId = getLong(1) ?: 0L, // block_id
        key = getString(2) ?: "", // key
        value = getString(3) ?: "", // value
        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(getLong(4) ?: 0L) // created_at
    )
}

/**
 * Simple JSON properties parser (basic implementation)
 */
private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

private fun parseJsonProperties(json: String): Map<String, String> {
    if (json.isBlank() || json == "{}") return emptyMap()
    return try {
        jsonParser.decodeFromString<Map<String, String>>(json)
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Simple JSON string conversion (basic implementation)
 */
fun Map<String, String>.toJsonString(): String {
    return jsonParser.encodeToString(this)
}
