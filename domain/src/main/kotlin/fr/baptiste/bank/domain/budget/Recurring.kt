package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.bank.normalizeLabel
import fr.baptiste.bank.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth

/*
 * Dépenses qui reviennent chaque mois (abonnements, forfaits, prélèvements) : un même libellé,
 * au moins trois mois différents parmi les derniers, un montant stable (à 10 % près). On les
 * montre pour qu'on les voie d'un coup d'œil ; rien n'est classé automatiquement.
 */

/** Une sortie d'argent : nom affiché, jour, montant (positif). */
data class Outflow(val label: String, val date: LocalDate, val amount: Money)

data class RecurringExpense(
    val label: String,
    /** Montant du dernier mois. */
    val monthly: Money,
    /** Nombre de mois où il apparaît. */
    val months: Int,
    val lastDate: LocalDate,
)

/** Dépenses récurrentes, la plus chère d'abord. [minMonths] : mois distincts exigés. */
fun detectRecurring(outflows: List<Outflow>, minMonths: Int = 3): List<RecurringExpense> =
    outflows
        .groupBy { normalizeLabel(it.label) }
        .mapNotNull { (_, items) ->
            val byMonth = items.groupBy { YearMonth.from(it.date) }
            if (byMonth.size < minMonths) return@mapNotNull null
            // Un passage par mois : plusieurs le même mois (courses), ce n'est pas un abonnement.
            if (byMonth.values.any { it.size > 1 }) return@mapNotNull null
            val amounts = items.map { it.amount.cents }
            val low = amounts.min()
            val high = amounts.max()
            if (high - low > low / 10) return@mapNotNull null
            val last = items.maxBy { it.date }
            RecurringExpense(last.label, last.amount, byMonth.size, last.date)
        }
        .sortedByDescending { it.monthly }
