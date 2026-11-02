package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class IncomeTest {

    private val inflows = listOf(
        Inflow("VIR SEPA RECU /DE SOCIETE EXEMPLE /MOTIF SALAIRE AOUT 2026", LocalDate.of(2026, 8, 28), Money.euros(2400)),
        Inflow("VIR SEPA RECU /DE SOCIETE EXEMPLE /MOTIF SALAIRE SEPTEMBRE 2026", LocalDate.of(2026, 9, 29), Money.euros(2410)),
        Inflow("VIR SEPA RECU /DE SOCIETE EXEMPLE /MOTIF SALAIRE OCTOBRE 2026", LocalDate.of(2026, 10, 28), Money.euros(2450)),
        Inflow("REMBOURSEMENT AMELI", LocalDate.of(2026, 10, 12), Money.euros(25)),
        Inflow("VIR INSTANTANE DE M DUPONT", LocalDate.of(2026, 9, 3), Money.euros(400)),
    )

    @Test
    fun `the salary is found from the same payer every month`() {
        val candidate = salaryCandidates(inflows).first()
        assertEquals(3, candidate.months)
        assertEquals(Money.euros(2450), candidate.lastAmount)
        assertTrue(candidate.paysNextMonth)
        assertEquals(1, salaryCandidates(inflows).size)
    }

    @Test
    fun `a salary paid on the 28th funds the next month, else the last one is expected`() {
        val key = salaryCandidates(inflows).first().key
        assertEquals(MonthIncome(Money.euros(2450), received = true), monthIncome(YearMonth.of(2026, 11), inflows, key, paysNextMonth = true))
        assertEquals(MonthIncome(Money.euros(2450), received = false), monthIncome(YearMonth.of(2026, 12), inflows, key, paysNextMonth = true))
        assertEquals(MonthIncome(Money.euros(2450), received = true), monthIncome(YearMonth.of(2026, 10), inflows, key, paysNextMonth = false))
        assertEquals(null, monthIncome(YearMonth.of(2026, 8), inflows, key, paysNextMonth = true))
    }

    @Test
    fun `the suggested amount averages the finished months available, up to twelve`() {
        val spent = listOf(
            LocalDate.of(2026, 8, 10) to Money.euros(30),
            LocalDate.of(2026, 9, 10) to Money.euros(0),
            LocalDate.of(2026, 10, 10) to Money.euros(48),
            LocalDate.of(2026, 11, 1) to Money.euros(100),
        )
        // Trois mois de données (août à octobre) : 78 € / 3 = 26 € par mois, 78 € tous les 3 mois.
        val suggestion = suggestedAmount(spent, Frequency(3, Scale.MONTH), LocalDate.of(2026, 11, 2), since = YearMonth.of(2026, 8))
        assertEquals(3, suggestion?.months)
        assertEquals(Money.euros(26), suggestion?.monthlyAverage)
        assertEquals(Money.euros(78), suggestion?.amount)
        assertEquals(12, suggestedAmount(spent, Frequency.MONTHLY, LocalDate.of(2026, 11, 2))?.months)
    }
}
