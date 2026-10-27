package fr.baptiste.bank.domain.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodTest {

    private val october = Period.Month(YearMonth.of(2026, 10))

    @Test
    fun `a month runs from the first to the last day`() {
        assertEquals(LocalDate.of(2026, 10, 1), october.start)
        assertEquals(LocalDate.of(2026, 10, 31), october.endInclusive)
        assertEquals(31, october.lengthInDays)
    }

    @Test
    fun `remaining and elapsed days both include the given date`() {
        val day = LocalDate.of(2026, 10, 18)
        assertEquals(14, october.daysRemainingFrom(day))
        assertEquals(18, october.daysElapsedAt(day))
    }

    @Test
    fun `remaining and elapsed days outside the period`() {
        assertEquals(31, october.daysRemainingFrom(LocalDate.of(2026, 9, 30)))
        assertEquals(0, october.daysRemainingFrom(LocalDate.of(2026, 11, 1)))
        assertEquals(0, october.daysElapsedAt(LocalDate.of(2026, 9, 30)))
        assertEquals(31, october.daysElapsedAt(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `contains checks the bounds inclusively`() {
        assertTrue(LocalDate.of(2026, 10, 1) in october)
        assertTrue(LocalDate.of(2026, 10, 31) in october)
        assertFalse(LocalDate.of(2026, 11, 1) in october)
    }

    @Test
    fun `iso week starts on monday`() {
        val week = Period.Week.of(LocalDate.of(2026, 10, 27))
        assertEquals("2026-W44", week.key)
        assertEquals(LocalDate.of(2026, 10, 26), week.start)
        assertEquals(LocalDate.of(2026, 11, 1), week.endInclusive)
    }

    @Test
    fun `iso week of a january date can belong to the previous year`() {
        val week = Period.Week.of(LocalDate.of(2027, 1, 1))
        assertEquals("2026-W53", week.key)
    }

    @Test
    fun `keys round trip through parse`() {
        listOf("2026-10", "2026", "2026-W44").forEach { key ->
            assertEquals(key, Period.parse(key).key)
        }
    }

    @Test
    fun `scale gives the period containing a date`() {
        val date = LocalDate.of(2026, 10, 27)
        assertEquals("2026-10", Scale.MONTH.periodContaining(date).key)
        assertEquals("2026", Scale.YEAR.periodContaining(date).key)
        assertEquals("2026-W44", Scale.WEEK.periodContaining(date).key)
    }
}
