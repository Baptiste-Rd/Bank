package fr.baptiste.bank.domain.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssistantTest {

    @Test
    fun `anonymization removes iban card mandate and long numbers but keeps the merchant`() {
        assertEquals("Paris Carrefour City", anonymizeLabel("Paris Carrefour City 4970XXXXXXXX1234"))
        assertEquals("Prlv Free Mobile", anonymizeLabel("Prlv Free Mobile RUM FM-2231-98 ICS FR12ZZZ123456"))
        assertEquals("Vir Loyer", anonymizeLabel("Vir FR76 3000 6000 0112 3456 7890 189 Loyer"))
        assertEquals("Contact", anonymizeLabel("Contact jean.dupont@mail.fr"))
        assertEquals("(libellé masqué)", anonymizeLabel("123456789"))
    }

    @Test
    fun `prompt carries the three phases envelopes and anonymized operations only`() {
        val prompt = sortingPrompt(
            listOf(AssistantOperation(1, LocalDate.of(2026, 10, 27), -1999, "Prlv Free Mobile RUM 778899")),
            listOf(AssistantEnvelope("Courses", "quotidien", "Nourriture"), AssistantEnvelope("Abonnements", "charges fixes")),
        )
        assertTrue(prompt.contains("Phase 1"))
        assertTrue(prompt.contains("Phase 3"))
        assertTrue(prompt.contains("- Courses (quotidien, groupe Nourriture)"))
        assertTrue(prompt.contains("1 | 27/10/2026 | −19,99 € | Prlv Free Mobile"))
        assertFalse(prompt.contains("778899"))
    }

    @Test
    fun `json is read from a fenced block or from the brackets`() {
        val fenced = "Analyse...\n```json\n[{\"n\": 1}]\n```\nVoilà."
        assertEquals("[{\"n\": 1}]", extractJsonArray(fenced))
        assertEquals("[{\"n\": 2}]", extractJsonArray("Résultat : [{\"n\": 2}] fin"))
        assertEquals(null, extractJsonArray("Pas de tableau"))
    }

    private fun proposal(envelope: String, isNew: Boolean = false, family: String? = null) =
        AssistantProposal(1, "Nom", envelope, isNew, family, null, "haute", null)

    @Test
    fun `targets match existing names without case or accents`() {
        val mine = listOf("Courses", "Épargne")
        assertEquals(ProposalTarget.Existing("Courses"), proposal("courses", isNew = true).target(mine))
        assertEquals(ProposalTarget.Existing("Épargne"), proposal("Epargne").target(mine))
        assertEquals(ProposalTarget.OutOfBudget, proposal("Hors budget").target(mine))
        assertEquals(ProposalTarget.New("Téléphone", "charges fixes"), proposal("Téléphone", true, "Charges fixes").target(mine))
        assertEquals(ProposalTarget.New("Animaux", "quotidien"), proposal("Animaux", true, "inconnue").target(mine))
    }

    @Test
    fun `amounts are written with a real minus sign`() {
        assertEquals("−19,99 €", assistantAmount(-1999))
        assertEquals("+2400,00 €", assistantAmount(240000))
    }

    @Test
    fun `a transfer to one of my accounts is recognized by name`() {
        val accounts = listOf("Livret A", "Trade Republic")
        assertEquals(ProposalTarget.Account("Livret A"), proposal("COMPTE: livret a").target(emptyList(), accounts))
        assertEquals(ProposalTarget.OutOfBudget, proposal("COMPTE: PEA").target(emptyList(), accounts))
        val prompt = sortingPrompt(emptyList(), emptyList(), accounts)
        assertTrue(prompt.contains("Mes comptes d'épargne : Livret A, Trade Republic."))
        assertTrue(prompt.contains("COMPTE: <nom du compte>"))
    }
}
