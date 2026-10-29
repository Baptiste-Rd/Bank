package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.DEFAULT_CATEGORY
import fr.baptiste.bank.domain.model.categoryChoices
import fr.baptiste.bank.domain.model.storedCategory
import fr.baptiste.bank.domain.model.suggestCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriesTest {

    @Test
    fun `choices keep the starting list, add mine once and end with Autres`() {
        val choices = categoryChoices(listOf("Animaux", "nourriture", "animaux ", "Enfants"))
        assertEquals("Nourriture", choices.first())
        assertEquals(DEFAULT_CATEGORY, choices.last())
        assertEquals(1, choices.count { it.equals("Animaux", ignoreCase = true) })
        assertEquals(1, choices.count { it.equals("Nourriture", ignoreCase = true) })
        assertEquals(listOf("Animaux", "Enfants"), choices.dropLast(1).takeLast(2))
    }

    @Test
    fun `Autres is stored as no category`() {
        assertEquals(null, storedCategory("autres"))
        assertEquals(null, storedCategory("  "))
        assertEquals("Loisirs", storedCategory(" Loisirs "))
    }

    @Test
    fun `a category is suggested from the start of a word`() {
        assertEquals("Transports", suggestCategory("Essence"))
        assertEquals("Nourriture", suggestCategory("Courses"))
        assertEquals("Cadeaux", suggestCategory("Cadeaux de Noël"))
        assertEquals("Logement", suggestCategory("Électricité"))
        assertEquals(null, suggestCategory("Divers"))
    }
}
