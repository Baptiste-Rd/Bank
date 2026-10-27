package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Le scénario de l'utilisateur, de bout en bout :
 * 1. il a des budgets annuels (vacances, cadeaux) et mensuels, un salaire de 2 400 € ;
 * 2. on est le 15 octobre, il consulte son épargne : septembre s'est bien passé ;
 * 3. il prévoit un vélo à 600 € en mars : 100 € par mois réservés dès octobre ;
 * 4. l'épargne prévue baisse, sous l'objectif et sous septembre ;
 * 5. il déplace le budget sorties de la semaine vers l'épargne.
 */
class SavingsScenarioTest {

    private fun envelope(id: Long, name: String, type: EnvelopeType, euros: Long, frequency: Frequency = type.defaultFrequency) =
        Envelope(id, name, type, frequency, Money.euros(euros), type.defaultStatus)

    private val courses = envelope(1, "Courses", EnvelopeType.DAILY, 320)
    private val sorties = envelope(2, "Sorties", EnvelopeType.DAILY, 25, Frequency.WEEKLY)
    private val loyer = envelope(3, "Loyer", EnvelopeType.FIXED, 700)
    private val vacances = envelope(4, "Vacances", EnvelopeType.ANNUAL, 1800)
    private val cadeaux = envelope(5, "Cadeaux", EnvelopeType.ANNUAL, 600)
    private val epargne = envelope(6, "Épargne", EnvelopeType.SAVINGS, 300)
    private val budgets = listOf(courses, sorties, loyer, vacances, cadeaux, epargne)
    private val income = Money.euros(2400)

    private val spendings = listOf(
        // Septembre : loyer, et 1 100 € de dépenses du quotidien.
        Spending(3, LocalDate.of(2026, 9, 5), Money.euros(700)),
        Spending(1, LocalDate.of(2026, 9, 10), Money.euros(1100)),
        // Octobre, jusqu'au 15 : loyer, et 600 € de quotidien (rythme : 1 240 € sur le mois).
        Spending(3, LocalDate.of(2026, 10, 5), Money.euros(700)),
        Spending(1, LocalDate.of(2026, 10, 8), Money.euros(600)),
    )

    private val today = LocalDate.of(2026, 10, 15)
    private val endOfSeptember = LocalDate.of(2026, 9, 30)

    private fun savings(envelopes: List<Envelope>, date: LocalDate, transfers: List<Transfer> = emptyList()) =
        computeDashboard(envelopes, emptyMap(), spendings, income, date, transfers = transfers).savings

    @Test
    fun `annual budgets are reserved monthly and leave a daily budget of 1200 euros`() {
        val dashboard = computeDashboard(budgets, emptyMap(), spendings, income, today)
        // 2 400 − (320 + 108,34 + 700 + 150 + 50 + 300) = 771,66 € de portefeuille,
        // plus 428,34 € d'enveloppes quotidiennes : 1 200 € pour le quotidien.
        assertEquals(Money(77166), dashboard.unallocated)
        assertEquals(Money.euros(1200), dashboard.dailyBudget)
        val vacancesLine = dashboard.envelopes.first { it.envelope.id == 4L }
        assertEquals("2026", vacancesLine.period.key)
    }

    @Test
    fun `september is final and saved 400 euros, october is below target at the current pace`() {
        val september = savings(budgets, endOfSeptember)
        assertTrue(september.isFinal)
        assertEquals(Money.euros(400), september.forecast) // 300 d'objectif + 100 de reste

        val october = savings(budgets, today)
        assertEquals(Money.euros(-40), october.dailyMargin) // 1 200 − 1 240
        assertEquals(Money.euros(260), october.forecast)
        assertEquals(Money.euros(-40), october.gapToTarget)
    }

    @Test
    fun `a planned purchase reserves its monthly share and lowers the forecast, not september`() {
        val velo = plannedPurchase("Vélo", Money.euros(600), YearMonth.of(2027, 3), today).copy(id = 7)
        assertEquals(Money.euros(100), velo.monthlyShare)
        assertEquals(YearMonth.of(2027, 3), velo.purchaseMonth)
        val withVelo = budgets + velo

        val dashboard = computeDashboard(withVelo, emptyMap(), spendings, income, today)
        assertEquals(Money.euros(1100), dashboard.dailyBudget)
        assertEquals("2026-10-01+6M", dashboard.envelopes.first { it.envelope.id == 7L }.period.key)

        assertEquals(Money.euros(160), dashboard.savings.forecast)
        // Le vélo n'existait pas en septembre : le bilan de septembre ne change pas.
        assertEquals(Money.euros(400), savings(withVelo, endOfSeptember).forecast)
    }

    @Test
    fun `moving the weekly outings budget to savings secures it, the forecast follows spending`() {
        val velo = plannedPurchase("Vélo", Money.euros(600), YearMonth.of(2027, 3), today).copy(id = 7)
        val withVelo = budgets + velo
        val move = Transfer(1, today, sourceEnvelopeId = 2, targetEnvelopeId = 6, amount = Money.euros(25))

        val after = savings(withVelo, today, listOf(move))
        assertEquals(Money.euros(325), after.secured)
        // Même rythme de dépense : les 25 € manquent au quotidien, la prévision ne bouge pas.
        assertEquals(Money.euros(160), after.forecast)
        val sortiesLine = computeDashboard(withVelo, emptyMap(), spendings, income, today, transfers = listOf(move))
            .envelopes.first { it.envelope.id == 2L }
        assertEquals(Money.ZERO, sortiesLine.available)
    }

    @Test
    fun `tells how much can still be spent per day to reach the target`() {
        val velo = plannedPurchase("Vélo", Money.euros(600), YearMonth.of(2027, 3), today).copy(id = 7)
        val move = Transfer(1, today, sourceEnvelopeId = 2, targetEnvelopeId = 6, amount = Money.euros(25))
        val dashboard = computeDashboard(budgets + velo, emptyMap(), spendings, income, today, transfers = listOf(move))
        // 325 − 300 + 1 075 − 600 = 500 € sur 17 jours (du 15 au 31 inclus) : 29,41 € par jour.
        assertEquals(17, dashboard.daysRemaining)
        assertEquals(Money(2941), dashboard.dailyCapForTarget())
        assertEquals(null, computeDashboard(budgets, emptyMap(), spendings, income, endOfSeptember).dailyCapForTarget())
    }

    @Test
    fun `a planned purchase shows what is already set aside`() {
        val velo = plannedPurchase("Vélo", Money.euros(600), YearMonth.of(2027, 3), today)
        assertEquals(Money.euros(100), velo.savedSoFar(today))
        assertEquals(Money.euros(300), velo.savedSoFar(LocalDate.of(2026, 12, 2)))
        assertEquals(Money.euros(600), velo.savedSoFar(LocalDate.of(2027, 8, 1)))
        assertEquals(Money.ZERO, velo.savedSoFar(LocalDate.of(2026, 9, 30)))
    }
}
