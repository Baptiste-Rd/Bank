package fr.baptiste.bank.domain.bank

import fr.baptiste.bank.domain.model.RuleMatchType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SortingTest {

    private val rules = listOf(
        LabelRule(1, RuleMatchType.CONTAINS, "carrefour", envelopeId = 10),
        LabelRule(2, RuleMatchType.CONTAINS, "Carrefour Market", envelopeId = 11),
        LabelRule(3, RuleMatchType.STARTS_WITH, "Total", envelopeId = 12),
        LabelRule(4, RuleMatchType.CONTAINS, "café", envelopeId = 13, priority = -1),
    )

    @Test
    fun `matches without case or accents and prefers the most precise pattern`() {
        assertEquals(1L, rules.matching("Paris Carrefour City")?.id)
        assertEquals(2L, rules.matching("Carrefour Market Bastille")?.id)
        assertEquals(3L, rules.matching("Total Access Lyon")?.id)
        assertEquals(null, rules.matching("Station Total"))
        assertEquals(null, rules.matching("Boulangerie"))
    }

    @Test
    fun `priority wins over precision`() {
        assertEquals(4L, rules.matching("Cafe Carrefour Market")?.id)
    }

    private val day = LocalDate.of(2026, 10, 20)

    @Test
    fun `reconciles a manual entry with the same amount a few days before`() {
        val manual = listOf(
            ManualEntry(1, day.minusDays(2), -1240),
            ManualEntry(2, day.minusDays(1), -999),
        )
        assertEquals(1L, findManualMatch(day, -1240, manual)?.id)
    }

    @Test
    fun `picks the closest date, then the oldest entry`() {
        val manual = listOf(
            ManualEntry(1, day.minusDays(5), -500),
            ManualEntry(2, day.minusDays(1), -500),
            ManualEntry(3, day.minusDays(1), -500),
        )
        assertEquals(2L, findManualMatch(day, -500, manual)?.id)
    }

    @Test
    fun `ignores entries outside the window or with another amount`() {
        val manual = listOf(
            ManualEntry(1, day.minusDays(8), -500),
            ManualEntry(2, day.plusDays(3), -500),
            ManualEntry(3, day, -501),
        )
        assertEquals(null, findManualMatch(day, -500, manual))
    }

    @Test
    fun `import keys use the bank reference, else a numbered composite`() {
        val operations = listOf(
            BankOperation("ABC", day, -300, "CAFE"),
            BankOperation(null, day, -300, "CAFE"),
            BankOperation(null, day, -300, "Café"),
            BankOperation("", day, -450, "BOULANGERIE"),
        )
        assertEquals(
            listOf(
                "eb:acc:ref:ABC",
                "eb:acc:2026-10-20|-300|CAFE",
                "eb:acc:2026-10-20|-300|CAFE#2",
                "eb:acc:2026-10-20|-450|BOULANGERIE",
            ),
            operations.importKeys("acc"),
        )
    }

    @Test
    fun `operation date comes from the bank, else from the label, else the booking date`() {
        val booked = LocalDate.of(2026, 10, 27)
        assertEquals(LocalDate.of(2026, 10, 25), operationDate("RETRAIT DAB 25/10 PARIS", booked))
        assertEquals(LocalDate.of(2026, 10, 25), operationDate("PAIEMENT CB 2510 PARIS DECATHLON", booked))
        assertEquals(LocalDate.of(2026, 10, 24), operationDate("RETRAIT DAB 25/10", booked, transactionDate = LocalDate.of(2026, 10, 24)))
        // Une date trop ancienne dans le libellé n'est pas celle du paiement.
        assertEquals(booked, operationDate("ECHEANCE PRET 0105", booked))
        // Fin d'année : « CB 3112 » comptabilisé le 2 janvier.
        assertEquals(LocalDate.of(2026, 12, 31), operationDate("CB 3112 CADEAU", LocalDate.of(2027, 1, 2)))
        assertEquals(booked, operationDate("PRLV SEPA FREE MOBILE", booked))
    }

    @Test
    fun `dedup key keeps the booking date`() {
        val booked = LocalDate.of(2026, 10, 27)
        val keys = listOf(BankOperation(null, LocalDate.of(2026, 10, 25), -5000, "RETRAIT DAB", bookingDate = booked)).importKeys("a")
        assertEquals(listOf("eb:a:2026-10-27|-5000|RETRAIT DAB"), keys)
    }
}
