package fr.baptiste.bank.ui.glass

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.domain.budget.CategorySpent
import fr.baptiste.bank.domain.budget.EnvelopeSpent
import fr.baptiste.bank.domain.budget.MonthSpending
import fr.baptiste.bank.domain.budget.YearSpending
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.InfoLine
import fr.baptiste.bank.ui.components.InfoSheet
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.summary
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.ui.theme.categoryColor
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Analyse des dépenses : « où est parti l'argent ? » (mois) et « est-ce que ça change ? » (année).
 * Mois : le total réellement dépensé, puis une barre par catégorie (la plus grosse en haut), le
 * budget marqué d'un trait ; toucher une catégorie montre ses enveloppes, puis leurs opérations.
 * Année : une barre par mois, empilée (les deux plus grosses catégories, puis le reste), la
 * moyenne en pointillés, puis un tableau par catégorie. Les mois non renseignés sont grisés et ne
 * comptent pas. Ce qui reste à trier est toujours affiché à part : sans lui, le total paraîtrait
 * complet alors qu'il ne l'est pas.
 */

/** Vues de l'onglet Dépenses. */
enum class SpendingTabView { ANALYSIS, LIST }

private enum class AnalysisPeriod { MONTH, YEAR }

/*
 * Couleurs de l'histogramme de l'année, vérifiées sur la carte de verre (#19357D) : lisibles à
 * plus de 3:1, distinctes pour les daltoniens (écart ΔE ≥ 10) comme pour les autres (≥ 22).
 */
private val FirstCategoryColor = Color(0xFFDB6F24)
private val SecondCategoryColor = Color(0xFF119C82)
private val OtherCategoriesColor = Color(0xFF8E7BE0)

/** Barres du mois : une seule série (la ligne nomme la catégorie), le dépassement en rouge. */
private val MonthBarColor = Color(0xFFE2E8F0)

@Composable
fun GlassAnalysisContent(
    header: LazyListScope.() -> Unit,
    month: YearMonth,
    monthYear: YearSpending?,
    canNextMonth: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    year: Int,
    yearSpending: YearSpending?,
    canNextYear: Boolean,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    /** Choisit un mois depuis l'histogramme de l'année : la vue passe au mois. */
    onSelectMonth: (YearMonth) -> Unit,
    trackingStart: YearMonth?,
    onTrackMonth: (YearMonth) -> Unit,
    /** Ouvre la liste, pour trier ce qui reste. */
    onSort: () -> Unit,
    envelopeOperations: (Long, YearMonth) -> Flow<List<OperationRow>>,
    /** Vue Année plutôt que Mois (choisie dans la bascule de l'en-tête). */
    showYear: Boolean = false,
    /** Passe à la vue Mois (après avoir touché un mois de l'histogramme). */
    onShowMonth: () -> Unit = {},
    /** Avis de Claude gardé avec un mois, et son enregistrement. */
    monthReview: (YearMonth) -> Flow<String?> = { kotlinx.coroutines.flow.flowOf(null) },
    onSaveReview: (YearMonth, String?) -> Unit = { _, _ -> },
    /** Épargne du mois consulté (pour l'avis). */
    savings: fr.baptiste.bank.domain.budget.SavingsOutlook? = null,
    /** Dépenses qui reviennent chaque mois. */
    recurring: List<fr.baptiste.bank.domain.budget.RecurringExpense> = emptyList(),
    /** Sorties d'argent d'un mois, avec leur nom (analyse complète). */
    monthOutflows: (YearMonth) -> Flow<List<fr.baptiste.bank.domain.budget.Outflow>> = { kotlinx.coroutines.flow.flowOf(emptyList()) },
    /** Salaire indiqué, s'il l'est (analyse complète). */
    income: Money? = null,
    /** L'année d'avant le mois consulté : les 12 mois glissants peuvent la toucher. */
    previousYear: YearSpending? = null,
    /** Rythme du mois en cours : la comparaison à la même date du mois précédent. */
    pace: fr.baptiste.bank.domain.budget.MonthPace? = null,
    /** Option de chaque mois : le compter, ou non, dans les moyennes. */
    onSetMonthCounted: (YearMonth, Boolean) -> Unit = { _, _ -> },
    /** Catégories imbriquées : la mère de chaque catégorie. */
    categoryParents: Map<String, String> = emptyMap(),
    /** Une catégorie à montrer dépliée (la recherche y mène). */
    expandCategory: String? = null,
    /** Actions des lignes glissées. */
    rowActions: CategoryRowActions = CategoryRowActions(),
    /** Le tableau du mois consulté : reste et barre de chaque enveloppe. */
    board: fr.baptiste.bank.domain.budget.Dashboard? = null,
    groups: List<fr.baptiste.bank.domain.budget.GroupNode> = emptyList(),
    onOpenLine: (fr.baptiste.bank.domain.budget.EnvelopeLine) -> Unit = {},
) {
    val period = if (showYear) AnalysisPeriod.YEAR else AnalysisPeriod.MONTH
    var info by remember { mutableStateOf<MonthSpending?>(null) }
    var asking by remember { mutableStateOf<MonthSpending?>(null) }
    val review by remember(month) { monthReview(month) }.collectAsState(initial = null)
    val current = YearMonth.now()
    val excludedMonths = monthYear?.excluded ?: previousYear?.excluded ?: emptySet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberTabListState(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()
        when (period) {
            AnalysisPeriod.MONTH -> {

                val report = monthYear?.takeIf { it.year == month.year }?.months?.firstOrNull { it.month == month }
                when {
                    trackingStart != null && month < trackingStart ->
                        item(key = "analysis-not-tracked") { NotTrackedCard(month, trackingStart) { onTrackMonth(month) } }
                    report != null -> monthAnalysis(
                        // Habitude : la moyenne des mois renseignés précédents, sur 12 mois glissants au plus
                        // (l'année d'avant comprise en début d'année).
                        averages = (previousYear?.months.orEmpty() + monthYear?.months.orEmpty())
                            .filter { it.tracked && it.month !in excludedMonths && it.month < month && !it.month.isBefore(month.minusMonths(12)) }.let { previous ->
                            if (previous.isEmpty()) emptyMap() else report.categories.associate { category ->
                                category.name to previous.fold(Money.ZERO) { sum, m ->
                                    sum + (m.categories.firstOrNull { it.name == category.name }?.spent ?: Money.ZERO)
                                }.divideFloor(previous.size.toLong())
                            }
                        },
                        report = report,
                        counted = month !in excludedMonths,
                        // L'option ne concerne qu'un mois terminé : le mois en cours n'entre pas dans les moyennes.
                        onSetCounted = if (month < current) ({ counted -> onSetMonthCounted(month, counted) }) else null,
                        pace = pace?.takeIf { it.month == month && month == current },
                        board = board,
                        groups = groups,
                        onOpenLine = onOpenLine,
                        onSort = onSort,
                        onInfo = { info = it },
                        review = review,
                        onAskReview = { asking = it },
                        onClearReview = { onSaveReview(month, null) },
                    )
                }
            }
            AnalysisPeriod.YEAR -> {
                item(key = "analysis-year-switch") {
                    PeriodSwitcher(
                        label = year.toString(),
                        canPrevious = true,
                        canNext = canNextYear,
                        onPrevious = onPreviousYear,
                        onNext = onNextYear,
                        note = if (year == current.year) "de janvier à aujourd'hui" else "année terminée",
                    )
                }
                if (yearSpending != null && yearSpending.year == year) {
                    yearAnalysis(yearSpending, onSort, recurring) { selected ->
                        onSelectMonth(selected)
                        onShowMonth()
                    }
                }
            }
        }
    }

    // Ce que le total compte, et ce qu'il laisse de côté (pour vérifier qu'il est complet).
    info?.let { report ->
        InfoSheet(
            title = "Ce qui est compté",
            lines = listOf(InfoLine("Dépenses rangées (quotidien, charges, budgets rares)", report.total.formatted())) +
                listOfNotNull(
                    report.unsorted.takeIf { it.isPositive }?.let { InfoLine("Pas compté : encore à trier", it.formatted()) },
                    report.outOfBudget.takeIf { it.isPositive }?.let { InfoLine("Pas compté : hors budget", it.formatted()) },
                    report.toAccounts.takeIf { it.isPositive }?.let { InfoLine("Pas compté : viré vers tes comptes", it.formatted()) },
                ),
            note = (if (report.budget.isPositive) "Budget du mois : ${report.budget.formatted()}. " else "") +
                "Sur chaque barre, le trait marque le budget de la catégorie. L'épargne n'est pas une dépense.",
            onDismiss = { info = null },
            // L'option du mois vit ici, avec ce qui est compté (un mois terminé seulement).
            extra = if (report.month < current) ({
                CountedLine(report.month, report.month !in excludedMonths) { counted -> onSetMonthCounted(report.month, counted) }
            }) else null,
        )
    }

    asking?.let { report ->
        val earlier = (previousYear?.months.orEmpty() + monthYear?.months.orEmpty())
            .filter { it.tracked && it.month < report.month && !it.month.isBefore(report.month.minusMonths(12)) }
        val outflows by remember(report.month) { monthOutflows(report.month) }.collectAsState(initial = emptyList())
        MonthReviewDialog(
            quickPrompt = fr.baptiste.bank.domain.budget.monthReviewPrompt(report, earlier, savings?.forecast, savings?.target),
            fullPrompt = { merchants ->
                fr.baptiste.bank.domain.budget.monthAnalysisPrompt(
                    fr.baptiste.bank.domain.budget.MonthAnalysisInput(
                        month = report,
                        previous = earlier,
                        outflows = outflows,
                        recurring = recurring,
                        income = income,
                        savingsForecast = savings?.forecast,
                        savingsTarget = savings?.target,
                        includeMerchants = merchants,
                    ),
                )
            },
            onSave = { text ->
                onSaveReview(report.month, text)
                asking = null
            },
            onDismiss = { asking = null },
        )
    }

}

private fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)

private fun shortMonth(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT, Locale.FRANCE)

/** « −196 € vs budget », « +104 € vs 4 oct. » : un écart porte son signe et sa référence. */
internal fun Money.signedVs(reference: String): String =
    (if (isNegative) "−" else "+") + (if (isNegative) -this else this).summary() + " vs " + reference

/** Le mois : total, reste à trier, une barre par catégorie. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun LazyListScope.monthAnalysis(
    report: MonthSpending,
    counted: Boolean = true,
    onSetCounted: ((Boolean) -> Unit)? = null,
    /** Mois en cours seulement : on compare à la même date du mois précédent, pas au budget du mois entier. */
    pace: fr.baptiste.bank.domain.budget.MonthPace? = null,
    board: fr.baptiste.bank.domain.budget.Dashboard?,
    groups: List<fr.baptiste.bank.domain.budget.GroupNode>,
    onOpenLine: (fr.baptiste.bank.domain.budget.EnvelopeLine) -> Unit,
    onSort: () -> Unit,
    onInfo: (MonthSpending) -> Unit,
    averages: Map<String, Money> = emptyMap(),
    review: String?,
    onAskReview: (MonthSpending) -> Unit,
    onClearReview: () -> Unit,
) {
    item(key = "analysis-month-total") {
        GlassCard(strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Dépensé en ${monthName(report.month)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                fr.baptiste.bank.ui.components.InfoButton(onClick = { onInfo(report) }, contentDescription = "Ce qui est compté")
            }
            Text(report.total.summary(), style = MaterialTheme.typography.displaySmall)
            // Comme le prototype : le chiffre, puis des puces courtes, signées, avec leur référence
            // (règle 4 du doc UX). Le budget du mois et le détail sont derrière ⓘ.
            val chips = buildList {
                val sameDay = pace?.previousSameDay
                if (pace != null && sameDay != null) {
                    val previous = report.month.minusMonths(1)
                    val through = minOf(pace.cumulative.size, previous.lengthOfMonth())
                    val gap = report.total - sameDay
                    add(gap.signedVs("$through ${shortMonth(previous)}") to if (gap.isPositive) fr.baptiste.bank.ui.components.StatusKind.WARNING else fr.baptiste.bank.ui.components.StatusKind.GAIN)
                } else if (pace == null && report.budget.isPositive) {
                    val gap = report.total - report.budget
                    add(gap.signedVs("budget") to if (gap.isPositive) fr.baptiste.bank.ui.components.StatusKind.OVER else fr.baptiste.bank.ui.components.StatusKind.GAIN)
                }
                if (!counted) add("Hors référence" to fr.baptiste.bank.ui.components.StatusKind.GOOD)
            }
            if (chips.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    chips.forEach { (text, kind) -> fr.baptiste.bank.ui.components.StatusChip(text, kind) }
                }
            }
        }
    }
    if (report.categories.isEmpty()) {
        item(key = "analysis-month-empty") {
            GlassCard(contentPadding = 16.dp) {
                Text("Aucune dépense rangée ce mois-ci.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    // Le dépensé du mois de chaque enveloppe : ce que chaque branche additionne.
    val monthSpent = report.categories.flatMap { it.envelopes + it.descendants.flatMap { child -> child.envelopes } }
        .associate { it.envelope.id to it.spent }
    sectionHeader("analysis-tree-label", "Enveloppes")
    if (board != null) {
        item(key = "analysis-tree") { SpendingTree(board.envelopes, monthSpent, groups, onOpenLine) }
    }
    reviewSection(report, review, onAskReview, onClearReview)
}

/** L'avis de Claude sur le mois : gardé avec lui, ou à demander. */
private fun LazyListScope.reviewSection(report: MonthSpending, review: String?, onAsk: (MonthSpending) -> Unit, onClear: () -> Unit) {
    sectionHeader(
        "analysis-review-label",
        "L'avis de Claude",
        action = if (review != null) "Redemander" else null,
        onAction = { onAsk(report) },
    )
    item(key = "analysis-review") {
        GlassCard(contentPadding = 16.dp, onClick = if (review == null) ({ onAsk(report) }) else null) {
            if (review == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Demander un avis sur ce mois", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(
                            "Ce qui va, ce qui dérive, une action pour le mois prochain",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Chevron()
                }
            } else {
                Text(review, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onClear) { Text("Effacer") }
            }
        }
    }
}

/**
 * L'option du mois : le compter dans les moyennes (montants proposés, dépense habituelle, moyennes
 * et épargne de l'année). Un mois incomplet est écarté par défaut, et le dit.
 */
@Composable
private fun CountedLine(month: YearMonth, counted: Boolean, onChange: (Boolean) -> Unit) {
    val first = LocalFirstOperationDate.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            // Un mot court ; ce qu'il change tient en une ligne (règles 2 et 3 du doc UX).
            Text("Mois de référence", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (fr.baptiste.bank.domain.budget.isPartialFirstMonth(month, first) && first != null) {
                    "Incomplet : données dès le ${first.dayOfMonth}"
                } else {
                    "Sert aux moyennes et aux montants proposés"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(checked = counted, onCheckedChange = onChange)
    }
}

/** Ce que proposent les lignes glissées de Dépenses › Catégories. */
data class CategoryRowActions(
    val onMoveEnvelope: (fr.baptiste.bank.domain.model.Envelope) -> Unit = {},
    val onEditEnvelope: (fr.baptiste.bank.domain.model.Envelope) -> Unit = {},
    val onMoveCategory: (String) -> Unit = {},
    val onRenameCategory: (String) -> Unit = {},
)

/**
 * Une catégorie et, dépliée, ses sous-catégories puis ses enveloppes, décalées d'un cran par
 * niveau. Chaque ligne se glisse : « Déplacer » et « Renommer » pour une catégorie (pas pour
 * « Autres »), « Déplacer » et « Modifier » pour une enveloppe.
 */
/** « 65,19 € encore à trier » : le total du mois n'est pas complet. */
@Composable
private fun UnsortedLine(amount: Money, onSort: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(BankTheme.status.warning))
        Text(
            "${amount.summary()} à trier",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSort) { Text("Trier") }
    }
}

/**
 * Barre horizontale : le dépensé en clair, sa part au-delà du budget en rouge, et un trait qui
 * dépasse de la barre à l'endroit du budget.
 */
@Composable
private fun SpendBar(spent: Money, budget: Money, scale: Long, color: Color = MonthBarColor) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val over = BankTheme.status.over
    val tick = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val barHeight = 8.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        fun x(cents: Long) = size.width * cents.coerceAtLeast(0L) / scale
        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
        val spentX = x(spent.cents)
        if (budget.isPositive && spent > budget) {
            val budgetX = x(budget.cents)
            drawRoundRect(over, topLeft = Offset(0f, top), size = Size(spentX, barHeight), cornerRadius = radius)
            drawRoundRect(color, topLeft = Offset(0f, top), size = Size(budgetX, barHeight), cornerRadius = radius)
        } else if (spentX > 0f) {
            drawRoundRect(color, topLeft = Offset(0f, top), size = Size(spentX.coerceAtLeast(barHeight), barHeight), cornerRadius = radius)
        }
        if (budget.isPositive) {
            val budgetX = x(budget.cents).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
            drawLine(tick, Offset(budgetX, 0f), Offset(budgetX, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/** L'année : total et moyenne, histogramme empilé, tableau par catégorie. */
internal fun LazyListScope.yearAnalysis(
    report: YearSpending,
    onSort: () -> Unit,
    recurring: List<fr.baptiste.bank.domain.budget.RecurringExpense> = emptyList(),
    onSelectMonth: (YearMonth) -> Unit,
) {
    val tracked = report.tracked
    item(key = "analysis-year-total") {
        GlassCard(strong = true) {
            Text(
                "Dépensé en ${report.year}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(report.total.summary(), style = MaterialTheme.typography.displaySmall)
            Text(
                if (tracked.isEmpty()) {
                    "Aucun mois renseigné cette année."
                } else {
                    val done = report.complete.size
                    when {
                        done == 0 -> "Premier mois en cours : pas encore de moyenne."
                        else -> "${report.monthlyAverage.summary()} par mois en moyenne"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (tracked.isEmpty()) return
    val categories = report.categories()
    val first = categories.getOrNull(0)?.name
    val second = categories.getOrNull(1)?.name
    item(key = "analysis-year-chart") {
        GlassCard(contentPadding = 16.dp) {
            Text("Mois par mois", style = MaterialTheme.typography.titleSmall)
            YearBars(report, first, second, onSelectMonth)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOfNotNull(
                    first?.let { it to FirstCategoryColor },
                    second?.let { it to SecondCategoryColor },
                    if (categories.size > 2) "Autres catégories" to OtherCategoriesColor else null,
                ).forEach { (name, color) -> LegendSwatch(color, name) }
            }
            Text(
                "Pointillés : moyenne. Touche un mois pour le détailler.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    sectionHeader("analysis-year-table-label", "Par catégorie", trailing = "total · moyenne · écart au budget")
    item(key = "analysis-year-table") {
        GlassRowsCard(categories, inset = 0.dp) { category ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${category.average.summary()} / mois",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // L'évolution mois par mois : « est-ce que ça monte ? » sans calcul.
                        Sparkline(
                            report.complete.map { month -> month.categories.firstOrNull { it.name == category.name }?.spent ?: Money.ZERO },
                            Modifier.padding(start = 8.dp).size(width = 56.dp, height = 16.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(category.total.summary(), style = MaterialTheme.typography.titleMedium)
                    if (category.budget.isPositive) {
                        val gap = category.gap
                        Text(
                            when {
                                gap.isPositive -> "+${gap.summary()} sur le budget"
                                gap.isNegative -> "${(-gap).summary()} sous le budget"
                                else -> "pile dans le budget"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                gap.isPositive -> BankTheme.status.over
                                gap.isNegative -> BankTheme.status.gain
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    } else {
                        Text("sans budget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    // Ce qui revient chaque mois : abonnements, forfaits, prélèvements.
    if (recurring.isNotEmpty()) {
        val total = recurring.fold(Money.ZERO) { sum, it -> sum + it.monthly }
        sectionHeader("analysis-recurring-label", "Chaque mois", trailing = "${total.summary()} / mois")
        item(key = "analysis-recurring") {
            GlassRowsCard(recurring.take(8), inset = 0.dp) { expense ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(expense.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${expense.months} mois de suite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(expense.monthly.summary(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Petite courbe d'une série (une valeur par mois), sans axe : la tendance seulement. */
@Composable
private fun Sparkline(values: List<Money>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val low = values.minOf { it.cents }
    val span = (values.maxOf { it.cents } - low).coerceAtLeast(1L)
    Canvas(modifier) {
        val step = size.width / (values.size - 1)
        fun point(index: Int) = Offset(step * index, size.height - size.height * (values[index].cents - low) / span)
        for (index in 1 until values.size) {
            drawLine(color, point(index - 1), point(index), strokeWidth = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        drawCircle(color, radius = 2.dp.toPx(), center = point(values.lastIndex))
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 6.dp).size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Une barre par mois, empilée de bas en haut : la plus grosse catégorie de l'année, la deuxième,
 * puis le reste ; un filet de 2 dp sépare les parts. Un mois non renseigné est une colonne grisée.
 */
@Composable
private fun YearBars(report: YearSpending, first: String?, second: String?, onSelectMonth: (YearMonth) -> Unit) {
    val months = report.months
    val top = months.maxOfOrNull { it.total.cents }?.coerceAtLeast(1L) ?: 1L
    val average = report.monthlyAverage.cents
    val untracked = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Box {
            Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp)) {
                val slot = size.width / 12f
                val barWidth = slot * 0.6f
                val gap = 2.dp.toPx()
                fun height(cents: Long) = size.height * cents.coerceAtLeast(0L) / top
                months.forEachIndexed { index, month ->
                    val left = slot * index + (slot - barWidth) / 2
                    if (!month.tracked) {
                        drawRoundRect(untracked, topLeft = Offset(left, 0f), size = Size(barWidth, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                        return@forEachIndexed
                    }
                    val firstCents = month.categories.firstOrNull { it.name == first }?.spent?.cents ?: 0L
                    val secondCents = month.categories.firstOrNull { it.name == second }?.spent?.cents ?: 0L
                    val restCents = month.total.cents - firstCents - secondCents
                    var bottom = size.height
                    // Le mois en cours, incomplet, est atténué : il ne se compare pas encore aux autres.
                    val alpha = if (month.month == report.inProgress) 0.45f else 1f
                    listOf(FirstCategoryColor to firstCents, SecondCategoryColor to secondCents, OtherCategoriesColor to restCents)
                        .filter { it.second > 0 }
                        .forEach { (color, cents) ->
                            val h = height(cents)
                            val drawn = (h - gap).coerceAtLeast(1f)
                            drawRoundRect(color.copy(alpha = alpha), topLeft = Offset(left, bottom - drawn), size = Size(barWidth, drawn), cornerRadius = CornerRadius(4.dp.toPx()))
                            bottom -= h
                        }
                }
                if (average > 0) {
                    val y = size.height - height(average)
                    drawLine(
                        guide,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )
                }
            }
            // Zones tactiles : une par mois renseigné, sur toute la hauteur.
            Row(Modifier.fillMaxWidth()) {
                (1..12).forEach { number ->
                    val month = months.getOrNull(number - 1)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(140.dp)
                            .then(
                                if (month != null && month.tracked) {
                                    Modifier.clickable(role = Role.Button, onClickLabel = "Détailler ${monthName(month.month)}") {
                                        onSelectMonth(month.month)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (1..12).forEach { number ->
                val month = YearMonth.of(report.year, number)
                Text(
                    month.month.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.FRANCE),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (months.getOrNull(number - 1)?.tracked == true) 1f else 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Demander l'avis de Claude, rapide (totaux seulement) ou complet (enveloppes, évolution,
 * habitudes, abonnements ; commerçants si on le veut). On l'envoie dans l'app Claude, puis on
 * colle la réponse, gardée avec le mois.
 */
@Composable
private fun MonthReviewDialog(
    quickPrompt: String,
    fullPrompt: (includeMerchants: Boolean) -> String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reply by rememberSaveable { mutableStateOf("") }
    var full by rememberSaveable { mutableStateOf(true) }
    var merchants by rememberSaveable { mutableStateOf(true) }
    val prompt = if (full) fullPrompt(merchants) else quickPrompt
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("L'avis de Claude") },
        text = {
            fr.baptiste.bank.ui.components.BlurBehindWindow()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(selected = full, onClick = { full = true }, label = { Text("Analyse complète") })
                    androidx.compose.material3.FilterChip(selected = !full, onClick = { full = false }, label = { Text("Avis rapide") })
                }
                Text(
                    if (full) {
                        "Ce qui part : ton budget enveloppe par enveloppe, tes mois précédents, ton épargne, tes abonnements" +
                            (if (merchants) " et tes commerçants (libellés nettoyés)" else "") + ". Aucun compte, aucun solde, aucun numéro."
                    } else {
                        "Ce qui part : tes totaux par catégorie, ton budget et ta moyenne. Aucun libellé, aucun compte, aucun solde."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (full) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = merchants, onCheckedChange = { merchants = it })
                        Text("Inclure les noms des commerçants", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                androidx.compose.material3.Button(
                    onClick = { fr.baptiste.bank.ui.assistant.openClaudeWith(context, prompt) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Copier et ouvrir Claude") }
                androidx.compose.material3.OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it },
                    label = { Text("Colle ici la réponse de Claude") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(reply) }, enabled = reply.isNotBlank()) { Text("Garder l'avis") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
