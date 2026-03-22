package com.logseq.kmp.ui.fixtures

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

object TestFixtures {

    private fun uuid(index: Int): String {
        val hex = index.toString(16).padStart(12, '0')
        return "00000000-0000-0000-0000-$hex"
    }

    fun sampleJournalPages(): List<Page> {
        val now = Clock.System.now()
        return (1..3).map { i ->
            Page(
                id = i.toLong(),
                uuid = uuid(i),
                name = "2026-03-0$i",
                createdAt = now,
                updatedAt = now,
                isJournal = true,
                journalDate = LocalDate(2026, 3, i)
            )
        }
    }

    fun sampleBlocksForPage(pageId: Long, startId: Long = pageId * 100): List<Block> {
        val now = Clock.System.now()
        val baseId = startId.toInt()
        return listOf(
            Block(
                id = baseId.toLong(),
                uuid = uuid(baseId),
                pageId = pageId,
                content = "**Bold text** in journal entry",
                level = 0,
                position = 0,
                createdAt = now,
                updatedAt = now
            ),
            Block(
                id = (baseId + 1).toLong(),
                uuid = uuid(baseId + 1),
                pageId = pageId,
                content = "TODO A task to complete",
                level = 0,
                position = 1,
                createdAt = now,
                updatedAt = now
            ),
            Block(
                id = (baseId + 2).toLong(),
                uuid = uuid(baseId + 2),
                pageId = pageId,
                content = "See also [[Another Page]]",
                level = 0,
                position = 2,
                createdAt = now,
                updatedAt = now
            ),
            Block(
                id = (baseId + 3).toLong(),
                uuid = uuid(baseId + 3),
                pageId = pageId,
                parentId = baseId.toLong(),
                content = "Child block content",
                level = 1,
                position = 0,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun samplePage(): Page {
        val now = Clock.System.now()
        return Page(
            id = 999,
            uuid = uuid(999),
            name = "Test Page",
            createdAt = now,
            updatedAt = now,
            isJournal = false
        )
    }

    fun samplePageBlocks(pageId: Long = 999): List<Block> {
        val now = Clock.System.now()
        return listOf(
            Block(
                id = 9001,
                uuid = uuid(9001),
                pageId = pageId,
                content = "Introduction paragraph with regular text",
                level = 0,
                position = 0,
                createdAt = now,
                updatedAt = now
            ),
            Block(
                id = 9002,
                uuid = uuid(9002),
                pageId = pageId,
                content = "Second block with **bold** and *italic*",
                level = 0,
                position = 1,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun sampleJournalData(): Map<Page, List<Block>> {
        val pages = sampleJournalPages()
        return pages.associateWith { page -> sampleBlocksForPage(page.id) }
    }
}
