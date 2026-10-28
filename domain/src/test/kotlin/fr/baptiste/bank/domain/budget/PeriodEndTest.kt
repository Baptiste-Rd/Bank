package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Ce qui reste dans une enveloppe à la fin de sa période va à l'épargne ; un dépassement y est pris. */
class PeriodEndTest {

    private fun envelope(id: Long, name: String, type: EnvelopeType, euros: Long, frequency: Frequency = type.defaultFrequency) =
        Envelope(id, name, type, frequency, Money.euros(euros), type.defaultStatus)

    private val telephone = envelope(1, "Téléphone", EnvelopeType.FIXED, 30)
    private val vacances = envelope(2, "Vacances", EnvelopeType.ANNUAL, 1200)
    private val epargne = envelope(3, "Épargne", EnvelopeType.SAVINGS, 300)
    private val all = listOf(telephone, vacances, epargne)
    private val income = Money.euros(2000)

    private fun outlook(date: LocalDate, spendings: List<Spending>) =
        computeDashboard(all, emptyMap(), spendings, income, date).savings

    @Test
    fun `a monthly charge left unspent goes to savings at month end only`() {
        val spendings = listOf(Spending(1, LocalDate.of(2026, 10, 5), Money.euros(20)))
        assertEquals(Money.ZERO, outlook(LocalDate.of(2026, 10, 15), spendings).surpluses)
        assertEquals(Money.euros(10), outlook(LocalDate.of(2026, 10, 31), spendings).surpluses)
    }

    @Test
    fun `an annual provision is balanced at year end`() {
        val spendings = listOf(Spending(2, LocalDate.of(2026, 7, 10), Money.euros(900)))
        // Fin novembre, seul le téléphone (non dépensé) est clos : les vacances courent jusqu'au 31 décembre.
        assertEquals(Money.euros(30), outlook(LocalDate.of(2026, 11, 30), spendings).surpluses)
        // Fin décembre : 300 € de vacances non dépensés, plus le téléphone.
        assertEquals(Money.euros(330), outlook(LocalDate.of(2026, 12, 31), spendings).surpluses)
    }

    @Test
    fun `an overrun is taken from savings and leaves no surplus`() {
        val spendings = listOf(Spending(1, LocalDate.of(2026, 10, 5), Money.euros(45)))
        val final = outlook(LocalDate.of(2026, 10, 31), spendings)
        assertEquals(Money.ZERO, final.surpluses)
        assertEquals(Money.euros(15), final.overruns)
    }
}
