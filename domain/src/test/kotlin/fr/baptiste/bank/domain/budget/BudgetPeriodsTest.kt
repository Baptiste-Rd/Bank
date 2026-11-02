package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.BudgetPeriod
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.domain.period.Scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetPeriodsTest {

    private val today = LocalDate.of(2026, 11, 2)

    // Coiffeur créé le 30 octobre : 26 € jusqu'au 1er janvier, puis 26 € tous les 3 mois, reste gardé.
    private val hairdresser = Envelope(
        id = 1, name = "Coiffeur", type = EnvelopeType.RECURRING, frequency = Frequency(3, Scale.MONTH),
        amount = Money.euros(26), status = EnvelopeType.RECURRING.defaultStatus, carryOver = true,
        start = LocalDate.of(2026, 10, 30), firstReset = LocalDate.of(2027, 1, 1),
    )

    // Vacances : 1 000 € pour l'année, ponctuel.
    private val holidays = Envelope(
        id = 2, name = "Vacances", type = EnvelopeType.ANNUAL, frequency = Frequency.YEARLY,
        amount = Money.euros(1000), status = EnvelopeType.ANNUAL.defaultStatus,
        start = LocalDate.of(2026, 1, 1), firstReset = LocalDate.of(2027, 1, 1), repeats = false,
    )

    private val groceries = Envelope(
        id = 3, name = "Courses", type = EnvelopeType.DAILY, frequency = Frequency.MONTHLY,
        amount = Money.euros(400), status = EnvelopeType.DAILY.defaultStatus, start = LocalDate.of(2026, 8, 1),
    )

    @Test
    fun `the first budget runs from the start to the reset, then every three months`() {
        val first = hairdresser.periodContaining(LocalDate.of(2026, 11, 15))
        assertEquals(LocalDate.of(2026, 10, 30), first.start)
        assertEquals(LocalDate.of(2026, 12, 31), first.endInclusive)
        val next = hairdresser.periodContaining(LocalDate.of(2027, 2, 10))
        assertEquals(LocalDate.of(2027, 1, 1), next.start)
        assertEquals(LocalDate.of(2027, 3, 31), next.endInclusive)
        // 26 € sur octobre, novembre et décembre : 8,67 € par mois.
        assertEquals(Money(867), hairdresser.monthShare(YearMonth.of(2026, 11), first, Money.euros(26)))
    }

    @Test
    fun `a first amount applies to the first period only`() {
        val envelope = hairdresser.copy(firstAmount = Money.euros(30))
        assertEquals(Money.euros(30), envelope.amountIn(envelope.periodContaining(LocalDate.of(2026, 11, 1))))
        assertEquals(Money.euros(26), envelope.amountIn(envelope.periodContaining(LocalDate.of(2027, 2, 1))))
    }

    @Test
    fun `a one-off budget has a single period and stays in the months it covered`() {
        assertTrue(holidays.existsOn(LocalDate.of(2026, 12, 31)))
        assertTrue(!holidays.existsOn(LocalDate.of(2027, 1, 1)))
        assertTrue(holidays.existedDuring(YearMonth.of(2026, 8)))
        assertTrue(!holidays.existedDuring(YearMonth.of(2027, 2)))
    }

    @Test
    fun `a retired envelope keeps the months it covered`() {
        val retired = groceries.copy(archived = true, retiredOn = LocalDate.of(2026, 10, 10))
        assertTrue(retired.existedDuring(YearMonth.of(2026, 9)))
        assertTrue(retired.existedDuring(YearMonth.of(2026, 10)))
        assertTrue(!retired.existedDuring(YearMonth.of(2026, 11)))
        val report = monthSpending(YearMonth.of(2026, 9), listOf(retired), emptyList())
        assertEquals(Money.euros(400), report.budget)
    }

    @Test
    fun `missing periods are recorded from the start, and a stored amount freezes the past`() {
        val rows = groceries.missingPeriods(LocalDate.of(2026, 1, 1), today)
        assertEquals(listOf("2026-08", "2026-09", "2026-10", "2026-11"), rows.map { it.key })
        // Le budget passe à 300 € : août garde 400 €.
        val frozen = groceries.copy(amount = Money.euros(300), periods = rows)
        assertEquals(Money.euros(400), frozen.monthlyBudget(YearMonth.of(2026, 8)))
        val current = groceries.copy(periods = rows).currentPeriodWithAmount(today, Money.euros(300))
        assertEquals(Money.euros(300), current?.amount)
        assertEquals("2026-11", current?.key)
    }

    @Test
    fun `a rare budget carries its leftover to the next period`() {
        val first = hairdresser.missingPeriods(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 2))
        assertEquals(1, first.size)
        val known = hairdresser.copy(periods = first)
        val next = known.missingPeriods(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 5), spentByKey = emptyMap())
        assertEquals(Money.euros(52), next.single().amount)
        val spent = known.missingPeriods(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 5), mapOf(first.single().key to Money.euros(26)))
        assertEquals(Money.euros(26), spent.single().amount)
    }

    @Test
    fun `past periods recorded late keep their amount`() {
        val rows = hairdresser.missingPeriods(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 5, 1))
        assertEquals(listOf(Money.euros(26), Money.euros(26), Money.euros(52)), rows.map { it.amount })
    }

    @Test
    fun `after a frozen period of an old rhythm the next one starts the day after`() {
        val stored = BudgetPeriod("2026-10-15..2026-11-14", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14), Money.euros(50))
        val envelope = groceries.copy(periods = listOf(stored))
        assertEquals(stored.key, envelope.periodContaining(LocalDate.of(2026, 11, 1)).key)
        val after = envelope.periodContaining(LocalDate.of(2026, 11, 20))
        assertEquals(LocalDate.of(2026, 11, 15), after.start)
        assertEquals(LocalDate.of(2026, 11, 30), after.endInclusive)
        assertEquals(Period.Month(YearMonth.of(2026, 12)), envelope.periodContaining(LocalDate.of(2026, 12, 3)))
    }

    @Test
    fun `a fixed period key round-trips`() {
        val period = Period.Fixed(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 12, 31))
        assertEquals(period, Period.parse(period.key))
    }
}
