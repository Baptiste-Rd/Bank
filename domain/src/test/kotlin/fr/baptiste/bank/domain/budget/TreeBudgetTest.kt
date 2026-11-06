package fr.baptiste.bank.domain.budget

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

class TreeBudgetTest {
    private fun envelope(id: Long, name: String, euros: Long, parent: Long? = null, type: EnvelopeType = EnvelopeType.DAILY, frequency: Frequency = Frequency.MONTHLY) =
        Envelope(id, name, type, frequency, Money.euros(euros), EnvelopeStatus.FLEXIBLE, parentId = parent)

    @Test
    fun aParentReservesOnlyWhatItsChildrenDoNotCover() {
        val own = ownAmounts(listOf(envelope(1, "A", 300), envelope(2, "B", 200, 1), envelope(3, "C", 100, 2)), emptyList())
        assertEquals(Money.euros(100), own[1])
        assertEquals(Money.euros(100), own[2])
        assertEquals(null, own[3])
    }

    @Test
    fun theMonthCountsEachEuroOnce() {
        val envelopes = listOf(envelope(1, "A", 300), envelope(2, "B", 200, 1), envelope(3, "C", 400, 2))
        val counted = envelopes.withOwnAmounts(emptyList()).fold(Money.ZERO) { sum, it -> sum + it.monthlyReservation() }
        assertEquals(Money.euros(400), counted)
    }

    @Test
    fun aYearlyChildCountsForItsMonthlyPartInAMonthlyParent() {
        val yearly = envelope(2, "Vacances", 1200, 1, EnvelopeType.ANNUAL, Frequency.YEARLY)
        assertEquals(Money.euros(200), ownAmounts(listOf(envelope(1, "Loisirs", 300), yearly), emptyList())[1])
    }

    @Test
    fun anArchivedChildNoLongerCounts() {
        val stopped = envelope(2, "B", 500, 1).copy(archived = true)
        assertEquals(emptyMap<Long, Money>(), ownAmounts(listOf(envelope(1, "A", 300), stopped), emptyList()))
    }

    @Test
    fun nothingChangesWithoutARealParent() {
        val envelopes = listOf(envelope(1, "Courses", 300, 10), envelope(2, "Bar", 60, 10))
        assertTrue(envelopes.withOwnAmounts(listOf(GroupNode(10, "Nourriture", null))) === envelopes)
    }

    @Test
    fun budgetRowsHideClosedBranchesAndKeepTheRootName() {
        val rows = budgetRows(listOf(envelope(1, "Essence", 120, 11), envelope(2, "Bus", 30, 10)), listOf(GroupNode(10, "Transport", null), GroupNode(11, "Voiture", 10)), setOf(10L))
        assertEquals(listOf("Transport", "Bus", "Voiture"), rows.map { it.name })
        assertEquals(Money.euros(120), rows[2].covered)
        assertEquals(Money.euros(150), rows[0].covered)
        assertEquals("Transport", rows[2].rootName)
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
