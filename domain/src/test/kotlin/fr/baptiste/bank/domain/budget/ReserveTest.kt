package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReserveTest {

    private val today = LocalDate.of(2026, 10, 15)

    private fun envelope(id: Long, type: EnvelopeType, euros: Long) = Envelope(
        id = id,
        name = "E$id",
        type = type,
        frequency = type.defaultFrequency,
        amount = Money.euros(euros),
        status = type.defaultStatus,
    )

    private val courses = envelope(1, EnvelopeType.DAILY, 400)
    private val epargne = envelope(2, EnvelopeType.SAVINGS, 300)
    private val envelopes = listOf(courses, epargne)
    private val spent = listOf(Spending(courses.id, today.withDayOfMonth(3), Money.euros(250)))
    private val income = Money.euros(1200)

    private fun board(transfers: List<Transfer> = emptyList()) =
        computeDashboard(envelopes, emptyMap(), spent, income, today, transfers = transfers)

    private val fromReserve = Transfer(0, today, null, epargne.id, Money.euros(1000), fromReserve = true)
    private val fromWallet = fromReserve.copy(fromReserve = false)

    @Test
    fun `setting 1000 euros aside from the reserve leaves the month untouched`() {
        val before = board()
        val after = board(listOf(fromReserve))
        assertEquals(before.monthLeft, after.monthLeft)
        assertEquals(before.unallocated, after.unallocated)
        // Ce n'est pas de l'épargne du mois : la prévision ne bouge pas.
        assertEquals(before.savings.forecast, after.savings.forecast)
        assertEquals(Money.euros(1000), after.reserveToSavings)
        assertEquals(before.monthLeft, before.monthLeftAfterTransfer(null, epargne.id, Money.euros(1000), fromReserve = true))
    }

    @Test
    fun `the same transfer from the wallet makes the month negative`() {
        // Reste du mois : 1 200 − 300 − 250 = 650 € ; 1 000 € pris dessus le rendent négatif.
        assertEquals(Money.euros(650), board().monthLeft)
        assertEquals(Money.euros(-350), board(listOf(fromWallet)).monthLeft)
    }

    @Test
    fun `the reserve shrinks and the savings to transfer grow`() {
        val balance = Money.euros(3000)
        val before = board().explainCurrentAccount(balance, today, Money.ZERO)
        val after = board(listOf(fromReserve)).explainCurrentAccount(balance, today, Money.ZERO)
        assertEquals(before.free - Money.euros(1000), after.free)
        assertEquals(before.savingsPending + Money.euros(1000), after.savingsPending)
        // Une fois viré vers le livret, l'épargne à virer retombe.
        val done = board(listOf(fromReserve)).explainCurrentAccount(balance - Money.euros(1000), today, Money.euros(1000))
        assertEquals(before.savingsPending, done.savingsPending)
        assertEquals(after.free, done.free)
    }
}
