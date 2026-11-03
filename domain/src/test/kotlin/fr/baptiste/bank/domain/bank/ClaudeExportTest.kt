package fr.baptiste.bank.domain.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeExportTest {

    @Test
    fun `opaque ids are stable for a salt and hide the internal id`() {
        val a = opaqueId("op", "sel", 42)
        assertEquals(a, opaqueId("op", "sel", 42))
        assertTrue(a != opaqueId("op", "autre", 42))
        assertTrue(a.startsWith("op_") && a.length == 13)
        assertTrue(!a.contains("42"))
    }

    @Test
    fun `labels lose card numbers and person names but keep chosen names`() {
        val labels = exportLabels(
            listOf(
                Triple("PAIEMENT CB 2209 PARIS FNAC CARTE 4970XXXXXXXX1234", null, -4500L),
                Triple("VIR SEPA RECU /DE M JEAN DUPONT /MOTIF REMBOURSEMENT", null, 2000L),
                Triple("VIR SEPA RECU /DE M JEAN DUPONT /MOTIF CINEMA", null, 1200L),
                Triple("VIR SEPA EMIS /A MME MARIE DURAND LOYER", "Loyer", -65000L),
                Triple("VIR SEPA RECU /DE SOCIETE EXEMPLE /MOTIF SALAIRE OCTOBRE 2026", null, 245000L),
                Triple("VIR INSTANTANE EMIS /A PAUL MARTIN", null, -3000L),
            ),
            salaryKeyValue = fr.baptiste.bank.domain.budget.salaryKey("VIR SEPA RECU /DE SOCIETE EXEMPLE /MOTIF SALAIRE AOUT 2026"),
        )
        assertTrue(!labels[0].contains("4970"))
        assertTrue(labels[0].contains("FNAC"))
        assertEquals("Virement reçu · émetteur 1", labels[1])
        assertTrue(labels[2].startsWith("Virement reçu · émetteur"))
        assertEquals("Loyer", labels[3])
        assertEquals("Salaire", labels[4])
        assertEquals("Virement émis · destinataire 1", labels[5])
        assertTrue(labels.none { it.contains("DUPONT") || it.contains("MARTIN") || it.contains("DURAND") })
    }

    @Test
    fun `the plan object is found in a fenced block or in plain text`() {
        val fenced = "Voici le plan (2 actions) :\n```json\n{\"format\":\"bank-plan\",\"actions\":[{\"type\":\"flag\",\"issue\":\"x\"}]}\n```"
        assertEquals("{\"format\":\"bank-plan\",\"actions\":[{\"type\":\"flag\",\"issue\":\"x\"}]}", extractJsonObject(fenced))
        assertEquals("{\"actions\":[]}", extractJsonObject("texte {\"actions\":[]} fin"))
    }
}
