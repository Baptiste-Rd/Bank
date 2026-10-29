package fr.baptiste.bank.ui.glass

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
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.BankTheme
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
    initialYear: Boolean = false,
) {
    var period by rememberSaveable { mutableStateOf(if (initialYear) AnalysisPeriod.YEAR else AnalysisPeriod.MONTH) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<EnvelopeSpent?>(null) }
    val current = YearMonth.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()
        item(key = "analysis-period") {
            SegmentedToggle(listOf("Mois", "Année"), period.ordinal) { period = AnalysisPeriod.entries[it] }
        }
        when (period) {
            AnalysisPeriod.MONTH -> {
                item(key = "analysis-month-switch") {
                    PeriodSwitcher(
                        label = month.label.replaceFirstChar { it.titlecase() },
                        canPrevious = true,
                        canNext = canNextMonth,
                        onPrevious = onPreviousMonth,
                        onNext = onNextMonth,
                        note = if (month == current) "au ${java.time.LocalDate.now().dayOfMonth} ${monthName(month)}" else "Mois terminé",
                    )
                }
                val report = monthYear?.takeIf { it.year == month.year }?.months?.firstOrNull { it.month == month }
                when {
                    trackingStart != null && month < trackingStart ->
                        item(key = "analysis-not-tracked") { NotTrackedCard(month, trackingStart) { onTrackMonth(month) } }
                    report != null -> monthAnalysis(
                        report = report,
                        expanded = expanded,
                        onToggle = { name -> expanded = if (expanded == name) null else name },
                        onOpenEnvelope = { opened = it },
                        onSort = onSort,
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
                    yearAnalysis(yearSpending, onSort) { selected ->
                        onSelectMonth(selected)
                        period = AnalysisPeriod.MONTH
                    }
                }
            }
        }
    }

    opened?.let { line ->
        val operations by remember(line.envelope.id, month) { envelopeOperations(line.envelope.id, month) }
            .collectAsState(initial = null)
        val day = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
        InfoSheet(
            title = "${line.envelope.name} · ${monthName(month)}",
            lines = operations.orEmpty().map { operation ->
                InfoLine("${operation.date.format(day)} · ${operation.label}", Money(-operation.amountCents).formatted())
            } + InfoLine("Total", line.spent.formatted()),
            note = when {
                operations == null -> "Chargement…"
                operations.isNullOrEmpty() -> "Aucune opération ce mois-ci."
                line.budget.isPositive -> "Budget du mois : ${line.budget.formatted()}."
                else -> null
            },
            onDismiss = { opened = null },
        )
    }
}

private fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)

/** Le mois : total, reste à trier, une barre par catégorie. */
private fun LazyListScope.monthAnalysis(
    report: MonthSpending,
    expanded: String?,
    onToggle: (String) -> Unit,
    onOpenEnvelope: (EnvelopeSpent) -> Unit,
    onSort: () -> Unit,
) {
    item(key = "analysis-month-total") {
        GlassCard(strong = true) {
            Text(
                "Dépensé en ${monthName(report.month)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(report.total.formatted(), style = MaterialTheme.typography.displaySmall)
            if (report.budget.isPositive) {
                val gap = report.budget - report.total
                Text(
                    if (gap.isNegative) {
                        "${(-gap).formatted()} de plus que le budget (${report.budget.formatted()})"
                    } else {
                        "${gap.formatted()} de moins que le budget (${report.budget.formatted()})"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (gap.isNegative) BankTheme.status.over else BankTheme.status.gain,
                )
            }
            Text(
                "Quotidien, charges et provisions ; hors épargne et virements vers tes comptes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.unsorted.isPositive) UnsortedLine(report.unsorted, onSort)
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
    sectionHeader("analysis-categories-label", "Par catégorie", trailing = "trait : budget du mois")
    item(key = "analysis-categories") {
        val scale = report.categories.maxOf { maxOf(it.spent.cents, it.budget.cents) }.coerceAtLeast(1L)
        GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
            report.categories.forEachIndexed { index, category ->
                if (index > 0) RowDivider(0.dp)
                CategoryRow(
                    category = category,
                    scale = scale,
                    expanded = expanded == category.name,
                    onClick = { onToggle(category.name) },
                )
                if (expanded == category.name) {
                    category.envelopes.forEach { line -> EnvelopeSpentRow(line) { onOpenEnvelope(line) } }
                }
            }
        }
    }
}

/** « 65,19 € encore à trier » : le total du mois n'est pas complet. */
@Composable
private fun UnsortedLine(amount: Money, onSort: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(BankTheme.status.warning))
        Text(
            "${amount.formatted()} encore à trier",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSort) { Text("Trier") }
    }
}

/** Une catégorie : nom, dépensé sur budget, barre (le trait marque le budget). */
@Composable
private fun CategoryRow(category: CategorySpent, scale: Long, expanded: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = if (expanded) "Masquer les enveloppes" else "Voir les enveloppes")
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(category.spent.formatted(), style = MaterialTheme.typography.titleMedium)
        }
        SpendBar(category.spent, category.budget, scale)
        Text(
            buildString {
                append(if (category.budget.isPositive) "sur ${category.budget.formatted()}" else "hors budget du mois")
                append(" · ${category.envelopes.size} enveloppe${if (category.envelopes.size > 1) "s" else ""}")
                if (category.over.isPositive) append(" · dépassé de ${category.over.formatted()}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (category.over.isPositive) BankTheme.status.over else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnvelopeSpentRow(line: EnvelopeSpent, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = "Voir les opérations")
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.envelope.name, style = MaterialTheme.typography.bodyMedium)
            if (line.budget.isPositive) {
                Text(
                    "sur ${line.budget.formatted()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            line.spent.formatted(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.budget.isPositive && line.spent > line.budget) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
        )
        Chevron()
    }
}

/**
 * Barre horizontale : le dépensé en clair, sa part au-delà du budget en rouge, et un trait qui
 * dépasse de la barre à l'endroit du budget.
 */
@Composable
private fun SpendBar(spent: Money, budget: Money, scale: Long) {
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
            drawRoundRect(MonthBarColor, topLeft = Offset(0f, top), size = Size(budgetX, barHeight), cornerRadius = radius)
        } else if (spentX > 0f) {
            drawRoundRect(MonthBarColor, topLeft = Offset(0f, top), size = Size(spentX.coerceAtLeast(barHeight), barHeight), cornerRadius = radius)
        }
        if (budget.isPositive) {
            val budgetX = x(budget.cents).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
            drawLine(tick, Offset(budgetX, 0f), Offset(budgetX, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/** L'année : total et moyenne, histogramme empilé, tableau par catégorie. */
private fun LazyListScope.yearAnalysis(report: YearSpending, onSort: () -> Unit, onSelectMonth: (YearMonth) -> Unit) {
    val tracked = report.tracked
    item(key = "analysis-year-total") {
        GlassCard(strong = true) {
            Text(
                "Dépensé en ${report.year}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(report.total.formatted(), style = MaterialTheme.typography.displaySmall)
            Text(
                if (tracked.isEmpty()) {
                    "Aucun mois renseigné cette année."
                } else {
                    "${report.monthlyAverage.formatted()} par mois en moyenne, sur ${tracked.size} mois renseigné${if (tracked.size > 1) "s" else ""}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.unsorted.isPositive) UnsortedLine(report.unsorted, onSort)
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
                "Pointillés : moyenne mensuelle. Mois grisés : non renseignés. Touche un mois pour le détailler.",
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
                    Text(
                        "${category.average.formatted()} / mois",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(category.total.formatted(), style = MaterialTheme.typography.titleMedium)
                    if (category.budget.isPositive) {
                        val gap = category.gap
                        Text(
                            when {
                                gap.isPositive -> "+${gap.formatted()} sur le budget"
                                gap.isNegative -> "${(-gap).formatted()} sous le budget"
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
                    listOf(FirstCategoryColor to firstCents, SecondCategoryColor to secondCents, OtherCategoriesColor to restCents)
                        .filter { it.second > 0 }
                        .forEach { (color, cents) ->
                            val h = height(cents)
                            val drawn = (h - gap).coerceAtLeast(1f)
                            drawRoundRect(color, topLeft = Offset(left, bottom - drawn), size = Size(barWidth, drawn), cornerRadius = CornerRadius(4.dp.toPx()))
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
