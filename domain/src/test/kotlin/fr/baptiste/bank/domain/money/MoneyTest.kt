package fr.baptiste.bank.domain.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `euros builds the amount in cents`() {
        assertEquals(Money(2340), Money.euros(23, 40))
        assertEquals(Money(-2340), Money.euros(-23, 40))
    }

    @Test
    fun `divideFloor rounds toward the lower cent`() {
        assertEquals(Money(2428), Money(34000).divideFloor(14))
        assertEquals(Money(-2429), Money(-34000).divideFloor(14))
    }

    @Test
    fun `divideCeil rounds toward the upper cent`() {
        assertEquals(Money(834), Money(10000).divideCeil(12))
        assertEquals(Money(1500), Money(18000).divideCeil(12))
    }

    @Test
    fun `sum adds every amount`() {
        assertEquals(Money(600), listOf(Money(100), Money(200), Money(300)).sum())
        assertEquals(Money.ZERO, emptyList<Money>().sum())
    }

    @Test
    fun `toString formats with a comma and two decimals`() {
        assertEquals("24,05 €", Money(2405).toString())
        assertEquals("-0,50 €", Money(-50).toString())
    }

    @Test
    fun `I1 a displayed half euro rounds away from zero on both signs`() {
        assertEquals(Money(-2800), Money(-2750).displayed())
        assertEquals(Money(2800), Money(2750).displayed())
        assertEquals(Money(-2700), Money(-2749).displayed())
        assertEquals(-Money(2750).displayed(), Money(-2750).displayed())
    }

    @Test
    fun `displayed keeps the cents below ten euros`() {
        assertEquals(Money(950), Money(950).displayed())
        assertEquals(Money(-950), Money(-950).displayed())
        assertEquals(Money(1000), Money(1000).displayed())
    }
}
