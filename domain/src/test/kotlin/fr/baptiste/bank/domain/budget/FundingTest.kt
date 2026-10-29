package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FundingTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val october = YearMonth.of(2026, 10)

    private fun annual(id: Long, euros: Long, fundingStart: YearMonth? = null) = Envelope(
        id = id,
        name = "E$id",
        type = EnvelopeType.ANNUAL,
        frequency = EnvelopeType.ANNUAL.defaultFrequency,
        amount = Money.euros(euros),
        status = EnvelopeType.ANNUAL.defaultStatus,
        fundingStart = fundingStart,
    )

    private val noel = annual(1, 300, fundingStart = october)
    private val vacances = annual(2, 1800)

    @Test
    fun `christmas created in october sets aside 100 euros a month`() {
        val period = noel.periodContaining(today)
        assertEquals(Money.euros(100), noel.monthShare(october, period, noel.amount))
        assertEquals(Money.ZERO, noel.monthShare(YearMonth.of(2026, 9), period, noel.amount))
        assertEquals(Money.euros(100), noel.fundedThrough(october, period, noel.amount))
        assertEquals(Money.euros(300), noel.fundedThrough(YearMonth.of(2026, 12), period, noel.amount))
    }

    @Test
    fun `an envelope without catch-up keeps its usual share`() {
        val period = vacances.periodContaining(today)
        assertEquals(Money.euros(150), vacances.monthShare(october, period, vacances.amount))
        // Suivi depuis août : août, septembre, octobre mis de côté.
        assertEquals(Money.euros(450), vacances.fundedThrough(october, period, vacances.amount, YearMonth.of(2026, 8)))
    }

    @Test
    fun `the dashboard reserves the catch-up share`() {
        val board = computeDashboard(listOf(noel), emptyMap(), emptyList(), Money.euros(2000), today)
        val line = board.envelopes.single()
        assertEquals(Money.euros(100), line.monthlyShare)
        assertEquals(Money.euros(100), line.funded)
        assertEquals(Money.euros(1900), board.unallocated)
    }

    @Test
    fun `a catch-up is proposed when tracking started after the period`() {
        val board = computeDashboard(listOf(vacances), emptyMap(), emptyList(), Money.euros(2000), today, trackingStart = october)
        val proposal = board.envelopes.single().catchUp!!
        assertEquals(october, proposal.from)
        assertEquals(3, proposal.months)
        assertEquals(Money.euros(600), proposal.monthlyShare)
        assertEquals(Money.euros(150), proposal.currentShare)
        // Suivi depuis janvier : la part habituelle suffit, rien à proposer.
        val since = computeDashboard(listOf(vacances), emptyMap(), emptyList(), Money.euros(2000), today, trackingStart = YearMonth.of(2026, 1))
        assertTrue(since.envelopes.single().catchUp == null)
        // Déjà rattrapée : plus de proposition.
        val done = computeDashboard(listOf(vacances.copy(fundingStart = october)), emptyMap(), emptyList(), Money.euros(2000), today, trackingStart = october)
        assertTrue(done.envelopes.single().catchUp == null)
        assertEquals(Money.euros(600), done.envelopes.single().monthlyShare)
    }

    @Test
    fun `the current account holds only what was funded`() {
        val board = computeDashboard(listOf(noel), emptyMap(), emptyList(), Money.euros(2000), today)
        val explained = board.explainCurrentAccount(Money.euros(3000), today, Money.ZERO)
        assertEquals(Money.euros(100), explained.provisionsHeld)
    }
}
