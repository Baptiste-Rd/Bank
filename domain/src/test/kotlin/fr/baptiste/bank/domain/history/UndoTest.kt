package fr.baptiste.bank.domain.history

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoTest {

    @Test
    fun `an older change is blocked while a newer one touches the same thing`() {
        // Plus récente d'abord : 3 déplace Courses, 2 range une opération, 1 modifie Courses.
        val entries = listOf(
            HistoryEntry(3, undone = false, touched = setOf("env:1", "cat:nourriture")),
            HistoryEntry(2, undone = false, touched = setOf("op:9")),
            HistoryEntry(1, undone = false, touched = setOf("env:1")),
        )
        assertEquals(setOf(1L), blockedEntries(entries))
        // La plus récente annulée : la plus ancienne se débloque.
        assertEquals(emptySet<Long>(), blockedEntries(listOf(entries[0].copy(undone = true)) + entries.drop(1)))
    }

    @Test
    fun `independent changes all stay undoable`() {
        val entries = listOf(
            HistoryEntry(2, undone = false, touched = setOf("op:1")),
            HistoryEntry(1, undone = false, touched = setOf("op:2")),
        )
        assertEquals(emptySet<Long>(), blockedEntries(entries))
    }
}
