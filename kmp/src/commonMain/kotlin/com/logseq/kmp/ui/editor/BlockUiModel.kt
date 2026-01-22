package com.logseq.kmp.ui.editor

import com.logseq.kmp.model.Block

/**
 * UI State model for a block.
 * Optimized for rendering in the LazyColumn.
 */
data class BlockUiModel(
    val id: Long,
    val uuid: String,
    val content: String,
    val level: Int,
    val isFocused: Boolean = false,
    val isSelected: Boolean = false,
    val isCollapsed: Boolean = false,
    val hasChildren: Boolean = false
) {
    companion object {
        fun fromDomain(block: Block, isFocused: Boolean = false, hasChildren: Boolean = false): BlockUiModel {
            return BlockUiModel(
                id = block.id,
                uuid = block.uuid,
                content = block.content,
                level = block.level,
                isFocused = isFocused,
                hasChildren = hasChildren
            )
        }
    }
}
