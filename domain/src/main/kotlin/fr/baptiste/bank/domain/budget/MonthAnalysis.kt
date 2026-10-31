package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.bank.anonymizeLabel
import fr.baptiste.bank.domain.bank.assistantAmount
import fr.baptiste.bank.domain.bank.normalizeLabel
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.category
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/*
 * « Analyse complète » d'un mois : un message pour Claude, plus riche que l'avis rapide
 * ([monthReviewPrompt]). Il contient le budget enveloppe par enveloppe, l'évolution des mois
 * précédents, l'épargne et les projets, les abonnements, et — si on le veut — les habitudes par
 * commerçant (libellés nettoyés). Jamais d'IBAN, de numéro de carte ou de référence, ni de solde.
 */

/** Ce que l'on sait d'un mois pour l'analyser. */
data class MonthAnalysisInput(
    val month: MonthSpending,
    /** Mois renseignés précédents, du plus ancien au plus récent (six au plus sont envoyés). */
    val previous: List<MonthSpending>,
    /** Sorties d'argent comptées du mois, avec leur nom (pour les habitudes). */
    val outflows: List<Outflow> = emptyList(),
    val recurring: List<RecurringExpense> = emptyList(),
    val income: Money? = null,
    val savingsForecast: Money? = null,
    val savingsTarget: Money? = null,
    /** Inclure les noms des commerçants (sinon : seulement des totaux). */
    val includeMerchants: Boolean = true,
    val today: LocalDate = LocalDate.now(),
)

private const val MAX_PREVIOUS = 6
private const val TOP = 8
private const val BIGGEST = 5

/** Une petite dépense : en dessous, elle compte dans les « petites dépenses répétées ». */
private val SMALL = Money.euros(10)

fun monthAnalysisPrompt(input: MonthAnalysisInput): String = buildString {
    val month = input.month
    val name = monthName(month.month)
    val current = YearMonth.from(input.today) == month.month
    val previous = input.previous.filter { it.tracked }.takeLast(MAX_PREVIOUS)

    appendLine("Tu es mon conseiller budget personnel (France). Fais une analyse complète et précise de $name.")
    appendLine("Montants en euros, positifs = dépensé. Données anonymisées : ni compte, ni solde, ni numéro.")
    if (current) {
        appendLine("Le mois est en cours : nous sommes le ${input.today.dayOfMonth} sur ${month.month.lengthOfMonth()} jours.")
    }
    appendLine()

    appendLine("## Cadre du mois")
    input.income?.let { appendLine("- Salaire mensuel : ${euros(it)}") }
    appendLine("- Dépensé : ${euros(month.total)} pour un budget de ${euros(month.budget)}")
    val families = month.categories.flatMap { it.envelopes }.groupBy { familyName(it.envelope.type) }
    families.forEach { (family, lines) ->
        appendLine("  - $family : ${euros(lines.sumOfMoney { it.spent })} sur ${euros(lines.sumOfMoney { it.budget })}")
    }
    if (input.savingsForecast != null) {
        appendLine("- Épargne du mois : ${euros(input.savingsForecast)}" + (input.savingsTarget?.let { " (objectif ${euros(it)})" } ?: ""))
    }
    appendLine()

    appendLine("## Budget enveloppe par enveloppe")
    appendLine("Format : enveloppe (catégorie) : dépensé / budget du mois → reste" + if (current) " ; projection fin de mois au rythme actuel" else "")
    month.categories.flatMap { it.envelopes }.sortedByDescending { it.spent }.forEach { line ->
        val envelope = line.envelope
        val rest = line.budget - line.spent
        val projection = if (current && envelope.type == EnvelopeType.DAILY && input.today.dayOfMonth > 0) {
            val projected = (line.spent * month.month.lengthOfMonth().toLong()).divideFloor(input.today.dayOfMonth.toLong())
            " ; projection ${euros(projected)}"
        } else {
            ""
        }
        val period = if (envelope.type != EnvelopeType.DAILY && envelope.type != EnvelopeType.FIXED) {
            val end = envelope.periodContaining(month.month.atEndOfMonth()).endInclusive
            " ; budget de ${euros(envelope.amount)} jusqu'au ${end.dayOfMonth}/${end.monthValue}/${end.year}"
        } else {
            ""
        }
        appendLine("- ${envelope.name} (${envelope.category}) : ${euros(line.spent)} / ${euros(line.budget)} → ${signed(rest)}$projection$period")
    }
    appendLine()

    if (previous.isNotEmpty()) {
        appendLine("## Évolution par catégorie")
        appendLine("Colonnes : " + (previous.map { shortMonth(it.month) } + shortMonth(month.month)).joinToString(" | "))
        val names = (month.categories.map { it.name } + previous.flatMap { m -> m.categories.map { it.name } }).distinct()
        names.forEach { category ->
            val values = (previous + month).map { m -> m.categories.firstOrNull { it.name == category }?.spent ?: Money.ZERO }
            appendLine("- $category : " + values.joinToString(" | ") { euros(it) })
        }
        appendLine("- Total : " + (previous + month).joinToString(" | ") { euros(it.total) })
        appendLine()
    }

    val outflows = input.outflows.filter { YearMonth.from(it.date) == month.month }
    if (outflows.isNotEmpty()) {
        appendLine("## Habitudes du mois")
        val weekend = outflows.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        appendLine("- ${outflows.size} dépenses ; week-ends : ${euros(weekend.sumOfMoney { it.amount })}, semaine : ${euros((outflows - weekend.toSet()).sumOfMoney { it.amount })}")
        val small = outflows.filter { it.amount < SMALL }
        if (small.isNotEmpty()) appendLine("- Petites dépenses (moins de 10 €) : ${small.size} pour ${euros(small.sumOfMoney { it.amount })}")
        if (input.includeMerchants) {
            val byMerchant = outflows.groupBy { normalizeLabel(it.label) }.values.map { items ->
                Triple(anonymizeLabel(items.first().label), items.size, items.sumOfMoney { it.amount })
            }
            appendLine("- Commerçants les plus coûteux : " + byMerchant.sortedByDescending { it.third }.take(TOP).joinToString(" ; ") { "${it.first} ${euros(it.third)} (${it.second}×)" })
            appendLine("- Les plus fréquents : " + byMerchant.filter { it.second > 1 }.sortedByDescending { it.second }.take(TOP).joinToString(" ; ") { "${it.first} ${it.second}× (${euros(it.third)})" }.ifEmpty { "aucun commerçant plus d'une fois" })
            appendLine("- Plus grosses dépenses : " + outflows.sortedByDescending { it.amount }.take(BIGGEST).joinToString(" ; ") { "${anonymizeLabel(it.label)} ${euros(it.amount)} le ${it.date.dayOfMonth}/${it.date.monthValue}" })
        } else {
            appendLine("- Plus grosses dépenses : " + outflows.sortedByDescending { it.amount }.take(BIGGEST).joinToString(" ; ") { "${euros(it.amount)} le ${it.date.dayOfMonth}/${it.date.monthValue}" })
        }
        appendLine()
    }

    if (input.recurring.isNotEmpty()) {
        appendLine("## Dépenses qui reviennent chaque mois")
        appendLine("Total : ${euros(input.recurring.sumOfMoney { it.monthly })} par mois")
        if (input.includeMerchants) {
            input.recurring.forEach { appendLine("- ${anonymizeLabel(it.label)} : ${euros(it.monthly)} (vu ${it.months} mois)") }
        } else {
            appendLine("- ${input.recurring.size} abonnements ou prélèvements réguliers")
        }
        appendLine()
    }

    appendLine("## Fiabilité des données")
    appendLine("- Encore à trier (non compté) : ${euros(month.unsorted)}")
    appendLine("- Classé hors budget (non compté) : ${euros(month.outOfBudget)}")
    appendLine("- Viré vers mes comptes d'épargne (non compté) : ${euros(month.toAccounts)}")
    appendLine()

    appendLine("## Ce que j'attends")
    appendLine("Réponds en français, avec ces parties et des chiffres partout :")
    appendLine("1. Synthèse en 3 lignes.")
    appendLine("2. Budget : enveloppe par enveloppe, ce qui tient, ce qui dérape et de combien.")
    appendLine("3. Habitudes : ce que révèlent les commerçants, la fréquence, les week-ends, les petites dépenses.")
    appendLine("4. Évolution : ce qui monte ou baisse par rapport aux mois précédents, et pourquoi si ça se devine.")
    appendLine("5. Risques : fin de mois" + (if (current) " (au rythme actuel)" else "") + " et mois suivants (abonnements, projets).")
    appendLine("6. Trois actions concrètes et chiffrées pour le mois prochain, la plus rentable d'abord.")
    appendLine("7. Les questions que tu me poserais pour affiner.")
    appendLine("Signale toute donnée qui te paraît incohérente (une dépense mal rangée, un mois incomplet). Pas de généralités.")
}.trimEnd()

private fun familyName(type: EnvelopeType): String = when (type) {
    EnvelopeType.DAILY -> "Au quotidien"
    EnvelopeType.FIXED -> "Charges fixes"
    EnvelopeType.SAVINGS -> "Épargne"
    else -> "Budgets rares et projets"
}

private fun monthName(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)} ${month.year}"

private fun shortMonth(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.FRANCE)} ${month.year % 100}"

private fun euros(amount: Money): String = assistantAmount(amount.cents).removePrefix("+")

private fun signed(amount: Money): String = assistantAmount(amount.cents)
