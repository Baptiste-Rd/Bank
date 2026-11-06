package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.BudgetPeriod
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeTree
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.TreeNode
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TreeBudgetTest {
    private val month = YearMonth.of(2026, 11)
    private val since = YearMonth.of(2026, 1)

    private fun envelope(id: Long, name: String, euros: Long, parent: Long? = null, type: EnvelopeType = EnvelopeType.DAILY, frequency: Frequency = Frequency.MONTHLY) =
        Envelope(id, name, type, frequency, Money.euros(euros), EnvelopeStatus.FLEXIBLE, parentId = parent)

    private fun own(vararg envelopes: Envelope) = envelopes.toList().withOwnAmounts(emptyList(), month, since).associate { it.id to it.amount }

    @Test
    fun aParentReservesOnlyWhatItsChildrenDoNotCover() {
        val own = own(envelope(1, "A", 300), envelope(2, "B", 200, 1), envelope(3, "C", 100, 2))
        assertEquals(Money.euros(100), own[1])
        assertEquals(Money.euros(100), own[2])
        assertEquals(Money.euros(100), own[3])
    }

    @Test
    fun theMonthCountsEachEuroOnce() {
        val own = own(envelope(1, "A", 300), envelope(2, "B", 200, 1), envelope(3, "C", 400, 2))
        assertEquals(Money.euros(400), own.values.fold(Money.ZERO) { sum, it -> sum + it })
    }

    @Test
    fun aYearlyChildCountsForItsMonthlyPartInAMonthlyParent() {
        val yearly = envelope(2, "Vacances", 1200, 1, EnvelopeType.ANNUAL, Frequency.YEARLY)
        assertEquals(Money.euros(200), own(envelope(1, "Loisirs", 300), yearly)[1])
    }

    @Test
    fun aChildStoppedThisMonthStaysCoveredUntilTheEndOfTheMonth() {
        val stopped = envelope(2, "C", 400, 1).copy(archived = true, retiredOn = LocalDate.of(2026, 11, 6))
        assertEquals(Money.ZERO, own(envelope(1, "A", 300), stopped)[1])
        val older = stopped.copy(retiredOn = LocalDate.of(2026, 9, 6))
        assertEquals(Money.euros(300), own(envelope(1, "A", 300), older)[1])
    }

    @Test
    fun aFrozenPeriodKeepsItsOwnCoverage() {
        val period = BudgetPeriod("2026-10", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), Money.euros(350), Money.euros(100))
        val parent = envelope(1, "A", 300).copy(periods = listOf(period))
        assertEquals(Money.euros(250), listOf(parent).withOwnAmounts(emptyList(), month, since).single().periods.single().amount)
    }

    @Test
    fun nothingChangesWithoutARealParent() {
        val envelopes = listOf(envelope(1, "Courses", 300, 10), envelope(2, "Bar", 60, 10))
        assertTrue(envelopes.withOwnAmounts(listOf(GroupNode(10, "Nourriture", null)), month, since) === envelopes)
    }

    @Test
    fun budgetRowsHideClosedBranchesAndKeepTheRootName() {
        val rows = budgetRows(listOf(envelope(1, "Essence", 120, 11), envelope(2, "Bus", 30, 10)), listOf(GroupNode(10, "Transport", null), GroupNode(11, "Voiture", 10)), month, since, setOf(10L))
        assertEquals(listOf("Transport", "Bus", "Voiture"), rows.map { it.name })
        assertEquals(Money.euros(120), rows[2].covered)
        assertEquals(Money.euros(150), rows[0].covered)
        assertEquals("Transport", rows[2].rootName)
    }

    @Test
    fun aProvisionStartedLateIsCoveredInTheSameProportionAsItsShare() {
        val noel = envelope(1, "Noël", 1200, null, EnvelopeType.ANNUAL, Frequency.YEARLY).copy(fundingStart = month)
        val child = envelope(2, "Cadeaux", 100, 1)
        assertEquals(Money.euros(600), noel.monthlyBudget(month, since))
        assertEquals(Money.euros(200), childrenAmounts(listOf(noel, child), emptyList(), month, since)[1])
    }

    @Test
    fun whatAStoppedParentContainsStaysVisible() {
        val stopped = envelope(1, "A", 300).copy(archived = true, retiredOn = LocalDate.of(2026, 11, 6))
        val rows = budgetRows(listOf(stopped, envelope(2, "B", 100, 1)), emptyList(), month, since, setOf(1L, 2L))
        assertEquals(listOf("B"), rows.map { it.name })
    }

    private fun line(envelope: Envelope, share: Money = Money.ZERO) = EnvelopeLine(
        envelope, fr.baptiste.bank.domain.period.Frequency.MONTHLY.periodContaining(month.atDay(1), LocalDate.of(2026, 1, 1)),
        Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
        Projection(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, false), monthlyShare = share,
    )

    @Test
    fun theSpendingTreeGivesBackTheMonthTotalAtEveryLevel() {
        val essence = envelope(1, "Essence", 120, 11)
        val bus = envelope(2, "Bus", 30, 10)
        val outside = envelope(3, "Ancienne", 10, 10)
        val rows = spendingRows(
            listOf(line(essence), line(bus)),
            listOf(outside),
            mapOf(1L to Money.euros(60), 2L to Money.euros(25), 3L to Money.euros(15)),
            listOf(GroupNode(10, "Transport", null), GroupNode(11, "Voiture", 10)),
            SpendFilter.ALL,
            setOf(10L, 11L),
        )
        assertEquals(Money.euros(100), rows.first { it.name == "Transport" }.spent)
        assertEquals(Money.euros(60), rows.first { it.name == "Voiture" }.spent)
        assertEquals("Ancienne", rows.first { it.id == 3L }.name)
    }

    @Test
    fun aFilterHidesOtherBranchesAndRecountsTheOnesItKeeps() {
        val essence = envelope(1, "Essence", 120, 10)
        val entretien = envelope(2, "Entretien", 600, 10, EnvelopeType.ANNUAL, Frequency.YEARLY)
        val cadeaux = envelope(3, "Noël", 300, null, EnvelopeType.ANNUAL, Frequency.YEARLY)
        val rows = spendingRows(
            listOf(line(essence), line(entretien), line(cadeaux)),
            emptyList(),
            mapOf(1L to Money.euros(60), 2L to Money.euros(40), 3L to Money.euros(10)),
            listOf(GroupNode(10, "Transport", null)),
            SpendFilter.MONTHLY,
            setOf(10L),
        )
        assertEquals(listOf("Transport", "Essence"), rows.map { it.name })
        assertEquals(Money.euros(60), rows.first().spent)
    }

    @Test
    fun theSavingsFilterShowsTheMonthlyShares() {
        val rows = spendingRows(
            listOf(line(envelope(1, "Suravenir", 140, 10, EnvelopeType.SAVINGS), Money.euros(140))),
            emptyList(), emptyMap(), listOf(GroupNode(10, "Bloquée", null)), SpendFilter.SAVINGS, setOf(10L),
        )
        assertEquals(Money.euros(140), rows.first().spent)
    }

    @Test
    fun coverageChangesSayWhatMovedInThePlan() {
        val before = EnvelopeTree(listOf(TreeNode(1, null, 0, Money.euros(300)), TreeNode(2, null, 1, Money.euros(400))))
        val after = EnvelopeTree(listOf(TreeNode(1, null, 0, Money.euros(300)), TreeNode(2, 1, 0, Money.euros(400))))
        val result = coverageChanges("C rangée", before, after, mapOf(1L to "A", 2L to "C"))
        assertEquals(listOf(CoverageChange("A", Money.euros(300), Money.euros(400))), result.changes)
        assertEquals(Money.euros(-300), result.planDelta)
    }

    @Test
    fun spendFiltersFollowTheType() {
        assertTrue(SpendFilter.MONTHLY.accepts(EnvelopeType.FIXED))
        assertTrue(SpendFilter.OCCASIONAL.accepts(EnvelopeType.PLANNED))
        assertTrue(!SpendFilter.MONTHLY.accepts(EnvelopeType.ANNUAL))
        assertTrue(SpendFilter.ALL.accepts(EnvelopeType.SAVINGS))
    }
}
