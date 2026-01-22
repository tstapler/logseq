package com.logseq.kmp.model

/**
 * Cursor state for editor.
 */
data class CursorState(
    val blockId: String? = null,
    val position: Int = 0
)