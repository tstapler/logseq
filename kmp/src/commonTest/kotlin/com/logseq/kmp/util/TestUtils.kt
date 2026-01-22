package com.logseq.kmp.util

import com.logseq.kmp.model.Block
import kotlinx.datetime.Clock

object TestUtils {
    // Helper to pad UUID segments
    private fun pad(s: String, length: Int): String = s.padStart(length, '0')

    fun createBlock(
        id: Long = 1,
        // Generate valid UUID based on ID: 00000000-0000-0000-0000-000000000001
        uuid: String = "00000000-0000-0000-0000-${pad(id.toString(), 12)}",
        pageId: Long = 1,
        parentId: Long? = null,
        content: String = "",
        level: Int = 0,
        position: Int = 0
    ): Block {
        val now = Clock.System.now()
        return Block(
            id = id,
            uuid = uuid,
            pageId = pageId,
            parentId = parentId,
            leftId = null,
            content = content,
            level = level,
            position = position,
            createdAt = now,
            updatedAt = now,
            properties = emptyMap()
        )
    }
}
