package fr.baptiste.bank.ui.format

import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

/** L12 : un même montant s'écrit pareil, quel que soit son signe (I1). */
class MoneyFormatTest {

    @Test
    fun `I1 a half euro rounds away from zero in a summary`() {
        assertEquals("−28 €", Money(-2750).summary())
        assertEquals("28 €", Money(2750).summary())
    }

    @Test
    fun `a summary keeps the cents below ten euros`() {
        assertEquals("9,50 €", Money(950).summary())
    }
}
