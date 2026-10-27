package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.domain.period.Frequency
import java.time.LocalDate
import java.time.YearMonth

/**
 * Part mensuelle qu'une enveloppe réserve sur le revenu du mois.
 *
 * Une enveloppe annuelle reste affichée « entièrement disponible », mais le calcul
 * du quotidien doit en réserver 1/12 chaque mois : sinon le même argent serait
 * compté deux fois, dans l'enveloppe et dans le budget quotidien.
 */
fun Envelope.monthlyReservation(): Money =
    if (archived) Money.ZERO else amount.convert(from = frequency, to = Frequency.MONTHLY)

/**
 * Budget quotidien suggéré pour le mois : le revenu moins tout ce qui est réservé
 * par les enveloppes non quotidiennes (charges fixes, épargne, provisions des
 * enveloppes récurrentes et annuelles).
 *
 * Le résultat peut être négatif : cela signale que les engagements dépassent le revenu.
 */
fun suggestedDailyBudget(monthlyIncome: Money, envelopes: List<Envelope>): Money {
    val reserved = envelopes
        .filter { it.type != EnvelopeType.DAILY }
        .sumOfMoney { it.monthlyReservation() }
    return monthlyIncome - reserved
}

/**
 * Ce que tu peux dépenser aujourd'hui.
 *
 * @property allowance part du budget restant attribuée à aujourd'hui, avant les dépenses du jour.
 * @property spentToday dépenses quotidiennes déjà faites aujourd'hui.
 * @property remainingToday ce qu'il reste à dépenser aujourd'hui (négatif en cas de dépassement).
 */
data class DailyAllowance(
    val allowance: Money,
    val spentToday: Money,
    val remainingToday: Money,
)

/**
 * Répartit le budget quotidien restant du mois sur les jours restants, aujourd'hui inclus.
 *
 * Les dépenses du jour ne réduisent pas la part du jour, elles la consomment : sinon
 * chaque achat ferait baisser la part journalière en plus d'être décompté.
 * La part est arrondie au centime inférieur pour ne jamais surestimer ce qui est disponible.
 *
 * @param monthlyDailyBudget total alloué aux enveloppes quotidiennes pour le mois.
 * @param spentBeforeToday dépenses quotidiennes du mois avant aujourd'hui.
 * @param spentToday dépenses quotidiennes d'aujourd'hui.
 */
fun dailyAllowance(
    monthlyDailyBudget: Money,
    spentBeforeToday: Money,
    spentToday: Money,
    today: LocalDate,
): DailyAllowance {
    val month = Period.Month(YearMonth.from(today))
    val daysRemaining = month.daysRemainingFrom(today).toLong()
    val allowance = (monthlyDailyBudget - spentBeforeToday).divideFloor(daysRemaining)
    return DailyAllowance(
        allowance = allowance,
        spentToday = spentToday,
        remainingToday = allowance - spentToday,
    )
}

/**
 * Restant d'une enveloppe sur une période.
 * Un restant négatif est un dépassement.
 */
fun envelopeRemaining(
    allocated: Money,
    carriedOver: Money,
    transfersIn: Money,
    transfersOut: Money,
    imputed: Money,
): Money = allocated + carriedOver + transfersIn - transfersOut - imputed
