package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TrackingStartTest {

    private val today = LocalDate.of(2026, 11, 4)
    private val firstDate = LocalDate.of(2026, 7, 27)
    private val july = YearMonth.of(2026, 7)
    private val august = YearMonth.of(2026, 8)

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
    fun `data starting on the 27th starts tracking the next month`() {
        assertEquals(august, defaultTrackingStart(firstDate, today))
        assertTrue(isPartialFirstMonth(july, firstDate))
        assertFalse(isPartialFirstMonth(august, firstDate))
    }

    @Test
    fun `data starting in the first days keeps its month`() {
        assertEquals(july, defaultTrackingStart(LocalDate.of(2026, 7, 5), today))
        assertFalse(isPartialFirstMonth(july, LocalDate.of(2026, 7, 5)))
    }

    @Test
    fun `the current month is never excluded`() {
        assertEquals(YearMonth.of(2026, 11), defaultTrackingStart(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25)))
        assertEquals(YearMonth.of(2026, 11), defaultTrackingStart(null, today))
    }

    @Test
    fun `the partial month weighs on no average`() {
        val start = defaultTrackingStart(firstDate, today)
        // Dépense habituelle : 300 € par mois, pas (60 + 900) / 4 = 240 €.
        assertEquals(Money.euros(300), dailyHabits(listOf(courses), spendings, today, start)[1L])
        // Montant proposé : la même moyenne, sur 3 mois.
        val proposed = suggestedAmount(spendings.map { it.date to it.amount }, Frequency.MONTHLY, today, start)
        assertEquals(3, proposed?.months)
        assertEquals(Money.euros(300), proposed?.monthlyAverage)
        // Année : juillet est non renseigné, la moyenne des mois terminés reste 300 €.
        val year = yearSpending(2026, today, trackingStart = start, envelopes = listOf(courses), spendings = spendings)
        assertFalse(year.months.first { it.month == july }.tracked)
        assertEquals(Money.euros(300), year.monthlyAverage)
    }

    @Test
    fun `counting the partial month on purpose brings it back`() {
        // « Le compter quand même » : le suivi choisi commence en juillet.
        val year = yearSpending(2026, today, trackingStart = july, envelopes = listOf(courses), spendings = spendings)
        assertTrue(year.months.first { it.month == july }.tracked)
        assertEquals(Money.euros(240), year.monthlyAverage)
    }
}
