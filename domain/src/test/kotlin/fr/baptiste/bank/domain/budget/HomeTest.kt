package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HomeTest {

    private val today = LocalDate.of(2026, 11, 10)

    private fun envelope(id: Long, type: EnvelopeType, euros: Long, frequency: Frequency = type.defaultFrequency) =
        Envelope(id, "E$id", type, frequency, Money.euros(euros), type.defaultStatus)

    private val envelopes = listOf(
        envelope(1, EnvelopeType.DAILY, 300),
        envelope(2, EnvelopeType.DAILY, 60),
        envelope(3, EnvelopeType.FIXED, 650),
        envelope(4, EnvelopeType.FIXED, 45),
        envelope(5, EnvelopeType.RECURRING, 26, Frequency(3, Scale.MONTH)),
        envelope(6, EnvelopeType.ANNUAL, 1000),
        envelope(7, EnvelopeType.DAILY, 80),
    )

    private fun board(spendings: List<Spending>) =
        computeDashboard(envelopes, emptyMap(), spendings, null, today)

    @Test
    fun `charges not yet debited are what must still leave the account`() {
        val dashboard = board(listOf(Spending(3, LocalDate.of(2026, 11, 5), Money.euros(650))))
        assertEquals(Money.euros(45), chargesToCome(dashboard.envelopes, today))
    }

    @Test
    fun `the home card shows an overspent envelope first and at most six rows`() {
        val dashboard = board(listOf(Spending(2, LocalDate.of(2026, 11, 3), Money.euros(70))))
        val home = homeBudget(dashboard.envelopes)
        assertEquals(2L, home.daily.first().envelope.id)
        // 3 quotidien + charges (une ligne) + 2 rares = 6.
        assertEquals(3, home.daily.size)
        assertEquals(2, home.rare.size)
        assertEquals(0, home.hidden)
        val short = homeBudget(dashboard.envelopes, maxRows = 3)
        assertEquals(2, short.daily.size)
        assertEquals(0, short.rare.size)
        assertEquals(3, short.hidden)
    }

    @Test
    fun `the pace compares the running total with the budget prorata`() {
        val pace = monthPace(
            YearMonth.of(2026, 11),
            today,
            listOf(LocalDate.of(2026, 11, 2) to Money.euros(100), LocalDate.of(2026, 11, 9) to Money.euros(50), LocalDate.of(2026, 10, 30) to Money.euros(999)),
            budget = Money.euros(900),
        )
        assertEquals(10, pace.cumulative.size)
        assertEquals(Money.euros(150), pace.spent)
        // 900 € × 10 / 30 = 300 € attendus : 150 € en dessous.
        assertEquals(Money.euros(300), pace.expected)
        assertEquals(Money.euros(-150), pace.gap)
    }

    @Test
    fun `a monthly saving is due until the transfer is seen, then shown three days`() {
        val account = HoldingAccount(1, "Trade Republic", AccountType.INVESTMENT, linked = false, monthlyTarget = Money.euros(100))
        val none = computeHoldings(listOf(account), emptyList(), today).lines.single()
        assertEquals(SavingReminder.Due(Money.euros(100)), none.reminder(today))
        val transfers = listOf(AccountTransfer(1, LocalDate.of(2026, 11, 9), Money.euros(-100)))
        val done = computeHoldings(listOf(account), emptyList(), today, transfersByAccount = transfers).lines.single()
        assertEquals(SavingReminder.Done(LocalDate.of(2026, 11, 9)), done.reminder(today))
        assertEquals(null, done.reminder(LocalDate.of(2026, 11, 13)))
    }
}
