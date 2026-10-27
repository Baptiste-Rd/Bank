package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ProjectionTest {

    private val october = Period.Month(YearMonth.of(2026, 10))

    @Test
    fun `projection extrapolates the current pace to the end of the period`() {
        // 80 € d'essence au 15 octobre : 80 x 31 / 15 = 165,34 €.
        val result = project(
            spent = Money.euros(80),
            available = Money.euros(100),
            period = october,
            today = LocalDate.of(2026, 10, 15),
        )
        assertEquals(Money(16534), result.projected)
        assertEquals(Money(6534), result.overrun)
        assertTrue(result.isAlert)
    }

    @Test
    fun `a small overrun under the threshold raises no alert`() {
        // 52 € au 16 octobre : 52 x 31 / 16 = 100,75 €, soit 0,75 € au-dessus de 100 €.
        val result = project(
            spent = Money.euros(52),
            available = Money.euros(100),
            period = october,
            today = LocalDate.of(2026, 10, 16),
        )
        assertEquals(Money(75), result.overrun)
        assertFalse(result.isAlert)
    }

    @Test
    fun `no spending means no alert`() {
        val result = project(
            spent = Money.ZERO,
            available = Money.euros(100),
            period = october,
            today = LocalDate.of(2026, 10, 10),
        )
        assertEquals(Money.ZERO, result.projected)
        assertFalse(result.isAlert)
    }

    @Test
    fun `any spending on an empty envelope raises an alert`() {
        val result = project(
            spent = Money.euros(1),
            available = Money.ZERO,
            period = october,
            today = LocalDate.of(2026, 10, 10),
        )
        assertTrue(result.isAlert)
    }

    @Test
    fun `after the period the projection equals the actual spending`() {
        val result = project(
            spent = Money.euros(90),
            available = Money.euros(100),
            period = october,
            today = LocalDate.of(2026, 11, 2),
        )
        assertEquals(Money.euros(90), result.projected)
        assertFalse(result.isAlert)
    }
}
