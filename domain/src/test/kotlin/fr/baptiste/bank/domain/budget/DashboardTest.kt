package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardTest {

    private val today = LocalDate.of(2026, 10, 18)

    private fun envelope(id: Long, type: EnvelopeType, euros: Long, scale: Scale = type.defaultScale) =
        Envelope(
            id = id,
            name = "E$id",
            type = type,
            scale = scale,
            amount = Money.euros(euros),
            status = type.defaultStatus,
        )

    private val food = envelope(1, EnvelopeType.DAILY, euros = 450)
    private val fuel = envelope(2, EnvelopeType.DAILY, euros = 300)
    private val loan = envelope(3, EnvelopeType.FIXED, euros = 400)
    private val projects = envelope(4, EnvelopeType.ANNUAL, euros = 1200)

    private fun spending(envelope: Envelope, day: LocalDate, euros: Long) =
        Spending(envelope.id, day, Money.euros(euros))

    @Test
    fun `daily allowance uses only daily envelopes of the current month`() {
        val spendings = listOf(
            spending(food, LocalDate.of(2026, 10, 3), 250),
            spending(fuel, LocalDate.of(2026, 10, 10), 160),
            spending(food, today, 12),
            // Hors du mois ou hors quotidien : ignorés pour le disponible du jour.
            spending(food, LocalDate.of(2026, 9, 28), 80),
            spending(projects, LocalDate.of(2026, 10, 5), 100),
        )
        val dashboard = computeDashboard(
            envelopes = listOf(food, fuel, loan, projects),
            allocations = emptyMap(),
            spendings = spendings,
            monthlyIncome = null,
            today = today,
        )
        assertEquals(Money.euros(750), dashboard.dailyBudget)
        // (750 - 410) / 14 = 24,28 ; 12 € déjà dépensés aujourd'hui.
        assertEquals(Money(2428), dashboard.dailyAllowance.allowance)
        assertEquals(Money(1228), dashboard.dailyAllowance.remainingToday)
        assertEquals(null, dashboard.unallocated)
    }

    @Test
    fun `an annual purchase stays out of the daily allowance`() {
        val bike = spending(projects, LocalDate.of(2026, 10, 5), 100)
        val dashboard = computeDashboard(
            envelopes = listOf(food, projects),
            allocations = emptyMap(),
            spendings = listOf(bike),
            monthlyIncome = null,
            today = today,
        )
        val projectLine = dashboard.envelopes.single { it.envelope.id == projects.id }
        assertEquals(Money.euros(1100), projectLine.remaining)
        assertEquals(Money(450_00L / 14), dashboard.dailyAllowance.allowance)
    }

    @Test
    fun `unallocated is what the income leaves after every envelope`() {
        val dashboard = computeDashboard(
            envelopes = listOf(food, fuel, loan, projects),
            allocations = emptyMap(),
            spendings = emptyList(),
            monthlyIncome = Money.euros(2000),
            today = today,
        )
        // 2000 - 400 (prêt) - 100 (projets / 12) = 1500 possibles pour le quotidien.
        assertEquals(Money.euros(1500), dashboard.suggestedDailyBudget)
        // 1500 - 750 alloués au quotidien.
        assertEquals(Money.euros(750), dashboard.unallocated)
    }

    @Test
    fun `a period allocation overrides the default amount`() {
        val dashboard = computeDashboard(
            envelopes = listOf(food),
            allocations = mapOf((food.id to "2026-10") to Money.euros(400)),
            spendings = emptyList(),
            monthlyIncome = null,
            today = today,
        )
        assertEquals(Money.euros(400), dashboard.envelopes.single().available)
        assertEquals(Money.euros(400), dashboard.dailyBudget)
    }

    @Test
    fun `archived envelopes are ignored`() {
        val dashboard = computeDashboard(
            envelopes = listOf(food, fuel.copy(archived = true)),
            allocations = emptyMap(),
            spendings = emptyList(),
            monthlyIncome = null,
            today = today,
        )
        assertEquals(1, dashboard.envelopes.size)
        assertEquals(Money.euros(450), dashboard.dailyBudget)
    }

    @Test
    fun `fast spending raises an alert`() {
        val dashboard = computeDashboard(
            envelopes = listOf(fuel),
            allocations = emptyMap(),
            spendings = listOf(spending(fuel, LocalDate.of(2026, 10, 2), 250)),
            monthlyIncome = null,
            today = today,
        )
        assertTrue(dashboard.alerts.single().envelope.id == fuel.id)
    }

    @Test
    fun `a catch-up expense counts as already spent, not as spent today`() {
        // 350 € de courses rattrapées pour octobre, saisies le 18.
        val catchUp = Spending(food.id, today, Money.euros(350), wholePeriod = true)
        val dashboard = computeDashboard(
            envelopes = listOf(food),
            allocations = emptyMap(),
            spendings = listOf(catchUp),
            monthlyIncome = null,
            today = today,
        )
        // (450 - 350) / 14 jours = 7,14 € ; rien n'est décompté d'aujourd'hui.
        assertEquals(Money(714), dashboard.dailyAllowance.allowance)
        assertEquals(Money(714), dashboard.dailyAllowance.remainingToday)
        assertEquals(Money.euros(350), dashboard.catchUpSpent)
        assertEquals(Money.ZERO, dashboard.monthDays.single { it.isToday }.spent)
    }

    @Test
    fun `a catch-up expense is not extrapolated in the projection`() {
        val catchUp = Spending(food.id, today, Money.euros(350), wholePeriod = true)
        val dashboard = computeDashboard(
            envelopes = listOf(food),
            allocations = emptyMap(),
            spendings = listOf(catchUp),
            monthlyIncome = null,
            today = today,
        )
        val line = dashboard.envelopes.single()
        assertEquals(Money.euros(350), line.projection.projected)
        assertTrue(dashboard.alerts.isEmpty())
    }

    @Test
    fun `month strip lists every day with dated daily spending`() {
        val dashboard = computeDashboard(
            envelopes = listOf(food, projects),
            allocations = emptyMap(),
            spendings = listOf(
                spending(food, LocalDate.of(2026, 10, 3), 20),
                spending(food, LocalDate.of(2026, 10, 3), 5),
                spending(projects, LocalDate.of(2026, 10, 3), 100),
            ),
            monthlyIncome = null,
            today = today,
        )
        assertEquals(31, dashboard.monthDays.size)
        assertEquals(Money.euros(25), dashboard.monthDays[2].spent)
        assertTrue(dashboard.monthDays[18].isFuture)
        assertEquals(14, dashboard.daysRemaining)
        // 450 / 31 = 14,51 €
        assertEquals(Money(1451), dashboard.averageDailyShare)
    }

    @Test
    fun `date range covers the year and a week overlapping it`() {
        val range = dashboardDateRange(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), range.start)
        assertEquals(LocalDate.of(2027, 12, 31), range.endInclusive)
    }
}
