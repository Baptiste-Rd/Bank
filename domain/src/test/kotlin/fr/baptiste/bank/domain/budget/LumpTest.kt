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

    @Test
    fun whatIsLeftInALumpIsNotFreeOnTheAccount() {
        val income = Money.euros(2400)
        val without = computeDashboard(listOf(food), emptyMap(), emptyList(), income, today)
        val with = computeDashboard(listOf(food, lump(LocalDate.of(2026, 11, 1))), emptyMap(), listOf(spending(lump(LocalDate.of(2026, 11, 1)), today, 200)), income, today)
        val free = without.explainCurrentAccount(Money.euros(3000), today, transferredToAccounts = Money.ZERO).free
        assertEquals(free - Money.euros(1000), with.explainCurrentAccount(Money.euros(3000), today, transferredToAccounts = Money.ZERO).free)
    }

    @Test
    fun lumpSpendingsAreNotTheMonthsAndALumpWeighsNothingInTheTree() {
        val start = LocalDate.of(2026, 11, 1)
        val fromMonth = lump(start, YearMonth.of(2026, 11)).copy(parentId = 10)
        val parent = Envelope(10, "Loisirs", EnvelopeType.DAILY, Frequency.MONTHLY, Money.euros(150), EnvelopeStatus.FLEXIBLE)
        assertEquals(emptyList<Spending>(), countedSpendings(YearMonth.of(2026, 11), listOf(fromMonth), listOf(spending(fromMonth, today, 50))))
        assertEquals(Money.ZERO, childrenAmounts(listOf(parent, fromMonth), emptyList(), YearMonth.of(2026, 11), null)[10] ?: Money.ZERO)
        val child = food.copy(parentId = 2)
        assertEquals(null, childrenAmounts(listOf(lump(start), child), emptyList(), YearMonth.of(2026, 11), null)[2])
    }

    @Test
    fun theMonthReportLeavesOutALumpBudgetAndSpendingAlike() {
        val start = LocalDate.of(2026, 11, 1)
        val fromMonth = lump(start, YearMonth.of(2026, 11))
        val report = monthSpending(YearMonth.of(2026, 11), listOf(food, fromMonth), listOf(spending(fromMonth, today, 300), spending(food, today, 20)))
        assertEquals(Money.euros(400), report.budget)
        assertEquals(Money.euros(20), report.total)
        // Sa ligne garde le dépensé du mois seulement : une dépense d'octobre n'y figure pas.
        val october = monthSpending(YearMonth.of(2026, 11), listOf(food, fromMonth), listOf(spending(fromMonth, today, 300), spending(fromMonth, LocalDate.of(2026, 10, 30), 50)))
        assertEquals(mapOf(fromMonth.id to Money.euros(300)), october.lumpSpent)
    }
}
