package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthAnalysisTest {

    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 15)

    private fun envelope(id: Long, name: String, type: EnvelopeType, euros: Long, group: String) =
        Envelope(id, name, type, type.defaultFrequency, Money.euros(euros), type.defaultStatus, group = group)

    private val food = envelope(1, "Courses", EnvelopeType.DAILY, 300, "Nourriture")
    private val rent = envelope(2, "Loyer", EnvelopeType.FIXED, 650, "Logement")

    private fun spending(month: YearMonth, foodEuros: Long) = monthSpending(
        month,
        listOf(food, rent),
        listOf(
            Spending(1, month.atDay(5), Money.euros(foodEuros)),
            Spending(2, month.atDay(2), Money.euros(650)),
        ),
        unsorted = Money.euros(12),
    )

    private val outflows = listOf(
        Outflow("Carrefour City", LocalDate.of(2026, 10, 3), Money.euros(45)),
        Outflow("Carrefour City", LocalDate.of(2026, 10, 10), Money.euros(38)),
        Outflow("Boulangerie Paul CB 4970XXXXXXXX1234", LocalDate.of(2026, 10, 11), Money(450)),
        Outflow("Loyer", LocalDate.of(2026, 10, 2), Money.euros(650)),
    )

    private fun prompt(includeMerchants: Boolean) = monthAnalysisPrompt(
        MonthAnalysisInput(
            month = spending(october, 150),
            previous = listOf(spending(YearMonth.of(2026, 9), 280), spending(YearMonth.of(2026, 8), 310)).reversed(),
            outflows = outflows,
            recurring = listOf(RecurringExpense("Netflix", Money(1399), 4, LocalDate.of(2026, 10, 8))),
            savingsForecast = Money.euros(200),
            savingsTarget = Money.euros(300),
            includeMerchants = includeMerchants,
            today = today,
        ),
    )

    @Test
    fun `the full analysis carries envelopes, history, habits and the expected answer`() {
        val text = prompt(includeMerchants = true)
        assertTrue(text.contains("- Courses (Nourriture) : 150,00 € / 300,00 € → +150,00 € ; projection 310,00 €"))
        assertTrue(text.contains("Colonnes : août 26 | sept. 26 | oct. 26"))
        assertTrue(text.contains("- Nourriture : 310,00 € | 280,00 € | 150,00 €"))
        assertTrue(text.contains("Carrefour City 83,00 € (2×)"))
        assertTrue(text.contains("Petites dépenses (moins de 10 €) : 1 pour 4,50 €"))
        assertTrue(text.contains("Netflix : 13,99 €"))
        assertTrue(text.contains("Encore à trier (non compté) : 12,00 €"))
        assertTrue(text.contains("Trois actions concrètes et chiffrées"))
        // Un numéro de carte ne part jamais.
        assertTrue(!text.contains("4970"))
    }

    @Test
    fun `without merchants only totals leave`() {
        val text = prompt(includeMerchants = false)
        assertTrue(!text.contains("Carrefour"))
        assertTrue(!text.contains("Netflix"))
        assertTrue(text.contains("1 abonnements ou prélèvements réguliers"))
    }
}
