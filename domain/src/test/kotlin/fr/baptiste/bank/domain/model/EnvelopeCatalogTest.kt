package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.bank.envelopeNameKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeCatalogTest {

    @Test
    fun `the catalogue is large and every name is unique`() {
        assertTrue(envelopeCatalog.size >= 140)
        assertEquals(envelopeCatalog.size, envelopeCatalog.map { envelopeNameKey(it.name) }.toSet().size)
    }

    @Test
    fun `a name comes with its category and use`() {
        assertEquals(EnvelopeIdea("Loyer", "Logement", "Logement", IdeaUse.FIXED), catalogIdea("loyer"))
        assertEquals(IdeaUse.RARE, catalogIdea("Coiffeur")?.use)
        assertEquals("Santé", catalogIdea("COIFFEUR")?.category)
        assertEquals(null, catalogIdea("Mon enveloppe à moi"))
    }

    @Test
    fun `search ignores case and accents and covers themes`() {
        assertTrue(searchCatalog("cafe").any { it.name == "Café" })
        assertTrue(searchCatalog("animaux").any { it.name == "Vétérinaire" })
        assertEquals(envelopeCatalog, searchCatalog(""))
    }

    @Test
    fun `the catalogue decides the suggested category first`() {
        assertEquals("Logement", suggestCategory("Loyer"))
        assertEquals("Animaux", suggestCategory("Vétérinaire"))
        // Hors catalogue, les indices restent.
        assertEquals("Transports", suggestCategory("Essence perso"))
    }
}
