package fr.baptiste.bank.ui.format

import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.domain.period.Scale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val french = Locale.FRANCE
private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", french)

val EnvelopeType.label: String
    get() = when (this) {
        EnvelopeType.DAILY -> "Quotidien"
        EnvelopeType.RECURRING -> "Récurrent"
        EnvelopeType.ANNUAL -> "Annuel"
        EnvelopeType.FIXED -> "Charge fixe"
        EnvelopeType.SAVINGS -> "Épargne"
    }

val EnvelopeType.hint: String
    get() = when (this) {
        EnvelopeType.DAILY -> "Compte dans le disponible du jour (nourriture, essence, sorties)"
        EnvelopeType.RECURRING -> "Dépense à fréquence connue (coiffeur, abonnements)"
        EnvelopeType.ANNUAL -> "Consommée irrégulièrement sur l'année (projets, CBD)"
        EnvelopeType.FIXED -> "Prélèvement certain (prêt, loyer)"
        EnvelopeType.SAVINGS -> "Objectif d'épargne (livret A, PEA)"
    }

/** Nom de l'unité, accordé : « jour » / « jours », « mois » est invariable. */
fun Scale.unitName(count: Int): String {
    val plural = count > 1
    return when (this) {
        Scale.DAY -> if (plural) "jours" else "jour"
        Scale.WEEK -> if (plural) "semaines" else "semaine"
        Scale.MONTH -> "mois"
        Scale.YEAR -> if (plural) "ans" else "an"
    }
}

/** « par mois », « toutes les 6 semaines », « tous les 10 jours ». */
val Frequency.label: String
    get() {
        if (every == 1) {
            return when (scale) {
                Scale.DAY -> "par jour"
                Scale.WEEK -> "par semaine"
                Scale.MONTH -> "par mois"
                Scale.YEAR -> "par an"
            }
        }
        val all = if (scale == Scale.WEEK) "toutes les" else "tous les"
        return "$all $every ${scale.unitName(every)}"
    }

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", french)

val Period.label: String
    get() = when (this) {
        is Period.Month -> "${yearMonth.month.getDisplayName(TextStyle.FULL, french)} ${yearMonth.year}"
        is Period.Year -> year.toString()
        is Period.Week -> "semaine $week"
        is Period.Span -> if (start == endInclusive) {
            "le ${start.format(dayMonth)}"
        } else {
            "du ${start.format(dayMonth)} au ${endInclusive.format(dayMonth)}"
        }
    }

/** Désignation de la période pour une phrase : « le mois », « la semaine », « la période ». */
val Period.noun: String
    get() = when (this) {
        is Period.Month -> "le mois"
        is Period.Year -> "l'année"
        is Period.Week -> "la semaine"
        is Period.Span -> if (start == endInclusive) "la journée" else "la période"
    }

fun LocalDate.formatted(): String = format(shortDate)
