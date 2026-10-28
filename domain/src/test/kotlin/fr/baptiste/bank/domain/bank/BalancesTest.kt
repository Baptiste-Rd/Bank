package fr.baptiste.bank.domain.bank

import org.junit.Assert.assertEquals
import org.junit.Test

class BalancesTest {

    @Test
    fun `expected balance wins when the bank gives it`() {
        assertEquals(ChosenBalance(70_200, "XPCD", 0), chooseBalance(mapOf("CLBD" to 81_000L, "XPCD" to 70_200L), pendingNet = -10_800))
    }

    @Test
    fun `booked balance loses the pending card payments`() {
        assertEquals(ChosenBalance(70_200, "CLBD", -10_800), chooseBalance(mapOf("CLBD" to 81_000L, "CLAV" to 131_000L), pendingNet = -10_800))
    }

    @Test
    fun `available balance only as a last resort, it may include the overdraft`() {
        assertEquals(ChosenBalance(131_000, "CLAV", 0), chooseBalance(mapOf("CLAV" to 131_000L), pendingNet = -10_800))
        assertEquals(null, chooseBalance(emptyMap()))
    }
}
