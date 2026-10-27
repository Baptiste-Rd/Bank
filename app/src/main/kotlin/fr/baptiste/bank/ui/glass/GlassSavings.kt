package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.MonthSavings
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.dailyCapForTarget
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.InfoButton
import fr.baptiste.bank.ui.components.InfoLine
import fr.baptiste.bank.ui.components.InfoSheet
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.home.SavingsBarsChart
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.ui.theme.envelopeTypeColor
import java.time.format.TextStyle
import java.util.Locale

/*
 * Onglet Épargne : « combien vais-je mettre de côté ? ».
 * 1. L'épargne prévue en fin de mois, un conseil d'une ligne, et l'action « Mettre de côté ».
 * 2. Deux écarts côte à côte : à l'objectif, au mois précédent.
 * 3. Les six derniers mois.
 * 4. Ce qui est mis de côté : enveloppes d'épargne, puis achats prévus.
 * Le calcul est dans la feuille ⓘ, pas dans la carte.
 */

private fun java.time.YearMonth.name(): String = month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)

@Composable
fun GlassSavingsContent(
    dashboard: Dashboard?,
    history: List<MonthSavings>,
    onPutAside: (EnvelopeLine) -> Unit,
    onPlanPurchase: () -> Unit,
    onOpenEnvelope: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    val accent = envelopeTypeColor(EnvelopeType.SAVINGS)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Épargne", dashboard?.today?.monthTitle())
        if (dashboard == null) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        val outlook = dashboard.savings
        val month = java.time.YearMonth.from(dashboard.today)
        val previous = history.dropLast(1).lastOrNull()
        val savingsLines = dashboard.envelopes.filter { it.envelope.type == EnvelopeType.SAVINGS }
        val planned = dashboard.envelopes.filter { it.envelope.type == EnvelopeType.PLANNED }

        // 1. Le chiffre, le conseil, l'action.
        item(key = "forecast") {
            GlassCard(strong = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (outlook.isFinal) "Épargné en ${month.name()}" else "Épargne prévue fin ${month.name()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    InfoButton(onClick = { showInfo = true }, contentDescription = "Comment c'est calculé")
                }
                Text(
                    outlook.forecast.formatted(),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (outlook.forecast.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                )
                adviceFor(dashboard)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                savingsLines.firstOrNull()?.let { target ->
                    GlassPillButton("Mettre de côté", onClick = { onPutAside(target) }, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        // 2. Les deux écarts.
        item(key = "gaps") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GapTile(
                    label = "Objectif",
                    delta = outlook.gapToTarget,
                    reference = "sur ${outlook.target.formatted()}",
                    modifier = Modifier.weight(1f),
                )
                if (previous != null) {
                    GapTile(
                        label = "vs ${previous.month.name()}",
                        delta = outlook.forecast - previous.outlook.forecast,
                        reference = "${previous.outlook.forecast.formatted()} épargnés",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 3. Six mois.
        if (history.size > 1) {
            sectionHeader("history-label", "Six derniers mois", trailing = "objectif ${outlook.target.formatted()}")
            item(key = "history") {
                GlassCard(contentPadding = 16.dp) {
                    SavingsBarsChart(history, outlook.target, accent, legend = null)
                }
            }
        }

        // 4. Ce qui est mis de côté.
        if (savingsLines.isNotEmpty()) {
            sectionHeader("put-aside-label", "Mis de côté", dot = accent)
            item(key = "put-aside") {
                GlassRowsCard(savingsLines) { line ->
                    EnvelopeRow(line, onClick = { onOpenEnvelope(line) }, onCover = { onCover(line) }, accent = accent)
                }
            }
        }
        val plannedColor = envelopeTypeColor(EnvelopeType.PLANNED)
        sectionHeader("planned-label", "Achats prévus", dot = plannedColor, action = "Prévoir", onAction = onPlanPurchase)
        item(key = "planned") {
            if (planned.isEmpty()) {
                GlassCard(contentPadding = 16.dp) {
                    Text(
                        "Un achat à une date : son prix est mis de côté chaque mois d'ici là.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onPlanPurchase) { Text("Prévoir un achat") }
                }
            } else {
                GlassRowsCard(planned) { line ->
                    EnvelopeRow(line, onClick = { onOpenEnvelope(line) }, onCover = { onCover(line) }, accent = plannedColor)
                }
            }
        }
    }

    if (showInfo && dashboard != null) {
        val outlook = dashboard.savings
        InfoSheet(
            title = "Comment est calculée l'épargne prévue",
            lines = listOfNotNull(
                InfoLine("Objectif (enveloppes d'épargne)", outlook.target.formatted()),
                InfoLine("Transferts vers l'épargne ce mois", outlook.transferred.signed()),
                InfoLine(
                    if (outlook.isFinal) "Reste du budget du quotidien" else "Reste prévu du quotidien",
                    outlook.dailyMargin.signed(),
                ),
                if (outlook.overruns.isPositive) InfoLine("Dépassements", "−${outlook.overruns.formatted()}") else null,
                InfoLine("Épargne prévue", outlook.forecast.formatted()),
            ),
            note = "Le reste du quotidien suit ton rythme de dépense actuel. Déplacer de l'argent vers l'épargne " +
                "le met à l'abri ; le total ne monte que si tu dépenses moins.",
            onDismiss = { showInfo = false },
        )
    }
}

/** Un conseil d'une ligne, tiré du plafond par jour. */
private fun adviceFor(dashboard: Dashboard): String? {
    val cap = dashboard.dailyCapForTarget() ?: return null
    return when {
        !cap.isPositive -> "Objectif hors d'atteinte ce mois-ci."
        dashboard.savings.gapToTarget.isNegative -> "Au plus ${cap.formatted()} par jour pour atteindre l'objectif."
        else -> "Au plus ${cap.formatted()} par jour pour rester au-dessus de l'objectif."
    }
}

/** Tuile d'écart : le libellé, l'écart signé (ambre s'il est négatif), la référence. */
@Composable
private fun GapTile(label: String, delta: Money, reference: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, contentPadding = 16.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            delta.signed(),
            style = MaterialTheme.typography.titleLarge,
            color = if (delta.isNegative) BankTheme.status.warning else BankTheme.status.good,
        )
        Text(reference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
