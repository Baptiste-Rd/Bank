package fr.baptiste.bank.ui.glass

import fr.baptiste.bank.domain.model.category
import fr.baptiste.bank.ui.theme.categoryColor
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
import androidx.compose.foundation.layout.Box
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
import fr.baptiste.bank.domain.budget.monthClosing
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.InfoButton
import fr.baptiste.bank.ui.components.InfoLine
import fr.baptiste.bank.ui.components.InfoSheet
import fr.baptiste.bank.ui.components.RowActionButton
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.envelopes.EnvelopeFamily
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.envelopes.family
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.summary
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
    onEditIncome: () -> Unit,
    onEdit: (Envelope) -> Unit,
    onRestore: (Envelope) -> Unit,
    /** Le bouton « Nouvelle enveloppe » en bas de l'onglet (aussi dans le + › Ajouter). */
    onNewEnvelope: () -> Unit = {},
    /** Début du suivi : un mois avant lui est « non renseigné ». */
    trackingStart: YearMonth? = null,
    /** Glisser une enveloppe : la ranger dans une autre catégorie. */
    onMoveEnvelope: (Envelope) -> Unit = {},
    /** L'arbre : les enveloppes actives, leurs montants saisis, et les groupes. */
    envelopes: List<Envelope> = emptyList(),
    groups: List<fr.baptiste.bank.domain.budget.GroupNode> = emptyList(),
    onRenameGroup: (String) -> Unit = {},
    onMoveGroup: (String) -> Unit = {},
    onDeleteGroup: (String) -> Unit = {},
    onDrop: (Long, fr.baptiste.bank.domain.model.TreeDrop) -> Unit = { _, _ -> },
) {
    val listState = rememberTabListState()
    var showInfo by remember { mutableStateOf(false) }
    val parts = dashboard?.let(::planParts).orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Budget")
        // Une seule vue : le mois. Les dépenses de l'année sont dans Dépenses, l'épargne de
        // l'année dans Épargne (chaque information n'a qu'une maison).
        run {
            run {
                if (dashboard != null) {
                    monthContent(
                        dashboard = dashboard,
                        parts = parts,
                        monthlyIncome = monthlyIncome,
                        archived = archived,
                        onShowInfo = { showInfo = true },
                        onEditIncome = onEditIncome,
                        onEdit = onEdit,
                        onRestore = onRestore,
                        onMoveEnvelope = onMoveEnvelope,
                        envelopes = envelopes,
                        groups = groups,
                        listState = listState,
                        onRenameGroup = onRenameGroup,
                        onMoveGroup = onMoveGroup,
                        onDeleteGroup = onDeleteGroup,
                        onDrop = onDrop,
                        trackingStart = trackingStart,
                    )
                    item(key = "new-envelope") {
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp), Alignment.Center) {
                            TextButton(onClick = onNewEnvelope) { Text("Nouvelle enveloppe") }
                        }
                    }
                }
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
    onShowInfo: () -> Unit,
    onEditIncome: () -> Unit,
    onEdit: (Envelope) -> Unit,
    onRestore: (Envelope) -> Unit,
    onMoveEnvelope: (Envelope) -> Unit = {},
    envelopes: List<Envelope>,
    groups: List<fr.baptiste.bank.domain.budget.GroupNode>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRenameGroup: (String) -> Unit,
    onMoveGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDrop: (Long, fr.baptiste.bank.domain.model.TreeDrop) -> Unit,
    trackingStart: YearMonth?,
) {
    item(key = "plan") {
        GlassCard(contentPadding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Plan du mois", style = MaterialTheme.typography.titleMedium)
                    Text(
                        monthlyIncome?.let { "Salaire ${it.summary()}" } ?: "Salaire non renseigné",
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
                        value = part.amount.summary(),
                        detail = if (part.family == EnvelopeFamily.DAILY && unallocated != null && unallocated.isPositive) {
                            "dont ${unallocated.summary()} à répartir"
                        } else {
                            null
                        },
                        // Du salaire que rien ne réclame : un bénéfice, en vert.
                        detailColor = BankTheme.status.gain,
                    )
                }
                // « Essayer un plan » vit dans Épargne : une seule maison par fonction.
            }
        }
    }

    sectionHeader("tree-label", "Enveloppes")
    item(key = "tree") {
        GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
            BudgetTree(
                envelopes = envelopes + archived,
                groups = groups,
                since = trackingStart,
                listState = listState,
                onEdit = onEdit,
                onMoveEnvelope = onMoveEnvelope,
                onRenameGroup = onRenameGroup,
                onMoveGroup = onMoveGroup,
                onDeleteGroup = onDeleteGroup,
                onDrop = onDrop,
            )
        }
    }

    if (archived.isNotEmpty()) {
        sectionHeader("archived-label", "Arrêtées", trailing = "${archived.size}")
        item(key = "archived") {
            GlassRowsCard(archived, inset = 0.dp) { envelope ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(envelope.name, style = MaterialTheme.typography.bodyLarge)
                        // Arrêtée ce mois-ci : ce qu'elle garde encore dans le mois, pour retrouver le total de sa famille.
                        val kept = dashboard.envelopes.firstOrNull { it.envelope.id == envelope.id }?.monthlyShare?.takeIf { it.isPositive }
                        Text(
                            kept?.let { "${it.summary()} gardés ce mois-ci" } ?: if (envelope.type == EnvelopeType.LUMP) "Somme mise à part, terminée" else "${envelope.amount.summary()} ${envelope.frequency.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Une somme terminée se rétablit par l'Historique, qui défait aussi ce qui a été fait de son reste.
                    if (envelope.type != EnvelopeType.LUMP) TextButton(onClick = { onRestore(envelope) }) { Text("Reprendre") }
                }
            }
        }
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
internal fun MonthReview(dashboard: Dashboard, onEdit: (Envelope) -> Unit, transfers: Money?) {
    val savings = dashboard.savings
    val closings = dashboard.closings()
    val status = BankTheme.status
    GlassCard(strong = true, contentPadding = 16.dp) {
        val reviewed = dashboard.today.monthTitle().substringBefore(' ').lowercase()
        // « Bilan d'octobre », « Bilan de mars » : l'élision devant une voyelle.
        Text(if (reviewed.first() in "aeiouâéèêîôû") "Bilan d'$reviewed" else "Bilan de $reviewed", style = MaterialTheme.typography.titleMedium)
        Text(
            savings.forecast.summary(),
            style = MaterialTheme.typography.titleLarge,
            color = if (savings.forecast.isNegative) status.over else MaterialTheme.colorScheme.onSurface,
        )
        // Un mois écarté des moyennes (premier mois incomplet, mois exceptionnel) le dit : son épargne ne fait pas référence.
        val excluded = java.time.YearMonth.from(dashboard.today) in fr.baptiste.bank.ui.theme.LocalExcludedMonths.current
        Text(if (excluded) "Épargne du mois · hors référence" else "Épargne du mois", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Ce qu'est devenue l'épargne du mois : virée vers un livret ou un placement, ou encore sur le compte courant.
        val closing = monthClosing(savings.forecast, transfers)
        val onAccount = closing.onAccount
        if (onAccount != null && savings.forecast.isPositive) {
            Column(Modifier.padding(top = 8.dp)) {
                // Un bloc qui n'a rien à dire n'apparaît pas (règle 6) : pas de « +0 € ».
                closing.transferred?.takeIf { it.cents != 0L }?.let { ReviewLine("Virée vers tes comptes", it) }
                when {
                    onAccount.isPositive -> ReviewLine("Encore sur le compte courant", onAccount)
                    onAccount.isNegative -> ReviewLine("Pris sur l'argent déjà sur le compte", onAccount)
                }
            }
        }
        Column(Modifier.padding(top = 8.dp)) {
            ReviewLine("Objectif et mises de côté", savings.secured)
            ReviewLine("Reste du quotidien", savings.dailyMargin)
            closings.forEach { line ->
                val label = if (line.remaining.isNegative) "${line.envelope.name} · dépassé" else "${line.envelope.name} · reste"
                ReviewLine(label, line.remaining)
            }
            val savingsSpent = savings.overruns - closings.filter { it.remaining.isNegative }
                .fold(Money.ZERO) { sum, line -> sum - line.remaining }
            if (savingsSpent.isPositive) ReviewLine("Dépenses sur l'épargne", Money(-savingsSpent.cents))
        }
        Text(
            "Ce qui reste va à l'épargne ; un dépassement y est pris.",
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
