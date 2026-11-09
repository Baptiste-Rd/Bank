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

class GoalsTest {
    private val november = YearMonth.of(2026, 11)

    private fun goal(id: Long, monthly: Long, goal: Long? = null, account: Long? = null) =
        Envelope(id, "G$id", EnvelopeType.SAVINGS, Frequency.MONTHLY, Money.euros(monthly), EnvelopeStatus.PROTECTED, goal = goal?.let(Money::euros), accountId = account)

    @Test
    fun aDatedGoalSpreadsWhatIsMissingRoundedUpToTenEuros() {
        // 8 000 € d'ici décembre 2027 avec 0 € : 14 mois, 571,43 € → 580 €.
        assertEquals(Money.euros(580), goalMonthly(Money.euros(8000), Money.ZERO, YearMonth.of(2027, 12), november))
    }

    @Test
    fun aGoalDueThisMonthOrPastAsksForEverythingAndAReachedOneForNothing() {
        assertEquals(Money.euros(500), goalMonthly(Money.euros(500), Money.ZERO, YearMonth.of(2026, 9), november))
        assertEquals(Money.ZERO, goalMonthly(Money.euros(500), Money.euros(600), YearMonth.of(2027, 9), november))
    }

    @Test
    fun onlyASavingsEnvelopeWithAGoalAndADateIsRecalculated() {
        val dated = goal(1, 0, 1200).copy(goalBy = YearMonth.of(2027, 10))
        assertEquals(Money.euros(100), dated.datedGoalMonthly(Money.ZERO, november))
        assertEquals(null, goal(2, 50, 1200).datedGoalMonthly(Money.ZERO, november))
        assertEquals(null, dated.copy(type = EnvelopeType.DAILY).datedGoalMonthly(Money.ZERO, november))
    }

    @Test
    fun toPlaceCountsTransfersSinceTheStartSplitMinusWhatWasPlaced() {
        val start = LocalDate.of(2026, 11, 2)
        val placements = listOf(Placement(1, start, Money.euros(3000), isStart = true), Placement(1, LocalDate.of(2026, 11, 5), Money.euros(100)))
        val transfers = listOf(
            AccountTransfer(7, LocalDate.of(2026, 10, 30), Money.euros(500)),
            AccountTransfer(7, LocalDate.of(2026, 11, 4), Money.euros(240)),
        )
        val since = placements.placingSince(LocalDate.of(2026, 7, 1))
        assertEquals(start, since)
        assertEquals(Money.euros(140), toPlace(transfers, placements, since))
        assertEquals(Money.euros(3100), placements.heldByEnvelope()[1])
    }

    @Test
    fun aSplitServesLinkedGoalsFirstThenTheFirstUnreachedOne() {
        val goals = listOf(SavingsGoalLine(goal(1, 200, 6000), Money.euros(3000)), SavingsGoalLine(goal(2, 140, account = 9), Money.ZERO))
        assertEquals(mapOf(2L to Money.euros(140), 1L to Money.euros(60)), proposeSplit(goals, mapOf(9L to Money.euros(200)), Money.euros(200)))
    }

    @Test
    fun theStartSplitFillsGoalsInOrder() {
        val goals = listOf(SavingsGoalLine(goal(1, 200, 1000), Money.ZERO), SavingsGoalLine(goal(2, 100), Money.ZERO))
        assertEquals(mapOf(1L to Money.euros(1000), 2L to Money.euros(500)), fillInOrder(goals, Money.euros(1500)))
    }
}
