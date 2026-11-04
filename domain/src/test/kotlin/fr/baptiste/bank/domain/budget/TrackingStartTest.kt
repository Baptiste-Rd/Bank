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

/** Chaque mois a son option « compter dans les moyennes » ; un mois incomplet est écarté par défaut. */
class TrackingStartTest {

    private val today = LocalDate.of(2026, 11, 4)
    private val firstDate = LocalDate.of(2026, 7, 27)
    private val july = YearMonth.of(2026, 7)
    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)

    private val courses = Envelope(
        id = 1,
        name = "Courses",
        type = EnvelopeType.DAILY,
        frequency = EnvelopeType.DAILY.defaultFrequency,
        amount = Money.euros(320),
        status = EnvelopeType.DAILY.defaultStatus,
        group = "Nourriture",
    )

    // Données du 27 juillet : 60 € en 5 jours de juillet, puis 300 € par mois d'août à octobre.
    private val spendings = listOf(
        Spending(1, LocalDate.of(2026, 7, 28), Money.euros(60)),
        Spending(1, LocalDate.of(2026, 8, 10), Money.euros(300)),
        Spending(1, LocalDate.of(2026, 9, 10), Money.euros(300)),
        Spending(1, LocalDate.of(2026, 10, 10), Money.euros(300)),
    )

    @Test
    fun `an incomplete month is left out by default, a complete one is counted`() {
        assertEquals(setOf(july), excludedMonths(emptyMap(), firstDate))
        assertEquals(emptySet<YearMonth>(), excludedMonths(emptyMap(), LocalDate.of(2026, 7, 5)))
        assertEquals(emptySet<YearMonth>(), excludedMonths(emptyMap(), null))
    }

    @Test
    fun `every month has its own option, which wins over the default`() {
        // Compter juillet quand même, écarter septembre (un mois de vacances).
        assertEquals(setOf(september), excludedMonths(mapOf(july to true, september to false), firstDate))
        // Remettre septembre.
        assertEquals(setOf(july), excludedMonths(mapOf(september to true), firstDate))
    }

    @Test
    fun `a month left out weighs on no average`() {
        val excluded = excludedMonths(emptyMap(), firstDate)
        // Dépense habituelle : 300 € par mois, pas (60 + 900) / 4 = 240 €.
        assertEquals(Money.euros(300), dailyHabits(listOf(courses), spendings, today, july, excluded)[1L])
        // Montant proposé : la même moyenne, sur 3 mois.
        val proposed = suggestedAmount(spendings.map { it.date to it.amount }, Frequency.MONTHLY, today, july, excluded = excluded)
        assertEquals(3, proposed?.months)
        assertEquals(Money.euros(300), proposed?.monthlyAverage)
        // Année : juillet reste visible avec ses 60 €, mais la moyenne des mois terminés reste 300 €.
        val year = yearSpending(2026, today, trackingStart = july, envelopes = listOf(courses), spendings = spendings, excluded = excluded)
        assertTrue(year.months.first { it.month == july }.tracked)
        assertEquals(Money.euros(60), year.months.first { it.month == july }.total)
        assertEquals(Money.euros(960), year.total)
        assertEquals(Money.euros(300), year.monthlyAverage)
        assertEquals(Money.euros(300), year.categories().first().average)
    }

    @Test
    fun `counting a month again brings it back into the averages`() {
        val year = yearSpending(2026, today, trackingStart = july, envelopes = listOf(courses), spendings = spendings, excluded = excludedMonths(mapOf(july to true), firstDate))
        assertEquals(Money.euros(240), year.monthlyAverage)
        // Et n'importe quel mois peut être écarté : sans août, (60 + 300 + 300) / 3.
        val withoutAugust = yearSpending(2026, today, july, listOf(courses), spendings, excluded = setOf(august))
        assertEquals(Money.euros(220), withoutAugust.monthlyAverage)
    }
}
