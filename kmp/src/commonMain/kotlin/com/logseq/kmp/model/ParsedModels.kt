package com.logseq.kmp.model

/**
 * Intermediate representation of a parsed Markdown page.
 * This structure mirrors the file content before it is normalized into the database schema.
 */
data class ParsedPage(
    val title: String?,
    val properties: Map<String, String>,
    val blocks: List<ParsedBlock>
)

/**
 * Intermediate representation of a parsed Markdown block.
 */
data class ParsedBlock(
    val content: String,
    val properties: Map<String, String>,
    val level: Int,
    // Children are optional here depending on whether the parser produces a flat list or tree.
    // For now, we'll assume the parser might preserve hierarchy if convenient, 
    // but the main usage in GraphLoader might just iterate a flat list.
    // Let's include it for flexibility.
    val children: List<ParsedBlock> = emptyList(),
    // Extracted references (WikiLinks [[...]] and Block Refs ((...)))
    val references: List<String> = emptyList(),
    // Metadata
    val scheduled: String? = null,
    val deadline: String? = null
)
