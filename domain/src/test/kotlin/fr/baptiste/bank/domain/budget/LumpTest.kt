package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LumpTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val food = Envelope(1, "Courses", EnvelopeType.DAILY, Frequency.MONTHLY, Money.euros(400), EnvelopeStatus.FLEXIBLE)

    private fun lump(start: LocalDate, fromMonth: YearMonth? = null) = Envelope(
        2, "Vacances", EnvelopeType.LUMP, EnvelopeType.LUMP.defaultFrequency, Money.euros(1200), EnvelopeStatus.PROTECTED,
        start = start, anchor = start, repeats = false, fundingStart = fromMonth,
    )

    private fun spending(envelope: Envelope, day: LocalDate, euros: Long) = Spending(envelope.id, day, Money.euros(euros))

    @Test
    fun aLumpTakesNothingFromTheMonthUnlessItWasTakenOnIt() {
        val august = lump(LocalDate.of(2026, 8, 1))
        assertEquals(Money.ZERO, august.monthlyBudget(YearMonth.of(2026, 8), null))
        assertEquals(Money.ZERO, august.monthlyBudget(YearMonth.of(2026, 11), null))
        val fromNovember = lump(LocalDate.of(2026, 11, 1), YearMonth.of(2026, 11))
        assertEquals(Money.euros(1200), fromNovember.monthlyBudget(YearMonth.of(2026, 11), null))
        assertEquals(Money.ZERO, fromNovember.monthlyBudget(YearMonth.of(2026, 12), null))
    }

    @Test
    fun spendingFromALumpLeavesThePerDayAloneAndRunsDownItsRest() {
        val august = lump(LocalDate.of(2026, 8, 1))
        val income = Money.euros(2400)
        val without = computeDashboard(listOf(food), emptyMap(), emptyList(), income, today)
        val with = computeDashboard(listOf(food, august), emptyMap(), listOf(spending(august, LocalDate.of(2026, 8, 12), 700), spending(august, today, 200)), income, today)
        assertEquals(without.dailyAllowance.allowance, with.dailyAllowance.allowance)
        assertEquals(Money.euros(300), with.envelopes.first { it.envelope.id == 2L }.remaining)
    }

    @Test
    fun aLumpTakenOnTheMonthLowersThePerDay() {
        val income = Money.euros(2400)
        val without = computeDashboard(listOf(food), emptyMap(), emptyList(), income, today)
        val with = computeDashboard(listOf(food, lump(LocalDate.of(2026, 11, 1), YearMonth.of(2026, 11))), emptyMap(), emptyList(), income, today)
        assertEquals(without.perDayWithReserved(Money.euros(1200)), with.dailyAllowance.allowance)
    }
}
