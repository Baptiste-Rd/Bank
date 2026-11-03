package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthClosingTest {

    @Test
    fun `october savings stayed on the current account`() {
        // Octobre : 1 027,63 € non dépensés, rien viré vers les livrets.
        val review = monthClosing(Money(102763), Money.ZERO)
        assertEquals(Money(102763), review.onAccount)
        assertEquals(Money(102763), review.toTransfer)
    }

    @Test
    fun `a full transfer leaves nothing to transfer`() {
        val review = monthClosing(Money.euros(500), Money.euros(500))
        assertEquals(Money.ZERO, review.onAccount)
        assertEquals(Money.ZERO, review.toTransfer)
    }

    @Test
    fun `transferring more than the month saved takes from the account`() {
        val review = monthClosing(Money.euros(200), Money.euros(300))
        assertEquals(Money.euros(-100), review.onAccount)
        assertEquals(Money.ZERO, review.toTransfer)
    }

    @Test
    fun `nothing to transfer after a month in the red`() {
        assertEquals(Money.ZERO, monthClosing(Money.euros(-80), Money.ZERO).toTransfer)
    }
}
