package com.logseq.kmp.outliner

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilsTest {
    @Test
    fun testJournalUtils() {
        val date = LocalDate(2026, 1, 4)
        val name = JournalUtils.formatDateForJournal(date)
        assertEquals("2026_01_04", name)
        assertTrue(JournalUtils.isJournalName(name))
        assertEquals(date, JournalUtils.parseJournalDate(name))
        assertFalse(JournalUtils.isJournalName("Not a journal"))
    }

    @Test
    fun testNamespaceUtils() {
        val name = "Parent/Child/GrandChild"
        assertEquals("Parent/Child", NamespaceUtils.getNamespace(name))
        assertEquals("GrandChild", NamespaceUtils.getShortName(name))
        
        val parents = NamespaceUtils.getParentPages(name)
        assertEquals(2, parents.size)
        assertEquals("Parent", parents[0])
        assertEquals("Parent/Child", parents[1])
    }
}
