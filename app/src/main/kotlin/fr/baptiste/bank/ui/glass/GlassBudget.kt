package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.YearOverview
import fr.baptiste.bank.domain.budget.CurrentAccountExplained
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.closings
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.InfoButton
import fr.baptiste.bank.ui.components.InfoLine
import fr.baptiste.bank.ui.components.InfoSheet
import fr.baptiste.bank.ui.components.RowActionButton
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.envelopes.CategoryAvatar
import fr.baptiste.bank.ui.envelopes.EnvelopeFamily
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.envelopes.TemplateChips
import fr.baptiste.bank.ui.envelopes.envelopeTemplates
import fr.baptiste.bank.ui.envelopes.family
import fr.baptiste.bank.ui.envelopes.template
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.home.SavingsBarsChart
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.ui.theme.envelopeTypeColor
import java.time.YearMonth

/*
 * Onglet Budget : « où va mon salaire ? », au mois ou à l'année.
 *
 * Mois (consultable en arrière avec ‹ ›) :
 * 1. le plan du mois : le salaire découpé en charges fixes, provisions, épargne et budget du
 *    quotidien (dont le non réparti) ;
 * 2. une carte par famille ; les enveloppes d'un même groupe sont réunies sous une ligne de total.
 * Un mois passé est en lecture seule : ses chiffres sont définitifs, on ne couvre plus rien.
 *
 * Année : l'épargne cumulée et ses mois, les dépenses de chaque famille face au prévu, et
 * l'avancement des provisions.
 */

/** Une part du plan du mois. */
private data class PlanPart(val family: EnvelopeFamily, val amount: Money)

/** Vue de l'onglet Budget. */
enum class BudgetView { MONTH, YEAR }

/** Ce qu'une carte de famille empile : une enveloppe seule, ou une catégorie et ses enveloppes. */
private sealed interface FamilyEntry {
    data class Single(val line: EnvelopeLine) : FamilyEntry
    data class Group(val name: String, val lines: List<EnvelopeLine>) : FamilyEntry
}

/**
 * Range les lignes d'une famille : une catégorie qui y réunit au moins deux enveloppes prend la
 * place de la première ; une enveloppe seule de sa catégorie (ou dans « Autres ») reste seule.
 */
private fun List<EnvelopeLine>.entries(): List<FamilyEntry> {
    val seen = mutableSetOf<String>()
    val shared = mapNotNull { it.envelope.group }.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
    return mapNotNull { line ->
        val group = line.envelope.group
        when {
            group == null || group !in shared -> FamilyEntry.Single(line)
            seen.add(group) -> FamilyEntry.Group(group, filter { it.envelope.group == group })
            else -> null
        }
    }
}

private fun planParts(dashboard: Dashboard): List<PlanPart> {
    val byFamily = dashboard.envelopes.groupBy { it.envelope.type.family }
    fun share(family: EnvelopeFamily) = byFamily[family].orEmpty().fold(Money.ZERO) { sum, line -> sum + line.monthlyShare }
    return listOf(
        PlanPart(EnvelopeFamily.FIXED, share(EnvelopeFamily.FIXED)),
        PlanPart(EnvelopeFamily.PROVISIONS, share(EnvelopeFamily.PROVISIONS)),
        PlanPart(EnvelopeFamily.SAVINGS, share(EnvelopeFamily.SAVINGS)),
        PlanPart(EnvelopeFamily.DAILY, dashboard.dailyBudget),
    )
}

@Composable
fun GlassBudgetContent(
    dashboard: Dashboard?,
    monthlyIncome: Money?,
    archived: List<Envelope>,
    month: YearMonth,
    canNextMonth: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    yearOverview: YearOverview?,
    year: Int,
    canNextYear: Boolean,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onEditIncome: () -> Unit,
    onOpen: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
    onEdit: (Envelope) -> Unit,
    onRestore: (Envelope) -> Unit,
    initialView: BudgetView = BudgetView.MONTH,
    /** Compte courant décomposé (mois en cours) ; null sans solde connu. */
    currentAccount: CurrentAccountExplained? = null,
    onSetBalance: () -> Unit = {},
    /** Viré vers mes livrets et placements le mois affiché (bilan). */
    monthTransfers: Money? = null,
    /** Début du suivi : un mois avant lui est « non renseigné ». */
    trackingStart: YearMonth? = null,
    onTrackMonth: (YearMonth) -> Unit = {},
    onTryPlan: () -> Unit = {},
) {
    var view by rememberSaveable { mutableStateOf(initialView) }
    var showInfo by remember { mutableStateOf(false) }
    val isCurrentMonth = month == YearMonth.now()
    val parts = dashboard?.let(::planParts).orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Budget")
        item(key = "view") {
            SegmentedToggle(listOf("Mois", "Année"), view.ordinal) { view = BudgetView.entries[it] }
        }
        when (view) {
            BudgetView.MONTH -> {
                item(key = "month-switch") {
                    PeriodSwitcher(
                        label = month.label.replaceFirstChar { it.titlecase() },
                        canPrevious = true,
                        canNext = canNextMonth,
                        onPrevious = onPreviousMonth,
                        onNext = onNextMonth,
                        note = if (isCurrentMonth) null else "Mois terminé",
                    )
                }
                if (trackingStart != null && month < trackingStart) {
                    item(key = "not-tracked") { NotTrackedCard(month, trackingStart) { onTrackMonth(month) } }
                } else if (dashboard != null) {
                    monthContent(
                        dashboard = dashboard,
                        parts = parts,
                        monthlyIncome = monthlyIncome,
                        archived = archived,
                        readOnly = !isCurrentMonth,
                        onShowInfo = { showInfo = true },
                        onEditIncome = onEditIncome,
                        onOpen = onOpen,
                        onCover = onCover,
                        onEdit = onEdit,
                        onRestore = onRestore,
                        currentAccount = if (isCurrentMonth) currentAccount else null,
                        showAccountCard = isCurrentMonth,
                        onSetBalance = onSetBalance,
                        monthTransfers = monthTransfers,
                        onTryPlan = if (isCurrentMonth) onTryPlan else null,
                    )
                }
            }
            BudgetView.YEAR -> {
                item(key = "year-switch") {
                    PeriodSwitcher(
                        label = year.toString(),
                        canPrevious = true,
                        canNext = canNextYear,
                        onPrevious = onPreviousYear,
                        onNext = onNextYear,
                        note = yearOverview?.let {
                            if (it.year == YearMonth.now().year) "de janvier à aujourd'hui" else "année terminée"
                        },
                    )
                }
                if (yearOverview != null && yearOverview.year == year) yearContent(yearOverview)
            }
        }
    }

    if (showInfo) {
        InfoSheet(
            title = "Comment le salaire est réparti",
            lines = listOfNotNull(monthlyIncome?.let { InfoLine("Salaire", it.formatted()) }) +
                parts.filter { it.family != EnvelopeFamily.DAILY }.map { InfoLine(it.family.label, "−${it.amount.formatted()}") } +
                listOfNotNull(parts.firstOrNull { it.family == EnvelopeFamily.DAILY }?.let { InfoLine("Budget du quotidien", it.amount.formatted()) }),
            note = "Chaque enveloppe prend sa part chaque mois (une enveloppe annuelle, un douzième). Ce qui reste " +
                "fait le budget du quotidien, partagé en reste du jour. Un achat prévu prend sa part ici : " +
                "le quotidien, donc l'épargne prévue, baisse d'autant.",
            onDismiss = { showInfo = false },
        )
    }
}

/** Vue du mois : le plan, puis une carte par famille. */
private fun LazyListScope.monthContent(
    dashboard: Dashboard,
    parts: List<PlanPart>,
    monthlyIncome: Money?,
    archived: List<Envelope>,
    readOnly: Boolean,
    onShowInfo: () -> Unit,
    onEditIncome: () -> Unit,
    onOpen: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
    onEdit: (Envelope) -> Unit,
    onRestore: (Envelope) -> Unit,
    currentAccount: CurrentAccountExplained?,
    showAccountCard: Boolean,
    onSetBalance: () -> Unit,
    monthTransfers: Money?,
    onTryPlan: (() -> Unit)? = null,
) {
    val byFamily = dashboard.envelopes.groupBy { it.envelope.type.family }
    val usedNames = dashboard.envelopes.map { it.envelope.name.lowercase() }.toSet()
    val suggestions = envelopeTemplates.filter { (name, _) -> name.lowercase() !in usedNames }

    // Un mois terminé commence par son bilan : où est allé ce qui restait, d'où vient ce qui manquait.
    if (readOnly && dashboard.savings.isFinal) item(key = "review") { MonthReview(dashboard, onEdit, monthTransfers) }

    item(key = "plan") {
        GlassCard(contentPadding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Plan du mois", style = MaterialTheme.typography.titleMedium)
                    Text(
                        monthlyIncome?.let { "Salaire ${it.formatted()}" } ?: "Salaire non renseigné",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InfoButton(onClick = onShowInfo, contentDescription = "Comment le salaire est réparti")
            }
            if (monthlyIncome == null) {
                RowActionButton("Indiquer mon salaire", onClick = onEditIncome, modifier = Modifier.padding(top = 8.dp))
            } else {
                PlanBar(parts, Modifier.padding(vertical = 12.dp))
                parts.forEach { part ->
                    val unallocated = dashboard.unallocated
                    LegendRow(
                        color = part.family.color,
                        label = part.family.label,
                        value = part.amount.formatted(),
                        detail = if (part.family == EnvelopeFamily.DAILY && unallocated != null && unallocated.isPositive) {
                            "dont ${unallocated.formatted()} non réparti"
                        } else {
                            null
                        },
                        // Du salaire que rien ne réclame : un bénéfice, en vert.
                        detailColor = BankTheme.status.gain,
                    )
                }
                // Essayer un projet ou une autre épargne, sans rien changer tant qu'on n'applique pas.
                if (onTryPlan != null) {
                    RowActionButton("Essayer un plan", onClick = onTryPlan, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    // Le compte courant, expliqué : du solde de la banque au reste du mois.
    if (showAccountCard) {
        sectionHeader("account-label", "Compte courant", action = "Solde", onAction = onSetBalance)
        item(key = "account") { CurrentAccountCard(currentAccount, onSetBalance) }
    }

    EnvelopeFamily.entries.forEach { family ->
        val familyLines = byFamily[family].orEmpty()
        if (familyLines.isEmpty()) return@forEach
        val total = familyLines.fold(Money.ZERO) { sum, line -> sum + line.monthlyShare }
        sectionHeader("label-$family", family.label, trailing = "${total.formatted()} / mois", dot = family.color)
        item(key = "group-$family") {
            GlassRowsCard(familyLines.entries()) { entry ->
                when (entry) {
                    is FamilyEntry.Single -> EnvelopeRow(
                        entry.line,
                        onClick = if (readOnly) null else ({ onOpen(entry.line) }),
                        onCover = if (readOnly) null else ({ onCover(entry.line) }),
                        accent = envelopeTypeColor(entry.line.envelope.type),
                    )
                    is FamilyEntry.Group -> GroupBlock(entry, readOnly, onOpen, onCover)
                }
            }
        }
    }

    if (!readOnly && suggestions.isNotEmpty()) {
        item(key = "templates") {
            GlassCard(contentPadding = 16.dp) {
                TemplateChips(suggestions, onPick = { (name, type) -> onEdit(template(name, type)) })
            }
        }
    }
    if (!readOnly && archived.isNotEmpty()) {
        sectionHeader("archived-label", "Archivées", trailing = "${archived.size}")
        item(key = "archived") {
            GlassRowsCard(archived, inset = 0.dp) { envelope ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(envelope.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${envelope.amount.formatted()} ${envelope.frequency.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRestore(envelope) }) { Text("Restaurer") }
                }
            }
        }
    }
}

/**
 * Un groupe dans sa carte : une ligne de total (pastille du groupe, nombre d'enveloppes, total
 * dépensé sur total prévu, reste), puis ses enveloppes en retrait.
 */
@Composable
private fun GroupBlock(
    group: FamilyEntry.Group,
    readOnly: Boolean,
    onOpen: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
) {
    val spent = group.lines.fold(Money.ZERO) { sum, line -> sum + line.spent }
    val available = group.lines.fold(Money.ZERO) { sum, line -> sum + line.available }
    val remaining = available - spent
    val color = envelopeTypeColor(group.lines.first().envelope.type)
    val status = BankTheme.status
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(group.name, color)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(group.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "${spent.formatted()} sur ${available.formatted()} · ${group.lines.size} enveloppes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    remaining.formatted(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (remaining.isNegative) status.over else MaterialTheme.colorScheme.onSurface,
                )
                Text("reste", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val ratio = if (available.isPositive) (spent.cents.toFloat() / available.cents).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { ratio },
            color = if (remaining.isNegative) status.over else color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
        )
        group.lines.forEach { line ->
            Column(Modifier.padding(start = 24.dp)) {
                RowDivider()
                EnvelopeRow(
                    line,
                    onClick = if (readOnly) null else ({ onOpen(line) }),
                    onCover = if (readOnly) null else ({ onCover(line) }),
                    accent = envelopeTypeColor(line.envelope.type),
                )
            }
        }
    }
}

/** Vue de l'année : épargne cumulée, dépenses par famille face au prévu, provisions. */
private fun LazyListScope.yearContent(overview: YearOverview) {
    if (overview.monthsCounted == 0) {
        item(key = "year-empty") {
            GlassCard(contentPadding = 16.dp) {
                Text("Aucune opération enregistrée en ${overview.year}.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val board = overview.dashboard
    val accent = envelopeTypeColor(EnvelopeType.SAVINGS)
    val target = board.savings.target * overview.monthsCounted
    val parts = planParts(board)
    val families = listOf(EnvelopeFamily.DAILY, EnvelopeFamily.FIXED, EnvelopeFamily.PROVISIONS)

    item(key = "year-savings") {
        GlassCard(strong = true) {
            Text("Épargne de l'année", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(overview.savings.formatted(), style = MaterialTheme.typography.displaySmall)
            val gap = overview.savings - target
            Text(
                "${gap.signed()} par rapport à l'objectif (${target.formatted()} sur ${overview.monthsCounted} mois)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (gap.isNegative) BankTheme.status.warning else BankTheme.status.gain,
            )
            if (overview.months.size > 1) {
                SavingsBarsChart(overview.months, board.savings.target, accent, legend = null)
            }
        }
    }

    sectionHeader("year-spent-label", "Dépenses de l'année", trailing = "face au prévu")
    item(key = "year-spent") {
        GlassRowsCard(families, inset = 0.dp) { family ->
            val ids = board.envelopes.filter { it.envelope.type.family == family }.map { it.envelope.id }.toSet()
            val spent = overview.spentByEnvelope.filterKeys { it in ids }.values.fold(Money.ZERO) { sum, m -> sum + m }
            val planned = (parts.firstOrNull { it.family == family }?.amount ?: Money.ZERO) * overview.monthsCounted
            FamilyYearRow(family.label, family.color, spent, planned)
        }
    }

    val provisions = board.envelopes.filter { it.envelope.type.family == EnvelopeFamily.PROVISIONS }
    if (provisions.isNotEmpty()) {
        sectionHeader("year-provisions-label", "Provisions", dot = EnvelopeFamily.PROVISIONS.color)
        item(key = "year-provisions") {
            GlassRowsCard(provisions) { line ->
                EnvelopeRow(line, accent = envelopeTypeColor(line.envelope.type))
            }
        }
    }
}

/** Une famille sur l'année : dépensé sur prévu, et la barre (rouge au-delà du prévu). */
@Composable
private fun FamilyYearRow(label: String, color: Color, spent: Money, planned: Money) {
    val over = spent > planned
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Text(spent.formatted(), style = MaterialTheme.typography.titleMedium)
            Text(
                "  sur ${planned.formatted()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val ratio = if (planned.isPositive) (spent.cents.toFloat() / planned.cents).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { ratio },
            color = if (over) BankTheme.status.over else color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        )
    }
}

/** La barre du plan : une seule barre, une couleur par famille, en proportion des montants. */
@Composable
private fun PlanBar(parts: List<PlanPart>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { maxOf(it.amount.cents, 0L) }.coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        var x = 0f
        val gap = 2.dp.toPx()
        parts.forEach { part ->
            val width = size.width * maxOf(part.amount.cents, 0L) / total
            if (width > gap) {
                drawRoundRect(
                    color = part.family.color,
                    topLeft = Offset(x, 0f),
                    size = Size(width - gap, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            x += width
        }
    }
}

/**
 * Bilan d'un mois terminé. Règle unique : ce qui reste dans une enveloppe à la fin de sa période
 * va à l'épargne, un dépassement y est pris. Rien ne part seul dans une autre enveloppe ; pour
 * remettre de l'argent quelque part, on le prend sur l'épargne (« Ajouter » sur l'enveloppe).
 */
@Composable
private fun MonthReview(dashboard: Dashboard, onEdit: (Envelope) -> Unit, transfers: Money?) {
    val savings = dashboard.savings
    val closings = dashboard.closings()
    val status = BankTheme.status
    GlassCard(strong = true, contentPadding = 16.dp) {
        Text("Bilan de ${dashboard.today.monthTitle().substringBefore(' ').lowercase()}", style = MaterialTheme.typography.titleMedium)
        Text(
            "${savings.forecast.formatted()} épargnés",
            style = MaterialTheme.typography.titleLarge,
            color = if (savings.forecast.isNegative) status.over else MaterialTheme.colorScheme.onSurface,
        )
        Column(Modifier.padding(top = 8.dp)) {
            ReviewLine("Objectif et transferts vers l'épargne", savings.secured)
            ReviewLine("Reste du quotidien", savings.dailyMargin)
            closings.forEach { line ->
                val label = if (line.remaining.isNegative) "${line.envelope.name} · dépassé" else "${line.envelope.name} · reste"
                ReviewLine(label, line.remaining)
            }
            val savingsSpent = savings.overruns - closings.filter { it.remaining.isNegative }
                .fold(Money.ZERO) { sum, line -> sum - line.remaining }
            if (savingsSpent.isPositive) ReviewLine("Dépenses sur l'épargne", Money(-savingsSpent.cents))
        }
        // Ce qui a vraiment quitté le compte courant pour un livret ou un placement.
        if (transfers != null && savings.forecast.isPositive) {
            val left = savings.forecast - transfers
            Column(Modifier.padding(top = 8.dp)) {
                ReviewLine("Viré vers tes comptes", transfers)
                if (left.isPositive) ReviewLine("Resté sur le compte courant", left)
            }
        }
        Text(
            "Ce qui reste dans une enveloppe à la fin de sa période va à l'épargne ; un dépassement y est pris.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        closings.firstOrNull { it.remaining.isNegative }?.let { over ->
            RowActionButton("Ajuster ${over.envelope.name}", onClick = { onEdit(over.envelope) }, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ReviewLine(label: String, amount: Money) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            amount.signed(),
            style = MaterialTheme.typography.titleSmall,
            color = when {
                amount.isNegative -> BankTheme.status.warning
                amount.isPositive -> BankTheme.status.gain
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Le solde du compte courant, décomposé. Règle de lecture : solde = reste du mois + charges à
 * payer + épargne à virer + provisions mises de côté + libre. Un « libre » négatif dit qu'il
 * manque de l'argent sur le compte par rapport au budget.
 */
@Composable
private fun CurrentAccountCard(explained: CurrentAccountExplained?, onSetBalance: () -> Unit) {
    GlassCard(contentPadding = 16.dp) {
        if (explained == null) {
            Text(
                "Indique le solde de ton compte courant (ou connecte ta banque) pour voir comment il se répartit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RowActionButton("Indiquer le solde", onClick = onSetBalance, modifier = Modifier.padding(top = 8.dp))
            return@GlassCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(explained.balance.formatted(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "au ${explained.asOf.formatted()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.padding(top = 8.dp)) {
            AccountLine("Reste du mois", explained.monthLeft)
            if (explained.chargesDue.isPositive) AccountLine("Charges à payer ce mois", explained.chargesDue)
            if (explained.savingsPending.isPositive) AccountLine("Épargne du mois à virer", explained.savingsPending)
            if (explained.provisionsHeld.isPositive) AccountLine("Provisions mises de côté", explained.provisionsHeld)
            fr.baptiste.bank.ui.components.RowDivider(0.dp)
            val free = explained.free
            AccountLine(if (free.isNegative) "Il manque" else "Réserve, libre sur le compte", free, strong = true, positiveGood = true)
        }
        val note = when {
            explained.free.isNegative -> buildString {
                append("Le compte contient moins que ce que le budget réserve. Causes possibles : salaire pas encore arrivé, ")
                append("provisions gardées sur un livret")
                if (explained.unsortedOut.isPositive) append(", ${explained.unsortedOut.formatted()} de dépenses à trier")
                append(", ou dépenses faites hors de l'app.")
            }
            explained.free.isPositive ->
                "Hors budget du mois. Pour la mettre de côté sans toucher au mois : ouvre une enveloppe d'épargne " +
                    "ou une provision, Ajouter, puis « Réserve »."
            else -> null
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AccountLine(label: String, amount: Money, strong: Boolean = false, positiveGood: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            amount.formatted(),
            style = MaterialTheme.typography.titleSmall,
            color = when {
                amount.isNegative -> BankTheme.status.warning
                positiveGood && amount.isPositive -> BankTheme.status.gain
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
