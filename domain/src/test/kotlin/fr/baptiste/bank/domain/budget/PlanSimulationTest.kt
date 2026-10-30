package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlanSimulationTest {

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
    private val pret = envelope(2, EnvelopeType.FIXED, 400)
    private val epargne = envelope(3, EnvelopeType.SAVINGS, 300)
    // Salaire 2 400 € : quotidien 2 400 − 400 − 300 = 1 700 €.
    private val board = computeDashboard(listOf(courses, pret, epargne), emptyMap(), emptyList(), Money.euros(2400), today)

    @Test
    fun `2000 euros in 3 months takes 667 a month from the daily budget`() {
        val result = simulatePlan(board, PlanInput(Money.euros(2000), 3, Money.euros(300)), Money.euros(300), Money.euros(900))
        assertEquals(Money(66667), result.projectShare)
        assertEquals(Money.euros(1700), result.dailyBefore)
        assertEquals(Money(103333), result.dailyAfter)
        assertEquals(PlanVerdict.REALISTIC, result.verdict)
    }

    @Test
    fun `slightly under the usual spending is tight with a daily effort`() {
        // 1 700 − 667 − 0 = 1 033 € pour 1 100 € dépensés d'habitude : serré.
        val result = simulatePlan(board, PlanInput(Money.euros(2000), 3, Money.euros(300)), Money.euros(300), Money.euros(1100))
        assertEquals(PlanVerdict.TIGHT, result.verdict)
        // (1 100 − 1 033,33) / 30, arrondi au centime supérieur.
        assertEquals(Money(223), result.savePerDay)
    }

    @Test
    fun `too much is unrealistic and says when it would be`() {
        // Épargne portée à 500 € : quotidien 1 700 − 667 − 200 = 833 € pour 1 100 € d'habitude.
        val result = simulatePlan(board, PlanInput(Money.euros(2000), 3, Money.euros(500)), Money.euros(300), Money.euros(1100))
        assertEquals(PlanVerdict.UNREALISTIC, result.verdict)
        // Marge : 1 700 − 200 − 1 100 = 400 € par mois, soit 5 mois pour 2 000 €.
        assertEquals(5, result.tenableMonths)
    }

    @Test
    fun `reducing some spending makes the same plan realistic`() {
        // 1 033 € de quotidien pour 1 100 € d'habitude : serré ; avec 80 € de sorties en moins, réaliste.
        val tight = simulatePlan(board, PlanInput(Money.euros(2000), 3, Money.euros(300)), Money.euros(300), Money.euros(1100))
        assertEquals(PlanVerdict.TIGHT, tight.verdict)
        val reduced = simulatePlan(
            board,
            PlanInput(Money.euros(2000), 3, Money.euros(300), reductions = Money.euros(80)),
            Money.euros(300),
            Money.euros(1100),
        )
        assertEquals(PlanVerdict.REALISTIC, reduced.verdict)
        assertEquals(Money.euros(1020), reduced.reference)
    }

    @Test
    fun `the reference is the daily spending of finished tracked months`() {
        val spendings = listOf(
            Spending(courses.id, LocalDate.of(2026, 9, 3), Money.euros(600)),
            Spending(courses.id, LocalDate.of(2026, 8, 3), Money.euros(800)),
            Spending(pret.id, LocalDate.of(2026, 9, 5), Money.euros(400)),
            Spending(courses.id, LocalDate.of(2026, 7, 3), Money.euros(999)),
            Spending(courses.id, LocalDate.of(2026, 10, 3), Money.euros(50)),
        )
        // Suivi depuis août : juillet n'est pas renseigné ; octobre n'est pas terminé.
        val history = dailySpendingHistory(listOf(courses, pret, epargne), spendings, YearMonth.of(2026, 10), YearMonth.of(2026, 8))
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8)), history.map { it.month })
        assertEquals(Money.euros(700), history.average())
        assertEquals(null, emptyList<MonthDailySpending>().average())
    }
}
