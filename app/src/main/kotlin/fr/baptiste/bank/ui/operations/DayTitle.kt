package fr.baptiste.bank.ui.operations

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayHeader = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

/** Titre d'un jour dans une liste d'opérations : « Aujourd'hui », « Hier », sinon « Lundi 3 novembre ». */
internal fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Aujourd'hui"
    today.minusDays(1) -> "Hier"
    else -> date.format(dayHeader).replaceFirstChar { it.titlecase(Locale.FRANCE) }
}
