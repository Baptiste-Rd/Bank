package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransferTest {

    private val today = LocalDate.of(2026, 10, 15)

    private fun envelope(
        id: Long,
        type: EnvelopeType,
        euros: Long,
        status: EnvelopeStatus = type.defaultStatus,
        priority: Int = 0,
    ) = Envelope(
        id = id,
        name = "E$id",
        type = type,
        frequency = type.defaultFrequency,
        amount = Money.euros(euros),
        status = status,
        priority = priority,
    )

    private val food = envelope(1, EnvelopeType.DAILY, 450)
    private val fuel = envelope(2, EnvelopeType.DAILY, 100)
    private val outings = envelope(3, EnvelopeType.DAILY, 80, priority = 0)
    private val projects = envelope(4, EnvelopeType.ANNUAL, 1200, priority = 1)
    private val savings = envelope(5, EnvelopeType.SAVINGS, 300)

    private fun transfer(source: Envelope?, target: Envelope?, euros: Long, date: LocalDate = today) =
        Transfer(0, date, source?.id, target?.id, Money.euros(euros))

    private fun dashboard(
        envelopes: List<Envelope>,
        transfers: List<Transfer> = emptyList(),
        spendings: List<Spending> = emptyList(),
        income: Money? = Money.euros(2000),
    ) = computeDashboard(envelopes, emptyMap(), spendings, income, today, transfers = transfers)

    @Test
    fun `money from the wallet raises the envelope and the daily budget`() {
        val before = dashboard(listOf(food, fuel))
        val after = dashboard(listOf(food, fuel), listOf(transfer(null, fuel, 40)))

        val fuelLine = after.envelopes.single { it.envelope.id == fuel.id }
        assertEquals(Money.euros(140), fuelLine.available)
        assertEquals(Money.euros(40), fuelLine.transferred)
        assertEquals(before.dailyBudget + Money.euros(40), after.dailyBudget)
        assertEquals(before.unallocated!! - Money.euros(40), after.unallocated)
    }

    @Test
    fun `reducing an envelope gives the money back to the wallet`() {
        val after = dashboard(listOf(food), listOf(transfer(food, null, 50)))
        // 2000 - 450 + 50
        assertEquals(Money.euros(1600), after.unallocated)
        assertEquals(Money.euros(400), after.dailyBudget)
    }

    @Test
    fun `moving money between envelopes leaves the wallet unchanged`() {
        val before = dashboard(listOf(fuel, projects))
        val after = dashboard(listOf(fuel, projects), listOf(transfer(projects, fuel, 40)))
        assertEquals(before.unallocated, after.unallocated)
        assertEquals(Money.euros(1160), after.envelopes.single { it.envelope.id == projects.id }.remaining)
    }

    @Test
    fun `a transfer from a closed month does not change the current one`() {
        val september = transfer(null, fuel, 40, date = LocalDate.of(2026, 9, 20))
        val after = dashboard(listOf(fuel), listOf(september))
        assertEquals(Money.euros(100), after.envelopes.single().available)
    }

    @Test
    fun `the daily allowance is recalculated from the transfer`() {
        val spent = listOf(Spending(fuel.id, LocalDate.of(2026, 10, 10), Money.euros(90)))
        val before = dashboard(listOf(fuel), spendings = spent)
        val after = dashboard(listOf(fuel), listOf(transfer(null, fuel, 34)), spent)
        // (100 - 90) / 17 jours, puis (134 - 90) / 17 jours.
        assertEquals(Money(58), before.dailyAllowance.allowance)
        assertEquals(Money(258), after.dailyAllowance.allowance)
    }

    @Test
    fun `cover plan takes the wallet first then flexible envelopes by priority`() {
        // Essence : 100 € prévus, 95 € dépensés au 15, projection 196,34 €, soit 97 € à couvrir.
        val spent = listOf(Spending(fuel.id, LocalDate.of(2026, 10, 3), Money.euros(95)))
        val envelopes = listOf(fuel, outings, projects, savings)
        // Portefeuille : 610 - 100 - 80 - 100 (projets / 12) - 300 = 30 €.
        val board = dashboard(envelopes, spendings = spent, income = Money.euros(610))
        val target = board.envelopes.single { it.envelope.id == fuel.id }
        val plan = board.coveragePlan(target)!!

        assertEquals(Money.euros(97), plan.needed)
        assertEquals(TransferLeg(null, Money.euros(30)), plan.legs[0])
        // Sorties (priorité 0) avant Projets (priorité 1) ; l'épargne, protégée, n'est jamais touchée.
        assertEquals(TransferLeg(outings.id, Money.euros(67)), plan.legs[1])
        assertEquals(2, plan.legs.size)
        assertEquals(Money.ZERO, plan.shortfall)
    }

    @Test
    fun `cover plan reports what is missing`() {
        val spent = listOf(Spending(fuel.id, LocalDate.of(2026, 10, 3), Money.euros(250)))
        val board = dashboard(listOf(fuel, savings), spendings = spent, income = Money.euros(400))
        val plan = board.coveragePlan(board.envelopes.single { it.envelope.id == fuel.id })!!
        // 150 € déjà dépassés ; le portefeuille n'a rien, l'épargne est protégée.
        assertEquals(Money.euros(150), plan.needed)
        assertTrue(plan.legs.isEmpty())
        assertEquals(Money.euros(150), plan.shortfall)
    }

    @Test
    fun `no plan for an envelope within its budget`() {
        val board = dashboard(listOf(food))
        assertEquals(null, board.coveragePlan(board.envelopes.single()))
    }
}
