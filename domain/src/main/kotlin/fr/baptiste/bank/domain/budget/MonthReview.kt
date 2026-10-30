package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.bank.assistantAmount
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import java.time.format.TextStyle
import java.util.Locale

/*
 * L'avis de Claude sur un mois : un message prêt à envoyer, fait seulement de totaux (par
 * catégorie, face au budget et à la moyenne des mois précédents), de l'épargne du mois et de ce qui
 * reste à trier. Aucun libellé d'opération, aucun compte, aucun solde.
 */

/** Message pour demander à Claude un avis sur [month], comparé aux mois de [previous] (renseignés). */
fun monthReviewPrompt(
    month: MonthSpending,
    previous: List<MonthSpending>,
    savingsForecast: Money? = null,
    savingsTarget: Money? = null,
): String = buildString {
    val name = "${month.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)} ${month.month.year}"
    val tracked = previous.filter { it.tracked }
    appendLine("Tu es mon conseiller budget. Voici mes dépenses de $name (budget personnel, en France).")
    appendLine("Montants positifs = dépensé. « moy. » = moyenne de mes ${tracked.size} mois précédents renseignés.")
    appendLine()
    appendLine("Total dépensé : ${euros(month.total)} pour un budget de ${euros(month.budget)}.")
    if (month.unsorted.isPositive) appendLine("Encore à trier (non compté) : ${euros(month.unsorted)}.")
    if (savingsForecast != null) {
        appendLine("Épargne du mois : ${euros(savingsForecast)}" + (savingsTarget?.let { " (objectif ${euros(it)})" } ?: "") + ".")
    }
    appendLine()
    appendLine("Par catégorie (dépensé | budget | moy.) :")
    month.categories.forEach { category ->
        val history = tracked.mapNotNull { m -> m.categories.firstOrNull { it.name == category.name }?.spent }
        val average = if (tracked.isEmpty()) null else history.sumOfMoney { it }.divideFloor(tracked.size.toLong())
        appendLine("- ${category.name} : ${euros(category.spent)} | ${euros(category.budget)} | ${average?.let(::euros) ?: "–"}")
    }
    appendLine()
    appendLine(
        "Donne-moi un avis court et concret, en français, en 5 points au plus : ce qui va bien, ce qui dérive " +
            "(par rapport au budget ou à mes habitudes), une cause probable si elle se devine, et UNE action " +
            "simple pour le mois prochain, chiffrée. Pas de généralités.",
    )
}.trimEnd()

private fun euros(amount: Money): String = assistantAmount(amount.cents).removePrefix("+")
