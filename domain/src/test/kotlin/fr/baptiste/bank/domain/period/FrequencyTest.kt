package fr.baptiste.bank.domain.period

import fr.baptiste.bank.domain.budget.convert
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FrequencyTest {

    private val anchor = LocalDate.of(2026, 10, 12)

    @Test
    fun `every six weeks rolls from the anchor`() {
        val sixWeeks = Frequency(6, Scale.WEEK)
        val current = sixWeeks.periodContaining(LocalDate.of(2026, 10, 27), anchor)
        assertEquals(LocalDate.of(2026, 10, 12), current.start)
        assertEquals(LocalDate.of(2026, 11, 22), current.endInclusive)
        assertEquals("2026-10-12+6W", current.key)

        val next = sixWeeks.periodContaining(LocalDate.of(2026, 11, 23), anchor)
        assertEquals(LocalDate.of(2026, 11, 23), next.start)
    }

    @Test
    fun `dates before the anchor fall in earlier periods`() {
        val tenDays = Frequency(10, Scale.DAY)
        val period = tenDays.periodContaining(LocalDate.of(2026, 10, 5), anchor)
        assertEquals(LocalDate.of(2026, 10, 2), period.start)
        assertEquals(LocalDate.of(2026, 10, 11), period.endInclusive)
    }

    @Test
    fun `every two months keeps the anchor day even after a short month`() {
        val twoMonths = Frequency(2, Scale.MONTH)
        val endOfJanuary = LocalDate.of(2026, 1, 31)
        val period = twoMonths.periodContaining(LocalDate.of(2026, 4, 15), endOfJanuary)
        assertEquals(LocalDate.of(2026, 3, 31), period.start)
        assertEquals(LocalDate.of(2026, 5, 30), period.endInclusive)
    }

    @Test
    fun `monthly stays on the calendar month whatever the anchor`() {
        val period = Frequency.MONTHLY.periodContaining(LocalDate.of(2026, 10, 27), anchor)
        assertEquals("2026-10", period.key)
    }

    @Test
    fun `a single day is its own period`() {
        val period = Frequency(1, Scale.DAY).periodContaining(LocalDate.of(2026, 10, 27), anchor)
        assertEquals(1, period.lengthInDays)
        assertEquals("2026-10-27+1D", period.key)
    }

    @Test
    fun `span keys round trip through parse`() {
        val period = Period.parse("2026-10-12+6W")
        assertEquals(LocalDate.of(2026, 11, 22), period.endInclusive)
        assertEquals("2026-10-12+6W", period.key)
    }

    @Test
    fun `amounts convert between frequencies through the year`() {
        val haircut = Money.euros(30)
        // 52 / 6 coupes par an, réparties sur 12 mois : 21,67 €.
        assertEquals(Money(2167), haircut.convert(Frequency(6, Scale.WEEK), Frequency.MONTHLY))
        // 10 € par jour = 304,17 € par mois.
        assertEquals(Money(30417), Money.euros(10).convert(Frequency(1, Scale.DAY), Frequency.MONTHLY))
        assertEquals(Money.euros(50), Money.euros(100).convert(Frequency(2, Scale.MONTH), Frequency.MONTHLY))
    }
}
