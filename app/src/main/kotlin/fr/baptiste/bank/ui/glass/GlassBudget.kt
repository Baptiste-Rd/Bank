package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.InfoButton
import fr.baptiste.bank.ui.components.InfoLine
import fr.baptiste.bank.ui.components.InfoSheet
import fr.baptiste.bank.ui.components.RowActionButton
import fr.baptiste.bank.ui.envelopes.EnvelopeFamily
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.envelopes.TemplateChips
import fr.baptiste.bank.ui.envelopes.envelopeTemplates
import fr.baptiste.bank.ui.envelopes.family
import fr.baptiste.bank.ui.envelopes.monthlyShare
import fr.baptiste.bank.ui.envelopes.template
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.envelopeTypeColor

/*
 * Onglet Budget : « où va mon salaire ? ».
 * 1. Le plan du mois : une barre, le salaire découpé en charges fixes, provisions, épargne et
 *    budget du quotidien (dont la part non répartie). C'est ce découpage qui donne le reste du
 *    jour et l'objectif d'épargne ; il explique aussi pourquoi un achat prévu fait baisser l'épargne.
 * 2. Une carte par famille, les enveloppes en lignes séparées par un filet.
 */

/** Une part du plan du mois. */
private data class PlanPart(val family: EnvelopeFamily, val amount: Money)

@Composable
fun GlassBudgetContent(
    dashboard: Dashboard?,
    monthlyIncome: Money?,
    archived: List<Envelope>,
    onEditIncome: () -> Unit,
    onOpen: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
    onEdit: (Envelope) -> Unit,
    onRestore: (Envelope) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    val lines = dashboard?.envelopes.orEmpty()
    val byFamily = lines.groupBy { it.envelope.type.family }
    fun share(family: EnvelopeFamily) = byFamily[family].orEmpty().fold(Money.ZERO) { sum, line -> sum + line.monthlyShare }
    val parts = dashboard?.let {
        listOf(
            PlanPart(EnvelopeFamily.FIXED, share(EnvelopeFamily.FIXED)),
            PlanPart(EnvelopeFamily.PROVISIONS, share(EnvelopeFamily.PROVISIONS)),
            PlanPart(EnvelopeFamily.SAVINGS, share(EnvelopeFamily.SAVINGS)),
            PlanPart(EnvelopeFamily.DAILY, it.dailyBudget),
        )
    }.orEmpty()
    val usedNames = lines.map { it.envelope.name.lowercase() }.toSet()
    val suggestions = envelopeTemplates.filter { (name, _) -> name.lowercase() !in usedNames }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Budget", dashboard?.today?.monthTitle())

        // 1. Le plan du mois.
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
                    InfoButton(onClick = { showInfo = true }, contentDescription = "Comment le salaire est réparti")
                }
                if (monthlyIncome == null) {
                    RowActionButton("Indiquer mon salaire", onClick = onEditIncome, modifier = Modifier.padding(top = 8.dp))
                } else {
                    PlanBar(parts, Modifier.padding(vertical = 12.dp))
                    parts.forEach { part ->
                        val unallocated = dashboard?.unallocated
                        LegendRow(
                            color = part.family.color,
                            label = part.family.label,
                            value = part.amount.formatted(),
                            detail = if (part.family == EnvelopeFamily.DAILY && unallocated != null && unallocated.isPositive) {
                                "dont ${unallocated.formatted()} non réparti"
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        // 2. Une carte par famille.
        EnvelopeFamily.entries.forEach { family ->
            val familyLines = byFamily[family].orEmpty()
            if (familyLines.isEmpty()) return@forEach
            sectionHeader("label-$family", family.label, trailing = "${share(family).formatted()} / mois", dot = family.color)
            item(key = "group-$family") {
                GlassRowsCard(familyLines) { line ->
                    EnvelopeRow(
                        line,
                        onClick = { onOpen(line) },
                        onCover = { onCover(line) },
                        accent = envelopeTypeColor(line.envelope.type),
                    )
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            item(key = "templates") {
                GlassCard(contentPadding = 16.dp) {
                    TemplateChips(suggestions, onPick = { (name, type) -> onEdit(template(name, type)) })
                }
            }
        }
        if (archived.isNotEmpty()) {
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
