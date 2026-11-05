package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.budget.Spending
import fr.baptiste.bank.domain.budget.monthSpending
import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CategoryTreeTest {

    private val parents = parentsOf(
        listOf(
            CategoryNode("Courses", "Nourriture"),
            CategoryNode("Bio", "Courses"),
            CategoryNode("Nourriture"),
            CategoryNode("Restaurants", "Nourriture"),
        ),
    )

    private fun envelope(id: Long, name: String, category: String?, euros: Long) = Envelope(
        id = id,
        name = name,
        type = EnvelopeType.DAILY,
        frequency = EnvelopeType.DAILY.defaultFrequency,
        amount = Money.euros(euros),
        status = EnvelopeType.DAILY.defaultStatus,
        group = category,
    )

    @Test
    fun `paths and ancestors follow the tree`() {
        assertEquals(listOf("Courses", "Nourriture"), ancestorsOf("Bio", parents))
        assertEquals("Nourriture › Courses › Bio", categoryPath("Bio", parents))
        assertEquals("Nourriture", rootOf("bio", parents))
    }

    @Test
    fun `a category never goes into itself or one of its subcategories`() {
        assertFalse(canMoveCategory("Nourriture", "Bio", parents))
        assertFalse(canMoveCategory("Courses", "Courses", parents))
        assertTrue(canMoveCategory("Bio", "Restaurants", parents))
        assertTrue(canMoveCategory("Courses", null, parents))
        // « Autres » reste principale, et ne reçoit pas de sous-catégorie.
        assertFalse(canMoveCategory(DEFAULT_CATEGORY, "Nourriture", parents))
        assertFalse(canMoveCategory("Courses", DEFAULT_CATEGORY, parents))
    }

    @Test
    fun `a parent adds up its subcategories`() {
        val month = YearMonth.of(2026, 11)
        val envelopes = listOf(
            envelope(1, "Supermarché", "Courses", 300),
            envelope(2, "Marché bio", "Bio", 80),
            envelope(3, "Resto", "Restaurants", 100),
            envelope(4, "Essence", "Transports", 120),
        )
        val day = LocalDate.of(2026, 11, 3)
        val spendings = listOf(
            Spending(1, day, Money.euros(50)),
            Spending(2, day, Money.euros(20)),
            Spending(3, day, Money.euros(30)),
            Spending(4, day, Money.euros(40)),
        )
        val report = monthSpending(month, envelopes, spendings, parents = parents)
        val food = report.categories.first { it.name == "Nourriture" }
        assertEquals(Money.euros(100), food.spent)
        assertEquals(Money.euros(480), food.budget)
        assertTrue(food.envelopes.isEmpty())
        val courses = food.children.first { it.name == "Courses" }
        assertEquals(Money.euros(70), courses.spent)
        assertEquals("Bio", courses.children.single().name)
        // Le total du mois ne compte chaque dépense qu'une fois.
        assertEquals(Money.euros(140), report.total)
        assertEquals(listOf("Nourriture", "Transports"), report.categories.map { it.name })
        assertEquals(3, food.descendants.size)
    }
}
