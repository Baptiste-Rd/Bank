package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import fr.baptiste.bank.domain.period.Frequency
import java.time.LocalDate
import java.time.YearMonth

/*
 * Montant proposé pour un budget : ce que l'on a vraiment dépensé, en moyenne, sur les douze
 * derniers mois terminés (ou moins : trois mois de données donnent la moyenne de ces trois mois).
 * On l'ajuste ensuite ; il ne change plus seul.
 */

data class AmountSuggestion(
    /** Montant par période de la fréquence demandée. */
    val amount: Money,
    /** Mois terminés pris en compte. */
    val months: Int,
    val monthlyAverage: Money,
)

/**
 * Proposition pour un budget de [frequency], d'après [spent] (jour, montant positif) : moyenne par
 * mois des mois terminés avant [today], depuis [since] (premier mois où l'on a des données :
 * début du suivi ou plus ancienne opération) et sur [maxMonths] au plus.
 * Null sans mois terminé ni dépense.
 */
fun suggestedAmount(
    spent: List<Pair<LocalDate, Money>>,
    frequency: Frequency,
    today: LocalDate,
    since: YearMonth? = null,
    maxMonths: Int = 12,
): AmountSuggestion? {
    val current = YearMonth.from(today)
    val months = (1..maxMonths)
        .map { current.minusMonths(it.toLong()) }
        .filter { since == null || !it.isBefore(since) }
    if (months.isEmpty()) return null
    val total = spent.filter { YearMonth.from(it.first) in months }.sumOfMoney { it.second }
    if (!total.isPositive) return null
    val monthly = total.divideCeil(months.size.toLong())
    return AmountSuggestion(monthly.convert(Frequency.MONTHLY, frequency), months.size, monthly)
}
