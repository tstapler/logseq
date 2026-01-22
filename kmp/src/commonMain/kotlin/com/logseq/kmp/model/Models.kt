package com.logseq.kmp.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Security validation for input data
 */
object Validation {
    private const val MAX_STRING_LENGTH = 10000
    private const val MAX_NAME_LENGTH = 255
    private const val MAX_CONTENT_LENGTH = 100000

    fun validateString(input: String?, maxLength: Int = MAX_STRING_LENGTH, allowWhitespace: Boolean = false): String {
        require(input != null) { "Input cannot be null" }
        require(input.length <= maxLength) { "Input exceeds maximum length of $maxLength" }
        require(!input.contains('\u0000')) { "Input contains null bytes" }
        if (!allowWhitespace) {
            require(!input.any { it.code in 0x00..0x1F || it.code in 0x80..0x9F }) { "Input contains control characters" }
        } else {
            require(!input.any { (it.code in 0x00..0x1F || it.code in 0x80..0x9F) && it != '\n' && it != '\r' && it != '\t' }) { "Input contains restricted control characters" }
        }
        return input.trim()
    }

    fun validateName(name: String?): String {
        val validated = validateString(name, MAX_NAME_LENGTH, allowWhitespace = false)
        require(validated.isNotBlank()) { "Name cannot be blank" }
        require(!validated.contains("..")) { "Name contains directory traversal patterns" }
        require(!validated.contains("/")) { "Name contains path separators" }
        // Use Char check for backslash to avoid string escaping issues in some compilers
        require(!validated.contains('\\')) { "Name contains backslashes" }
        return validated
    }

    fun validateContent(content: String?): String {
        return validateString(content, MAX_CONTENT_LENGTH, allowWhitespace = true)
    }

    fun validateId(id: Long): Long {
        require(id > 0) { "ID must be positive" }
        return id
    }

    fun validateUuid(uuid: String?): String {
        val validated = validateString(uuid, 36)
        require(validated.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))) {
            "Invalid UUID format"
        }
        return validated
    }
}

data class Page(
    val id: Long,
    val uuid: String,
    val name: String,
    val namespace: String? = null,
    val filePath: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val properties: Map<String, String> = emptyMap(),
    val isFavorite: Boolean = false,
    val isJournal: Boolean = false,
    val journalDate: LocalDate? = null
) {
    init {
        Validation.validateId(id)
        Validation.validateUuid(uuid)
        Validation.validateName(name)
        namespace?.let { Validation.validateName(it) }
        filePath?.let { Validation.validateContent(it) }
        properties.forEach { (key, value) ->
            Validation.validateName(key)
            Validation.validateContent(value)
        }
    }
}

data class Block(
    val id: Long,
    val uuid: String,
    val pageId: Long,
    val parentId: Long? = null,
    val leftId: Long? = null,
    val content: String,
    val level: Int = 0,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val properties: Map<String, String> = emptyMap()
) {
    init {
        Validation.validateId(id)
        Validation.validateUuid(uuid)
        Validation.validateId(pageId)
        parentId?.let { Validation.validateId(it) }
        leftId?.let { Validation.validateId(it) }
        Validation.validateContent(content)
        require(level >= 0) { "Level must be non-negative" }
        require(position >= 0) { "Position must be non-negative" }
        properties.forEach { (key, value) ->
            Validation.validateName(key)
            Validation.validateContent(value)
        }
    }
}

data class Property(
    val id: Long,
    val blockId: Long,
    val key: String,
    val value: String,
    val createdAt: Instant
) {
    init {
        Validation.validateId(id)
        Validation.validateId(blockId)
        Validation.validateName(key)
        Validation.validateContent(value)
    }
}

enum class NotificationType {
    INFO, WARNING, ERROR, SUCCESS
}

data class Notification(
    val id: String,
    val content: String,
    val type: NotificationType = NotificationType.INFO,
    val timestamp: Instant,
    val timeout: Long? = 3000
) {
    init {
        Validation.validateContent(content)
        Validation.validateUuid(id)
    }
}
