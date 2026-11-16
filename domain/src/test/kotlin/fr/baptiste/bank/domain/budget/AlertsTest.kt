package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** « À surveiller » du Résumé (L11) : les dépassées d'abord, puis par montant. */
class AlertsTest {

    private val today = LocalDate.of(2026, 11, 16)

    private fun envelope(id: Long, name: String, euros: Long) = Envelope(
        id = id,
        name = name,
        type = EnvelopeType.DAILY,
        frequency = EnvelopeType.DAILY.defaultFrequency,
        amount = Money.euros(euros),
        status = EnvelopeType.DAILY.defaultStatus,
    )

    // Trois enveloppes « Prévu » (rythme trop rapide, reste encore positif), rangées avant
    // Transports par ordre alphabétique, puis Transports, dépassé de 28 €.
    private val alimentation = envelope(1, "Alimentation", 300)
    private val bars = envelope(2, "Bars", 100)
    private val courses = envelope(3, "Courses", 200)
    private val transports = envelope(4, "Transports", 100)

    private fun spent(envelope: Envelope, euros: Long) = Spending(envelope.id, today.withDayOfMonth(12), Money.euros(euros))

    private val board = computeDashboard(
        listOf(alimentation, bars, courses, transports),
        emptyMap(),
        listOf(spent(alimentation, 240), spent(bars, 70), spent(courses, 180), spent(transports, 128)),
        null,
        today,
    )

    @Test
    fun `an overspent envelope comes before the projected ones`() {
        val alerts = board.alerts
        assertEquals(4, alerts.size)
        assertTrue(alerts.drop(1).none { it.remaining.isNegative })
        assertEquals("Transports", alerts.first().envelope.name)
    }

    @Test
    fun `the three watched envelopes keep the overspent one`() {
        assertTrue(board.alerts.take(3).any { it.envelope.name == "Transports" })
    }

    @Test
    fun `projected alerts are ordered by expected overrun, largest first`() {
        val overruns = board.alerts.drop(1).map { it.projection.overrun.cents }
        assertEquals(overruns.sortedDescending(), overruns)
    }

    @Test
    fun `two overspent envelopes are ordered by overrun, largest first`() {
        val loisirs = envelope(5, "Loisirs", 50)
        val board = computeDashboard(
            listOf(alimentation, loisirs, transports),
            emptyMap(),
            listOf(spent(alimentation, 240), spent(loisirs, 60), spent(transports, 128)),
            null,
            today,
        )
        assertEquals(listOf("Transports", "Loisirs", "Alimentation"), board.alerts.map { it.envelope.name })
    }
}
