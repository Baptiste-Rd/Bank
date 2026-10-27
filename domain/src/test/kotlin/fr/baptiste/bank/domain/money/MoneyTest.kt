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
}
