package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Le tableau d'un mois daté (dashboardAt) et la cohérence des chiffres entre écrans : un même nom
 * donne le même nombre au Résumé, dans Budget, dans Dépenses et dans l'Épargne.
 */
class MonthBoardTest {

    private val november = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 15)

    private fun envelope(id: Long, type: EnvelopeType, euros: Long, name: String = "E$id", status: EnvelopeStatus = type.defaultStatus) =
        Envelope(id = id, name = name, type = type, frequency = type.defaultFrequency, amount = Money.euros(euros), status = status)

    private val food = envelope(1, EnvelopeType.DAILY, 400, "Courses")
    private val rent = envelope(2, EnvelopeType.FIXED, 800, "Loyer")
    private val bar = envelope(3, EnvelopeType.DAILY, 100, "Bar")
    private val hardware = envelope(4, EnvelopeType.DAILY, 50, "Quincaillerie", EnvelopeStatus.FLEXIBLE)
    private val savings = envelope(5, EnvelopeType.SAVINGS, 300, "Livret")

    private fun MonthSpending.lines() = categories.flatMap { listOf(it) + it.descendants }.flatMap { it.envelopes }

    private fun spent(envelope: Envelope, date: LocalDate, euros: Long) = Spending(envelope.id, date, Money.euros(euros))

    private val salaryKeyValue = salaryKey("VIR SEPA RECU /DE SOCIETE EXEMPLE /MOTIF SALAIRE")
    private fun salary(date: LocalDate, euros: Long) =
        Inflow("VIR SEPA RECU /DE SOCIETE EXEMPLE /MOTIF SALAIRE", date, Money.euros(euros))

    private fun data(
        envelopes: List<Envelope>,
        spendings: List<Spending> = emptyList(),
        transfers: List<Transfer> = emptyList(),
        inflows: List<Inflow> = emptyList(),
    ) = BudgetData(
        envelopes = envelopes,
        allocations = emptyMap(),
        spendings = spendings,
        transfers = transfers,
        inflows = inflows,
        salary = salaryKeyValue to true,
        typedIncome = null,
        trackingStart = YearMonth.of(2026, 9),
    )

    @Test
    fun `an envelope stopped mid-month keeps its budget and spending in that month`() {
        // Loyer payé le 3, enveloppe arrêtée le 5 : le 15, le reste du mois ne gagne pas 800 €.
        val stoppedRent = rent.copy(archived = true, retiredOn = LocalDate.of(2026, 11, 5))
        val spendings = listOf(spent(rent, LocalDate.of(2026, 11, 3), 800), spent(food, LocalDate.of(2026, 11, 10), 120))
        val inflows = listOf(salary(LocalDate.of(2026, 10, 28), 2400))
        val kept = dashboardAt(data(listOf(food, rent), spendings, inflows = inflows), today)
        val stopped = dashboardAt(data(listOf(food, stoppedRent), spendings, inflows = inflows), today)
        assertEquals(kept.dailyBudget, stopped.dailyBudget)
        assertEquals(kept.dailyAllowance.allowance, stopped.dailyAllowance.allowance)
        assertEquals(Money.euros(800), stopped.envelopes.single { it.envelope.id == rent.id }.spent)
        // Le mois suivant, elle n'y figure plus.
        val december = dashboardAt(data(listOf(food, stoppedRent), spendings, inflows = inflows), LocalDate.of(2026, 12, 10))
        assertTrue(december.envelopes.none { it.envelope.id == rent.id })
        // Ses dépenses restent aussi dans Dépenses ce mois-là, avec son budget.
        val report = monthSpending(november, listOf(food, stoppedRent), spendings)
        assertEquals(Money.euros(800), report.lines().single { it.envelope.id == rent.id }.budget)
    }

    @Test
    fun `a stopped daily envelope no longer projects spending`() {
        val stoppedBar = bar.copy(archived = true, retiredOn = LocalDate.of(2026, 11, 5))
        val spendings = listOf(spent(bar, LocalDate.of(2026, 11, 2), 60))
        val board = dashboardAt(data(listOf(food, stoppedBar), spendings), today)
        val line = board.envelopes.single { it.envelope.id == bar.id }
        assertEquals(Money.euros(60), line.projection.projected)
    }

    @Test
    fun `each month is computed with its own salary`() {
        // Salaire versé fin de mois pour le mois suivant : 2 400 € fin septembre, 2 900 € fin octobre (prime).
        val inflows = listOf(salary(LocalDate.of(2026, 9, 28), 2400), salary(LocalDate.of(2026, 10, 28), 2900))
        val budget = data(listOf(food, rent), inflows = inflows)
        assertEquals(Money.euros(2400), budget.incomeOf(YearMonth.of(2026, 10)))
        assertEquals(Money.euros(2900), budget.incomeOf(november))
        val october = dashboardAt(budget, LocalDate.of(2026, 10, 31))
        val current = dashboardAt(budget, today)
        assertEquals(Money.euros(2400), october.income)
        assertEquals(Money.euros(2900), current.income)
        // Le bilan d'octobre ne gagne pas les 500 € de la prime de novembre.
        assertEquals(Money.euros(500), current.unallocated!! - october.unallocated!!)
    }

    @Test
    fun `the month's savings use spending habits, the same figure everywhere`() {
        // Douze mois d'habitude à 300 € de courses : le 3 du mois, l'épargne prévue en tient compte.
        val history = (1..12L).map { spent(food, november.minusMonths(it).atDay(10), 300) }
        val budget = data(listOf(food, rent), history, inflows = listOf(salary(LocalDate.of(2026, 10, 28), 2400)))
        val early = LocalDate.of(2026, 11, 3)
        val board = dashboardAt(budget, early)
        val withoutHabits = computeDashboard(budget.envelopes, budget.allocations, budget.spendings, budget.incomeOf(november), early, trackingStart = budget.trackingStart)
        assertTrue(board.savings.forecast != withoutHabits.savings.forecast)
        // L'épargne mois par mois (savingsByMonth) donne, pour chaque mois, le chiffre de son tableau.
        val dates = listOf(YearMonth.of(2026, 9).atEndOfMonth(), YearMonth.of(2026, 10).atEndOfMonth(), early)
        val months = savingsByMonth(budget, dates, november)
        assertEquals(board.savings, months.last().outlook)
        assertEquals(dashboardAt(budget, dates[1]).savings, months[1].outlook)
        // Un mois d'avant le début du suivi (septembre) n'y est pas compté comme « tout épargné ».
        val later = budget.copy(trackingStart = YearMonth.of(2026, 10))
        assertEquals(listOf(YearMonth.of(2026, 10), november), savingsByMonth(later, dates, november).map { it.month })
    }

    @Test
    fun `the first tracked month takes the first salary paid after it`() {
        // Données à partir de septembre : le salaire de septembre est tombé fin août, avant elles.
        val budget = data(listOf(food, rent), inflows = listOf(salary(LocalDate.of(2026, 9, 28), 2400), salary(LocalDate.of(2026, 10, 28), 2450)))
        assertEquals(Money.euros(2400), dashboardAt(budget, YearMonth.of(2026, 9).atEndOfMonth()).income)
    }

    @Test
    fun `money taken on the account does not count as the month's savings`() {
        // Sorties (quotidien, 50 €) dépasse de 30 € ; on couvre depuis « Disponible sur le compte ».
        val spendings = listOf(spent(hardware, LocalDate.of(2026, 11, 8), 80))
        val inflows = listOf(salary(LocalDate.of(2026, 10, 28), 2400))
        val before = dashboardAt(data(listOf(food, hardware), spendings, inflows = inflows), today)
        val cover = Transfer(1, LocalDate.of(2026, 11, 9), sourceEnvelopeId = null, targetEnvelopeId = hardware.id, amount = Money.euros(30), fromReserve = true)
        val after = dashboardAt(data(listOf(food, hardware), spendings, listOf(cover), inflows), today)
        assertEquals(Money.ZERO, after.envelopes.single { it.envelope.id == hardware.id }.remaining)
        assertEquals(before.savings.forecast, after.savings.forecast)
        assertEquals(Money.euros(30), after.savings.fromAccount)
        // Même chose pour une charge qui se termine ce mois-ci.
        val rentOver = listOf(spent(rent, LocalDate.of(2026, 11, 5), 830))
        val rentBefore = dashboardAt(data(listOf(food, rent), rentOver, inflows = inflows), today)
        val rentCover = cover.copy(targetEnvelopeId = rent.id)
        val rentAfter = dashboardAt(data(listOf(food, rent), rentOver, listOf(rentCover), inflows), today)
        assertEquals(rentBefore.savings.forecast, rentAfter.savings.forecast)
    }

    @Test
    fun `a stopped envelope is shown but never asks for action`() {
        val stoppedBar = bar.copy(archived = true, retiredOn = LocalDate.of(2026, 11, 5))
        val spendings = listOf(spent(bar, LocalDate.of(2026, 11, 2), 160), spent(hardware, LocalDate.of(2026, 11, 8), 80))
        val board = dashboardAt(data(listOf(food, stoppedBar, hardware), spendings), today)
        val line = board.envelopes.single { it.envelope.id == bar.id }
        assertTrue(line.stopped && line.remaining.isNegative)
        assertTrue(board.alerts.none { it.envelope.id == bar.id })
        val target = board.envelopes.single { it.envelope.id == hardware.id }
        assertTrue(board.coverSources(target).none { it.envelope.id == bar.id })
    }

    @Test
    fun `a covered overrun is no longer over in Dépenses, as in Budget`() {
        // Quincaillerie : 50 € prévus, 80 € dépensés, 30 € ajoutés depuis Bar.
        val spendings = listOf(spent(hardware, LocalDate.of(2026, 11, 8), 80))
        val cover = Transfer(1, LocalDate.of(2026, 11, 9), sourceEnvelopeId = bar.id, targetEnvelopeId = hardware.id, amount = Money.euros(30))
        val board = dashboardAt(data(listOf(food, bar, hardware), spendings, listOf(cover)), today)
        val line = board.envelopes.single { it.envelope.id == hardware.id }
        assertEquals(Money.ZERO, line.remaining)
        val report = monthSpending(november, listOf(food, bar, hardware), spendings, transfers = listOf(cover))
        val spentLine = report.lines().single { it.envelope.id == hardware.id }
        assertEquals(line.available, spentLine.budget)
        assertTrue(spentLine.spent <= spentLine.budget)
        // Et Bar perd ce qu'il a donné, des deux côtés.
        val barLine = report.lines().firstOrNull { it.envelope.id == bar.id }
        assertEquals(board.envelopes.single { it.envelope.id == bar.id }.available, barLine?.budget)
    }

    @Test
    fun `month spent is the same in the summary, the analysis and the running total`() {
        // Une dépense répartie : 60 € de courses, 40 € vers le livret (l'épargne n'est pas une dépense).
        val day = LocalDate.of(2026, 11, 4)
        val spendings = listOf(
            spent(food, day, 60),
            spent(savings, day, 40),
            spent(rent, LocalDate.of(2026, 11, 5), 800),
            spent(food, LocalDate.of(2026, 11, 12), 25),
        )
        val envelopes = listOf(food, rent, savings)
        val report = monthSpending(november, envelopes, spendings)
        val running = cumulativeByDay(november, envelopes, spendings)
        val pace = monthPace(november, today, countedSpendings(november, envelopes, spendings).map { it.date to it.amount }, report.budget)
        assertEquals(Money.euros(885), report.total)
        assertEquals(report.total, running.values.last())
        assertEquals(report.total, pace.spent)
        assertEquals(Money.euros(60), running[day])
    }

    @Test
    fun `next month's salary already received is not available on the account`() {
        val inflows = listOf(salary(LocalDate.of(2026, 10, 28), 2400), salary(LocalDate.of(2026, 11, 27), 2450))
        assertEquals(Money.euros(2450), nextMonthSalaryReceived(LocalDate.of(2026, 11, 29), inflows, salaryKeyValue, true))
        // Avant son arrivée, ou pour un salaire du mois même : rien à retirer.
        assertEquals(Money.ZERO, nextMonthSalaryReceived(LocalDate.of(2026, 11, 20), inflows, salaryKeyValue, true))
        assertEquals(Money.ZERO, nextMonthSalaryReceived(LocalDate.of(2026, 11, 29), inflows, salaryKeyValue, false))
        val board = dashboardAt(data(listOf(food), inflows = inflows), LocalDate.of(2026, 11, 29))
        val explained = board.explainCurrentAccount(Money.euros(3000), LocalDate.of(2026, 11, 29), Money.ZERO, nextMonthSalary = Money.euros(2450))
        val without = board.explainCurrentAccount(Money.euros(3000), LocalDate.of(2026, 11, 29), Money.ZERO)
        assertEquals(Money.euros(2450), without.free - explained.free)
    }

    @Test
    fun `a cover can start from the chosen source`() {
        val spendings = listOf(spent(hardware, LocalDate.of(2026, 11, 8), 80))
        val board = dashboardAt(data(listOf(food, bar, hardware), spendings, inflows = listOf(salary(LocalDate.of(2026, 10, 28), 2400))), today)
        val line = board.envelopes.single { it.envelope.id == hardware.id }
        // Par défaut : le non réparti d'abord, la réserve jamais.
        val auto = board.coveragePlan(line, reserve = Money.euros(500))!!
        assertTrue(auto.legs.none { it.fromReserve })
        assertEquals(null, auto.legs.first().sourceEnvelopeId)
        // Choisi : « Disponible sur le compte », puis Bar.
        val fromAccount = board.coveragePlan(line, CoverSource.Reserve, reserve = Money.euros(500))!!
        assertTrue(fromAccount.legs.single().fromReserve)
        assertEquals(auto.needed, fromAccount.legs.single().amount)
        val fromBar = board.coveragePlan(line, CoverSource.FromEnvelope(bar.id))!!
        assertEquals(bar.id, fromBar.legs.first().sourceEnvelopeId)
        assertEquals(Money.ZERO, fromBar.shortfall)
    }

    @Test
    fun `money taken on the account for a provision counts when its period ends`() {
        // Vacances (1 200 € par an) : 100 € pris sur le compte en mars, 1 300 € dépensés en décembre.
        val trip = envelope(9, EnvelopeType.ANNUAL, 1200, "Vacances")
        val endOfYear = LocalDate.of(2026, 12, 31)
        val spendings = listOf(spent(trip, LocalDate.of(2026, 12, 10), 1300))
        val budget = data(listOf(food, trip), spendings).copy(trackingStart = YearMonth.of(2026, 1))
        val before = dashboardAt(budget, endOfYear)
        val march = Transfer(1, LocalDate.of(2026, 3, 2), sourceEnvelopeId = null, targetEnvelopeId = trip.id, amount = Money.euros(100), fromReserve = true)
        val after = dashboardAt(budget.copy(transfers = listOf(march)), endOfYear)
        assertEquals(before.savings.forecast, after.savings.forecast)
        assertEquals(Money.euros(100), after.savings.fromAccount)
    }
}
