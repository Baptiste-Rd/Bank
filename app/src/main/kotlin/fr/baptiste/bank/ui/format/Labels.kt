package fr.baptiste.bank.ui.format

import fr.baptiste.bank.domain.model.EnvelopeType
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

val Scale.label: String
    get() = when (this) {
        Scale.WEEK -> "par semaine"
        Scale.MONTH -> "par mois"
        Scale.YEAR -> "par an"
    }

val Period.label: String
    get() = when (this) {
        is Period.Month -> "${yearMonth.month.getDisplayName(TextStyle.FULL, french)} ${yearMonth.year}"
        is Period.Year -> year.toString()
        is Period.Week -> "semaine $week"
    }

fun LocalDate.formatted(): String = format(shortDate)
