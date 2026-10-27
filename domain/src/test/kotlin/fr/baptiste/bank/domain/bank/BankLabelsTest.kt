package fr.baptiste.bank.domain.bank

import org.junit.Assert.assertEquals
import org.junit.Test

class BankLabelsTest {

    @Test
    fun `removes card payment prefix, short date and masked card number`() {
        assertEquals(
            "Paris Carrefour City",
            cleanBankLabel("PAIEMENT CB 2410 PARIS CARREFOUR CITY CARTE 4970XXXXXXXX1234"),
        )
        assertEquals("Boulangerie Paul", cleanBankLabel("PAIEMENT PSC 0311 BOULANGERIE PAUL"))
        assertEquals("Total Access", cleanBankLabel("CB 2510 TOTAL ACCESS 4970XXXXXXXX1234"))
    }

    @Test
    fun `removes direct debit and transfer prefixes`() {
        assertEquals("Free Mobile", cleanBankLabel("PRLV SEPA FREE MOBILE"))
        assertEquals("M Jean Dupont", cleanBankLabel("VIR SEPA RECU DE M JEAN DUPONT"))
        assertEquals("Loyer Octobre", cleanBankLabel("VIR INST EMIS LOYER OCTOBRE"))
    }

    @Test
    fun `keeps the nature of a cash withdrawal`() {
        assertEquals("Retrait Paris", cleanBankLabel("RETRAIT DAB 2410 PARIS CARTE 4970XXXXXXXX1234"))
    }

    @Test
    fun `falls back to the raw label when nothing is left`() {
        assertEquals("Prlv Sepa", cleanBankLabel("PRLV SEPA"))
        assertEquals("Frais", cleanBankLabel("  frais  "))
    }

    @Test
    fun `normalizes accents, case and spaces for comparison`() {
        assertEquals("CAFE DE LA GARE", normalizeLabel("  Café   de la gare "))
    }

    @Test
    fun `suggests the first three meaningful words as rule keyword`() {
        assertEquals("Paris Carrefour City", suggestedRuleKeyword("Paris Carrefour City 75011 Express"))
        assertEquals("Free Mobile", suggestedRuleKeyword("Free Mobile"))
    }

    @Test
    fun `converts bank decimal amounts to cents`() {
        assertEquals(1230L, decimalToCents("12.3"))
        assertEquals(123456L, decimalToCents("1234.56"))
        assertEquals(1L, decimalToCents("0.005"))
        assertEquals(null, decimalToCents("douze"))
    }
}
