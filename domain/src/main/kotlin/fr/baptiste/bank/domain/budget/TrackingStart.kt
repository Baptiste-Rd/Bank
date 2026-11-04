package fr.baptiste.bank.domain.budget

import java.time.LocalDate
import java.time.YearMonth

/**
 * Jours de grâce en début de mois : une première opération le 5 n'empêche pas le mois d'être
 * entier (on ne dépense pas forcément le 1er), une première opération le 27 si.
 */
const val PARTIAL_MONTH_GRACE_DAYS = 7

/**
 * Début du suivi quand l'utilisateur n'en a pas choisi : le mois de la première opération, sauf
 * si ses données commencent après les premiers jours. Ce mois incomplet (une banque qui ne donne
 * que 90 jours d'historique commence souvent en cours de mois) ferait baisser les moyennes, les
 * montants proposés et la dépense habituelle : le suivi commence alors au mois suivant, et le mois
 * incomplet reste visible comme « non renseigné », avec la possibilité de le compter quand même.
 * Le mois en cours n'est jamais exclu.
 */
fun defaultTrackingStart(firstDate: LocalDate?, today: LocalDate): YearMonth {
    val current = YearMonth.from(today)
    if (firstDate == null) return current
    val first = YearMonth.from(firstDate)
    val start = if (firstDate.dayOfMonth > PARTIAL_MONTH_GRACE_DAYS) first.plusMonths(1) else first
    return minOf(start, current)
}

/** Vrai si [month] est le mois incomplet du début des données (exclu du suivi par défaut). */
fun isPartialFirstMonth(month: YearMonth, firstDate: LocalDate?): Boolean =
    firstDate != null && YearMonth.from(firstDate) == month && firstDate.dayOfMonth > PARTIAL_MONTH_GRACE_DAYS
