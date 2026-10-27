package fr.baptiste.bank.domain.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParsingTest {

    @Test
    fun `accepts the usual french and english notations`() {
        assertEquals(Money(1200), parseMoney("12"))
        assertEquals(Money(1250), parseMoney("12,5"))
        assertEquals(Money(1250), parseMoney("12.50"))
        assertEquals(Money(123456), parseMoney("1 234,56"))
        assertEquals(Money(123456), parseMoney("1 234,56 €"))
        assertEquals(Money(-305), parseMoney("-3,05"))
    }

    @Test
    fun `rejects invalid input`() {
        assertEquals(null, parseMoney(""))
        assertEquals(null, parseMoney("abc"))
        assertEquals(null, parseMoney("12,345"))
        assertEquals(null, parseMoney("12,"))
        assertEquals(null, parseMoney("1,2,3"))
    }
}
