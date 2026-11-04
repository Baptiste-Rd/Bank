package fr.baptiste.bank.domain.budget

import java.time.LocalDate
import java.time.YearMonth

/**
 * Jours de grâce en début de mois : une première opération le 5 n'empêche pas le mois d'être
 * entier (on ne dépense pas forcément le 1er), une première opération le 27 si.
 */
const val PARTIAL_MONTH_GRACE_DAYS = 7

/** Vrai si les données de [month] ne couvrent pas son début (première opération après le 7). */
fun isPartialFirstMonth(month: YearMonth, firstDate: LocalDate?): Boolean =
    firstDate != null && YearMonth.from(firstDate) == month && firstDate.dayOfMonth > PARTIAL_MONTH_GRACE_DAYS

/**
 * Mois que l'on ne compte pas dans les moyennes (montants proposés, dépense habituelle, moyennes
 * et épargne de l'année). Chaque mois a son option, [choices] (mois → compté) : un mois de vacances
 * ou exceptionnel peut être écarté, et remis. Sans choix, un mois est compté, sauf si ses données
 * ne couvrent pas son début (une banque qui ne donne que 90 jours d'historique commence souvent
 * en cours de mois) : il ferait baisser toutes les moyennes. Le mois reste visible avec ses dépenses.
 */
fun excludedMonths(choices: Map<YearMonth, Boolean>, firstDate: LocalDate?): Set<YearMonth> {
    val byDefault = firstDate?.let { YearMonth.from(it) }?.takeIf { isPartialFirstMonth(it, firstDate) && choices[it] == null }
    return choices.filterValues { counted -> !counted }.keys + listOfNotNull(byDefault)
}
