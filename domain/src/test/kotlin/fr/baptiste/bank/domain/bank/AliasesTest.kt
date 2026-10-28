package fr.baptiste.bank.domain.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasesTest {

    private val aliases = listOf(
        LabelAlias(1, "free", "Free"),
        LabelAlias(2, "Free Mobile", "Forfait mobile"),
        LabelAlias(3, "", "Vide"),
    )

    @Test
    fun `alias applies without case or accents and prefers the longest pattern`() {
        assertEquals("Forfait mobile", aliases.aliasFor("PRLV SEPA FREE MOBILE 0612")?.alias)
        assertEquals("Free", aliases.aliasFor("Free Telecom")?.alias)
        assertEquals(null, aliases.aliasFor("Boulangerie"))
    }

    @Test
    fun `suggestion follows the most frequent envelope for the same keyword`() {
        val history = listOf(
            SortedLabel("Paris Carrefour City 75011", "Bar"),
            SortedLabel("Paris Carrefour City 75020", "Courses"),
            SortedLabel("PARIS CARREFOUR CITY 1234", "Courses"),
            SortedLabel("Boulangerie Paul", "Sorties"),
        )
        assertEquals("Courses", suggestEnvelope("Paris Carrefour City 75003", history))
        assertEquals("Sorties", suggestEnvelope("Boulangerie Paul", history))
        assertEquals(null, suggestEnvelope("Station Total", history))
    }

    @Test
    fun `on a tie the most recent choice wins`() {
        val history = listOf(SortedLabel("Uber Eats", "Sorties"), SortedLabel("Uber Eats", "Courses"))
        assertEquals("Sorties", suggestEnvelope("Uber Eats", history))
    }

    @Test
    fun `envelope names compare without case accents or spaces`() {
        assertTrue(isNameTaken(" bar ", listOf("Bar", "Courses")))
        assertTrue(isNameTaken("Epargne", listOf("Épargne")))
        assertFalse(isNameTaken("Bars", listOf("Bar")))
        assertFalse(isNameTaken("   ", listOf("Bar")))
    }
}
