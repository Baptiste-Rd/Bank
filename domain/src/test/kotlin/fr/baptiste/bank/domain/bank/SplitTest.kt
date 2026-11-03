package fr.baptiste.bank.domain.bank

import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTest {
    @Test
    fun `a split needs two positive parts adding up to the total`() {
        val total = Money.euros(33)
        assertTrue(isValidSplit(total, listOf(Money.euros(23), Money.euros(10))))
        assertTrue(!isValidSplit(total, listOf(Money.euros(33))))
        assertTrue(!isValidSplit(total, listOf(Money.euros(25), Money.euros(10))))
        assertTrue(!isValidSplit(total, listOf(Money.euros(33), Money.ZERO)))
        assertEquals(Money.euros(23), splitRemainder(total, listOf(Money.euros(10))))
    }
}
