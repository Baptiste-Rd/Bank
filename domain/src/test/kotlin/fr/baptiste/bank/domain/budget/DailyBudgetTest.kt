package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyBudgetTest {

    private fun envelope(
        type: EnvelopeType,
        scale: Scale,
        euros: Long,
        archived: Boolean = false,
    ) = Envelope(
        id = 0,
        name = type.name,
        type = type,
        frequency = Frequency(1, scale),
        amount = Money.euros(euros),
        status = EnvelopeStatus.FLEXIBLE,
        archived = archived,
    )

    @Test
    fun `scale conversion goes through the yearly equivalent`() {
        assertEquals(Money.euros(15), Money.euros(180).convert(Scale.YEAR, Scale.MONTH))
        assertEquals(Money.euros(1200), Money.euros(100).convert(Scale.MONTH, Scale.YEAR))
        assertEquals(Money(6924), Money.euros(300).convert(Scale.MONTH, Scale.WEEK))
    }

    @Test
    fun `an annual envelope reserves a twelfth of its amount each month`() {
        val cbd = envelope(EnvelopeType.ANNUAL, Scale.YEAR, euros = 240)
        assertEquals(Money.euros(20), cbd.monthlyReservation())
    }

    @Test
    fun `an archived envelope reserves nothing`() {
        val old = envelope(EnvelopeType.FIXED, Scale.MONTH, euros = 50, archived = true)
        assertEquals(Money.ZERO, old.monthlyReservation())
    }

    @Test
    fun `suggested daily budget removes everything except daily envelopes`() {
        val envelopes = listOf(
            envelope(EnvelopeType.FIXED, Scale.MONTH, euros = 400),
            envelope(EnvelopeType.SAVINGS, Scale.MONTH, euros = 300),
            envelope(EnvelopeType.RECURRING, Scale.YEAR, euros = 180),
            envelope(EnvelopeType.ANNUAL, Scale.YEAR, euros = 1200),
            envelope(EnvelopeType.DAILY, Scale.MONTH, euros = 300),
        )
        // 2000 - 400 - 300 - 15 - 100 = 1185 ; l'enveloppe quotidienne n'est pas déduite.
        assertEquals(Money.euros(1185), suggestedDailyBudget(Money.euros(2000), envelopes))
    }

    @Test
    fun `daily allowance spreads what is left over the remaining days`() {
        val result = dailyAllowance(
            monthlyDailyBudget = Money.euros(750),
            spentBeforeToday = Money.euros(410),
            spentToday = Money.ZERO,
            today = LocalDate.of(2026, 10, 18),
        )
        // (750 - 410) / 14 jours = 24,2857 arrondi au centime inférieur.
        assertEquals(Money(2428), result.allowance)
        assertEquals(Money(2428), result.remainingToday)
    }

    @Test
    fun `spending today consumes the allowance without lowering it`() {
        val result = dailyAllowance(
            monthlyDailyBudget = Money.euros(750),
            spentBeforeToday = Money.euros(410),
            spentToday = Money.euros(30),
            today = LocalDate.of(2026, 10, 18),
        )
        assertEquals(Money(2428), result.allowance)
        assertEquals(Money(-572), result.remainingToday)
    }

    @Test
    fun `on the last day the whole remainder is available`() {
        val result = dailyAllowance(
            monthlyDailyBudget = Money.euros(750),
            spentBeforeToday = Money.euros(700),
            spentToday = Money.ZERO,
            today = LocalDate.of(2026, 10, 31),
        )
        assertEquals(Money.euros(50), result.allowance)
    }

    @Test
    fun `overspending earlier in the month gives a negative allowance`() {
        val result = dailyAllowance(
            monthlyDailyBudget = Money.euros(750),
            spentBeforeToday = Money.euros(800),
            spentToday = Money.ZERO,
            today = LocalDate.of(2026, 10, 22),
        )
        // -50 / 10 jours
        assertEquals(Money.euros(-5), result.allowance)
    }

    @Test
    fun `envelope remaining combines allocation, carry over and transfers`() {
        val remaining = envelopeRemaining(
            allocated = Money.euros(100),
            carriedOver = Money.euros(10),
            transfersIn = Money.euros(40),
            transfersOut = Money.euros(5),
            imputed = Money.euros(120),
        )
        assertEquals(Money.euros(25), remaining)
    }
}
