package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Plan du mois (Budget) : l'invariant I5 de `docs/produit.md` et la ligne des ajouts (D8). */
class MonthPlanTest {

    private val today = LocalDate.of(2026, 11, 16)

    private fun envelope(id: Long, type: EnvelopeType, euros: Long) = Envelope(
        id = id,
        name = "E$id",
        type = type,
        frequency = type.defaultFrequency,
        amount = Money.euros(euros),
        status = type.defaultStatus,
    )

    private val courses = envelope(1, EnvelopeType.DAILY, 400)
    private val loyer = envelope(2, EnvelopeType.FIXED, 700)
    private val epargne = envelope(3, EnvelopeType.SAVINGS, 300)
    private val salary = Money.euros(2000)

    private fun board(vararg transfers: Transfer) = computeDashboard(
        listOf(courses, loyer, epargne),
        emptyMap(),
        listOf(Spending(courses.id, today.withDayOfMonth(3), Money.euros(120))),
        salary,
        today,
        transfers = transfers.toList(),
    )

    /** 93 € pris sur « Disponible sur le compte » pour le quotidien. */
    private val fromAccount = Transfer(1, today, null, courses.id, Money.euros(93), fromReserve = true)

    /** 40 € pris sur l'enveloppe d'épargne pour le quotidien. */
    private val fromSavings = Transfer(2, today, epargne.id, courses.id, Money.euros(40))

    /** 40 € pris sur À répartir du mois pour l'enveloppe d'épargne. */
    private val toSavings = Transfer(3, today, null, epargne.id, Money.euros(40))

    @Test
    fun `I5 the plan lines add up to the salary when money comes from elsewhere`() {
        val plan = board(fromAccount, fromSavings).monthPlan()
        assertEquals(salary, plan.lines.fold(Money.ZERO) { sum, it -> sum + it })
        assertEquals(Money.euros(1000), plan.daily)
        assertEquals(Money.euros(133), plan.moves)
    }

    @Test
    fun `I5 the plan lines add up to the salary when money leaves the daily budget`() {
        val plan = board(toSavings).monthPlan()
        assertEquals(salary, plan.lines.fold(Money.ZERO) { sum, it -> sum + it })
        assertEquals(Money.euros(-40), plan.moves)
    }

    @Test
    fun `I5 without any move the plan has no moves line`() {
        val plan = board().monthPlan()
        assertEquals(salary, plan.lines.fold(Money.ZERO) { sum, it -> sum + it })
        assertEquals(Money.ZERO, plan.moves)
    }

    @Test
    fun `the plan leaves the daily budget and the month left unchanged`() {
        val before = board(fromAccount, fromSavings)
        val plan = before.monthPlan()
        // Le budget du quotidien (donc le « par jour » et le « Reste du mois ») reste celui du calcul.
        assertEquals(before.dailyBudget, plan.daily + plan.moves)
        assertEquals(Money.euros(1133), before.dailyBudget)
        assertEquals(Money.euros(1013), before.monthLeft)
    }
}
