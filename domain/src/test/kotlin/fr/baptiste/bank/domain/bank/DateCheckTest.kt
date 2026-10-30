package fr.baptiste.bank.domain.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateCheckTest {

    private fun op(id: Long, label: String, date: LocalDate, booking: LocalDate? = null, bank: LocalDate? = null) =
        DatedOperation(id, label, -1000, date, booking, bank)

    private val debit = LocalDate.of(2026, 10, 30)

    @Test
    fun `deferred card is detected from long delays`() {
        val ops = (1..6).map { op(it.toLong(), "PAIEMENT CB ${20 + it}09 MAGASIN", LocalDate.of(2026, 9, 20 + it), debit) }
        val report = dateReport(ops)
        assertEquals(CardDebit.DEFERRED, report.debit)
        assertEquals(6, report.cardCount)
        assertTrue(report.corrections.isEmpty())
    }

    @Test
    fun `immediate card is detected from short delays`() {
        val ops = (1..6).map {
            val day = LocalDate.of(2026, 10, 10 + it)
            op(it.toLong(), "PAIEMENT CB ${10 + it}10 MAGASIN", day, day.plusDays(2))
        }
        assertEquals(CardDebit.IMMEDIATE, dateReport(ops).debit)
    }

    @Test
    fun `too few card payments give no verdict`() {
        assertEquals(CardDebit.UNKNOWN, dateReport(listOf(op(1, "CB 2209 FNAC", LocalDate.of(2026, 9, 22), debit))).debit)
    }

    @Test
    fun `a payment dated on the debit day is proposed back to its purchase day`() {
        // Ancienne règle : 38 jours, au-delà des 10 jours, donc datée du débit (octobre au lieu de septembre).
        val wrong = op(1, "PAIEMENT CB 2209 FNAC", debit, booking = debit)
        val unknownBooking = op(2, "PAIEMENT CB 2509 FNAC", debit)
        val right = op(3, "PAIEMENT CB 2810 FNAC", LocalDate.of(2026, 10, 28), booking = debit)
        val report = dateReport(listOf(wrong, unknownBooking, right))
        assertEquals(2, report.corrections.size)
        val first = report.corrections.single { it.operation.id == 1L }
        assertEquals(LocalDate.of(2026, 9, 22), first.newDate)
        assertTrue(first.changesMonth)
        assertEquals(LocalDate.of(2026, 9, 25), report.corrections.single { it.operation.id == 2L }.newDate)
    }

    @Test
    fun `the global card debit line is spotted`() {
        val report = dateReport(listOf(op(1, "FACTURE CARTE DU 201026", debit), op(2, "PRLV SEPA EDF", debit)))
        assertEquals(listOf(1L), report.cardStatements.map { it.id })
    }
}
