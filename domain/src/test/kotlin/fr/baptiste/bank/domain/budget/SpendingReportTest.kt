package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SpendingReportTest {

    private fun envelope(id: Long, name: String, type: EnvelopeType, euros: Long, category: String? = null, archived: Boolean = false) =
        Envelope(
            id = id,
            name = name,
            type = type,
            frequency = type.defaultFrequency,
            amount = Money.euros(euros),
            status = type.defaultStatus,
            group = category,
            archived = archived,
        )

    private val courses = envelope(1, "Courses", EnvelopeType.DAILY, 320, "Nourriture")
    private val bar = envelope(2, "Bar", EnvelopeType.DAILY, 60, "Nourriture")
    private val essence = envelope(3, "Essence", EnvelopeType.DAILY, 120, "Transports")
    private val vacances = envelope(4, "Vacances", EnvelopeType.ANNUAL, 1200, "Voyages")
    private val epargne = envelope(5, "Épargne", EnvelopeType.SAVINGS, 300)
    private val divers = envelope(6, "Divers", EnvelopeType.DAILY, 50)
    private val ancienne = envelope(7, "Ancienne", EnvelopeType.DAILY, 40, "Loisirs", archived = true)
    private val all = listOf(courses, bar, essence, vacances, epargne, divers, ancienne)

    private val october = YearMonth.of(2026, 10)
    private fun spent(envelope: Envelope, day: Int, euros: Long, month: YearMonth = october) =
        Spending(envelope.id, month.atDay(day), Money.euros(euros))

    private val spendings = listOf(
        spent(courses, 3, 200), spent(courses, 20, 150), spent(bar, 12, 30),
        spent(essence, 8, 60),
        // Un remboursement réduit la dépense.
        spent(essence, 15, -10),
        spent(vacances, 10, 400),
        // L'épargne n'est pas une dépense.
        spent(epargne, 2, 300),
        spent(ancienne, 4, 25),
        // Un autre mois ne compte pas.
        spent(courses, 28, 999, YearMonth.of(2026, 9)),
    )

    @Test
    fun `a month adds real spending by category, savings excluded`() {
        val report = monthSpending(october, all, spendings, unsorted = Money.euros(45))
        val names = report.categories.map { it.name }
        // Les plus grosses dépenses en premier ; une enveloppe sans catégorie est dans « Autres ».
        assertEquals(listOf("Voyages", "Nourriture", "Transports", "Loisirs", "Autres"), names)
        val food = report.categories.single { it.name == "Nourriture" }
        assertEquals(Money.euros(380), food.spent)
        assertEquals(Money.euros(380), food.budget)
        assertEquals(listOf("Courses", "Bar"), food.envelopes.map { it.envelope.name })
        assertEquals(Money.euros(50), report.categories.single { it.name == "Transports" }.spent)
        // Total : 380 + 50 + 400 + 25 ; l'épargne et le mois précédent n'y sont pas, ni le « à trier ».
        assertEquals(Money.euros(855), report.total)
        assertEquals(Money.euros(45), report.unsorted)
    }

    @Test
    fun `the budget is the monthly share of active envelopes`() {
        val report = monthSpending(october, all, spendings)
        // Une provision annuelle de 1 200 € compte 100 € par mois ; une archivée n'a plus de budget.
        val trips = report.categories.single { it.name == "Voyages" }
        assertEquals(Money.euros(100), trips.budget)
        assertEquals(Money.euros(300), trips.over)
        assertEquals(Money.ZERO, report.categories.single { it.name == "Loisirs" }.budget)
        // Divers n'a rien dépensé mais garde son budget.
        assertEquals(Money.euros(50), report.categories.single { it.name == "Autres" }.budget)
    }

    @Test
    fun `untracked months are empty and left out of the year`() {
        val today = LocalDate.of(2026, 10, 29)
        val september = YearMonth.of(2026, 9)
        val year = yearSpending(2026, today, trackingStart = september, envelopes = all, spendings = spendings)
        assertEquals(10, year.months.size)
        assertFalse(year.months.first().tracked)
        assertTrue(year.months.first().categories.isEmpty())
        assertEquals(2, year.tracked.size)
        // Septembre : 999 € de courses ; octobre : 855 €.
        assertEquals(Money.euros(1854), year.total)
        assertEquals(Money.euros(927), year.monthlyAverage)
        val food = year.categories().first { it.name == "Nourriture" }
        assertEquals(Money.euros(1379), food.total)
        assertEquals(Money(68950), food.average)
        assertEquals(Money.euros(760), food.budget)
        assertEquals(Money.euros(619), food.gap)
    }
}
