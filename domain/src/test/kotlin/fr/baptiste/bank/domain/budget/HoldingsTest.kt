package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HoldingsTest {

    private val today = LocalDate.of(2026, 10, 28)
    private val livret = HoldingAccount(1, "Livret A", AccountType.SAVINGS, linked = true)
    private val tr = HoldingAccount(2, "Trade Republic", AccountType.INVESTMENT, linked = false)
    private val current = HoldingAccount(3, "Compte courant", AccountType.CURRENT, linked = true)
    private val snapshots = listOf(
        BalanceSnapshot(1, LocalDate.of(2026, 8, 3), Money.euros(5000)),
        BalanceSnapshot(1, LocalDate.of(2026, 9, 3), Money.euros(5300)),
        BalanceSnapshot(1, LocalDate.of(2026, 10, 3), Money.euros(5600)),
        BalanceSnapshot(2, LocalDate.of(2026, 9, 20), Money.euros(3000)),
        BalanceSnapshot(3, LocalDate.of(2026, 10, 1), Money.euros(900)),
    )

    @Test
    fun `total is the last known balance of savings and investment accounts only`() {
        val holdings = computeHoldings(listOf(livret, tr, current), snapshots, today)
        assertEquals(Money.euros(8600), holdings.total)
        assertEquals(2, holdings.lines.size)
        assertEquals(LocalDate.of(2026, 10, 3), holdings.lines.first().asOf)
    }

    @Test
    fun `history carries balances forward and starts at the first snapshot`() {
        val holdings = computeHoldings(listOf(livret, tr), snapshots, today, months = 6)
        assertEquals(
            listOf(
                HoldingsMonth(YearMonth.of(2026, 8), Money.euros(5000)),
                HoldingsMonth(YearMonth.of(2026, 9), Money.euros(8300)),
                HoldingsMonth(YearMonth.of(2026, 10), Money.euros(8600)),
            ),
            holdings.history,
        )
    }

    @Test
    fun `transfers to own accounts are summed as positive amounts`() {
        val holdings = computeHoldings(listOf(livret), snapshots, today, transfersThisMonth = listOf(Money.euros(-300), Money.euros(-50)))
        assertEquals(Money.euros(350), holdings.transferredThisMonth)
    }

    @Test
    fun `an account never filled in has no balance and no history`() {
        val holdings = computeHoldings(listOf(tr), emptyList(), today)
        assertEquals(null, holdings.lines.single().balance)
        assertEquals(Money.ZERO, holdings.total)
        assertEquals(emptyList<HoldingsMonth>(), holdings.history)
    }
}

class TaxesTest {

    @Test
    fun `securities account pays the 31_4 percent flat tax on the gain`() {
        // 3 120 € pour 3 000 € versés : 120 € de plus-value, 37,68 € d'impôt.
        val estimate = netEstimate(TaxWrapper.SECURITIES, Money.euros(3120), Money.euros(3000))!!
        assertEquals(Money.euros(120), estimate.gain)
        assertEquals(Money(3768), estimate.tax)
        assertEquals(Money(308232), estimate.net)
    }

    @Test
    fun `an old PEA only pays social contributions and a loss pays nothing`() {
        assertEquals(Money(18600), TaxWrapper.PEA_OVER_5.taxOn(Money.euros(1000)))
        assertEquals(Money.ZERO, TaxWrapper.SECURITIES.taxOn(Money.euros(-50)))
        // Assurance-vie de plus de 8 ans : 17,2 % sur tout, 7,5 % au-delà de 4 600 €.
        assertEquals(Money(172000 + 40500), TaxWrapper.LIFE_INSURANCE_OVER_8.taxOn(Money.euros(10000)))
    }

    @Test
    fun `without invested amount there is no estimate`() {
        assertEquals(null, netEstimate(TaxWrapper.SECURITIES, Money.euros(3120), null))
        assertEquals(null, netEstimate(null, Money.euros(3120), Money.euros(3000)))
    }
}
