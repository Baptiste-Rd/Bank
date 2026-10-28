package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CurrentAccountTest {

    private fun envelope(id: Long, name: String, type: EnvelopeType, euros: Long) =
        Envelope(id, name, type, type.defaultFrequency, Money.euros(euros), type.defaultStatus)

    private val courses = envelope(1, "Courses", EnvelopeType.DAILY, 400)
    private val pret = envelope(2, "Prêt", EnvelopeType.FIXED, 400)
    private val abonnements = envelope(3, "Abonnements", EnvelopeType.FIXED, 45)
    private val vacances = envelope(4, "Vacances", EnvelopeType.ANNUAL, 1200)
    private val epargne = envelope(5, "Épargne", EnvelopeType.SAVINGS, 300)
    private val today = LocalDate.of(2026, 10, 28)

    private val dashboard = computeDashboard(
        envelopes = listOf(courses, pret, abonnements, vacances, epargne),
        allocations = emptyMap(),
        spendings = listOf(
            Spending(1, LocalDate.of(2026, 10, 10), Money.euros(600)),
            Spending(2, LocalDate.of(2026, 10, 5), Money.euros(400)),
            Spending(3, LocalDate.of(2026, 10, 3), Money.euros(15)),
        ),
        monthlyIncome = Money.euros(2400),
        today = today,
    )

    @Test
    fun `the balance splits into month left, charges due, savings pending and the free rest`() {
        val explained = dashboard.explainCurrentAccount(Money.euros(2000), today, transferredToAccounts = Money.euros(100))
        // Quotidien : 2 400 − 400 − 45 − 100 − 300 = 1 555 € ; 600 € dépensés → 955 € de reste du mois.
        assertEquals(Money.euros(955), explained.monthLeft)
        // Abonnements : 30 € pas encore prélevés ; le prêt est payé ; les vacances courent jusqu'en décembre.
        assertEquals(Money.euros(30), explained.chargesDue)
        // Épargne du mois 300 €, dont 100 € déjà virés.
        assertEquals(Money.euros(200), explained.savingsPending)
        // Vacances : 100 € par mois de janvier à octobre, rien de dépensé.
        assertEquals(Money.euros(1000), explained.provisionsHeld)
        assertEquals(Money.euros(-185), explained.free)
    }

    @Test
    fun `a balance below the budget shows what is missing`() {
        // Données depuis septembre : seuls septembre et octobre ont été mis de côté pour les vacances.
        val explained = dashboard.explainCurrentAccount(
            Money.euros(749), today, transferredToAccounts = Money.ZERO, since = LocalDate.of(2026, 9, 2),
        )
        assertEquals(Money.euros(200), explained.provisionsHeld)
        assertEquals(Money.euros(-736), explained.free)
    }

    @Test
    fun `goals count livrets as available and placements apart`() {
        val holdings = computeHoldings(
            accounts = listOf(
                HoldingAccount(1, "Livret A", AccountType.SAVINGS, linked = false),
                HoldingAccount(2, "PEA", AccountType.INVESTMENT, linked = false),
            ),
            snapshots = listOf(
                BalanceSnapshot(1, today, Money.euros(11000)),
                BalanceSnapshot(2, today, Money.euros(4000)),
            ),
            today = today,
        )
        val progress = holdings.progress(SavingsGoals(precaution = Money.euros(10000), total = Money.euros(20000)))
        assertTrue(progress.precautionReached)
        assertFalse(progress.totalReached)
        assertEquals(Money.euros(5000), progress.totalMissing)
        assertEquals(Money.ZERO, progress.beyond)
    }
}
