package fr.baptiste.bank.domain.bank

import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPartsTest {

    @Test
    fun `equal parts lose no cent`() {
        assertEquals(listOf(Money(1100), Money(1100), Money(1100)), equalParts(Money(3300), 3))
        assertEquals(listOf(Money(333), Money(333), Money(334)), equalParts(Money(1000), 3))
        assertTrue(isValidSplit(Money(4567), equalParts(Money(4567), 4)))
    }

    @Test
    fun `percentages make the exact total when they reach 100`() {
        val parts = percentParts(Money(1000), listOf(3333, 3333, 3334))
        assertEquals(Money(1000), parts.fold(Money.ZERO) { sum, part -> sum + part })
        assertEquals(listOf(Money(333), Money(333), Money(334)), parts)
        // Moins de 100 % : chaque part arrondie, le reste n'est pas réparti (l'enregistrement reste bloqué).
        assertEquals(listOf(Money(500), Money(250)), percentParts(Money(1000), listOf(5000, 2500)))
    }
}
