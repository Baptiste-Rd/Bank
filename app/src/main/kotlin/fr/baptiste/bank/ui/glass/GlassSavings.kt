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
import fr.baptiste.bank.domain.budget.HoldingLine
import fr.baptiste.bank.domain.budget.Holdings
import fr.baptiste.bank.ui.accounts.detail
import fr.baptiste.bank.ui.accounts.isStale
import fr.baptiste.bank.domain.budget.GoalsProgress
import fr.baptiste.bank.domain.budget.SavingsGoals
import fr.baptiste.bank.domain.budget.progress
import fr.baptiste.bank.domain.model.AccountType
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.dailyCapForTarget
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.InfoButton
import fr.baptiste.bank.ui.components.InfoLine
import fr.baptiste.bank.ui.components.InfoSheet
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
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
    selectedMonth: java.time.YearMonth = java.time.YearMonth.now(),
    canNextMonth: Boolean = false,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    /** Livrets et investissements ; null tant que non chargé. */
    holdings: Holdings? = null,
    onManageAccounts: () -> Unit = {},
    onOpenAccount: (HoldingLine) -> Unit = {},
    goals: SavingsGoals = SavingsGoals(Money.euros(10_000), Money.euros(20_000)),
    onEditGoals: () -> Unit = {},
    trackingStart: java.time.YearMonth? = null,
    onTrackMonth: (java.time.YearMonth) -> Unit = {},
) {
    var showInfo by remember { mutableStateOf(false) }
    // Un mois passé se consulte : ses chiffres sont définitifs, on n'y range plus rien.
    val readOnly = selectedMonth != java.time.YearMonth.now()
    val accent = envelopeTypeColor(EnvelopeType.SAVINGS)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Épargne")
        item(key = "month-switch") {
            PeriodSwitcher(
                label = selectedMonth.label.replaceFirstChar { it.titlecase() },
                canPrevious = true,
                canNext = canNextMonth,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                note = if (readOnly) "Mois terminé" else null,
            )
        }
        if (trackingStart != null && selectedMonth < trackingStart) {
            item(key = "not-tracked") { NotTrackedCard(selectedMonth, trackingStart) { onTrackMonth(selectedMonth) } }
            return@LazyColumn
        }
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
                if (!readOnly) savingsLines.firstOrNull()?.let { target ->
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

        // 4. Sur les autres comptes : ce qui est déjà de côté (le mois en cours seulement).
        if (!readOnly && holdings != null) holdingsSection(holdings, goals, dashboard.savings.forecast, onManageAccounts, onOpenAccount, onEditGoals)

        // 5. Ce qui est mis de côté.
        if (savingsLines.isNotEmpty()) {
            sectionHeader("put-aside-label", "Mis de côté", dot = accent)
            item(key = "put-aside") {
                GlassRowsCard(savingsLines) { line ->
                    EnvelopeRow(
                        line,
                        onClick = if (readOnly) null else ({ onOpenEnvelope(line) }),
                        onCover = if (readOnly) null else ({ onCover(line) }),
                        accent = accent,
                    )
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
                    EnvelopeRow(
                        line,
                        onClick = if (readOnly) null else ({ onOpenEnvelope(line) }),
                        onCover = if (readOnly) null else ({ onCover(line) }),
                        accent = plannedColor,
                    )
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
                if (outlook.surpluses.isPositive) InfoLine("Restes des enveloppes closes", outlook.surpluses.signed()) else null,
                if (outlook.overruns.isPositive) InfoLine("Dépassements", "−${outlook.overruns.formatted()}") else null,
                InfoLine("Épargne prévue", outlook.forecast.formatted()),
            ),
            note = "Le reste du quotidien suit ton rythme de dépense actuel. Ce qui reste dans une charge ou une " +
                "provision à la fin de sa période va à l'épargne ; un dépassement y est pris. Déplacer de l'argent " +
                "vers l'épargne le met à l'abri ; le total ne monte que si tu dépenses moins.",
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
            color = if (delta.isNegative) BankTheme.status.warning else BankTheme.status.gain,
        )
        Text(reference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * « Sur tes comptes » : le total, puis l'avancement vers les objectifs (la précaution sur les
 * livrets, le total visé sur tous les comptes), puis les comptes groupés par disponibilité :
 * disponibles tout de suite (livrets), placés (valeur qui fluctue).
 */
private fun LazyListScope.holdingsSection(
    holdings: Holdings,
    goals: SavingsGoals,
    monthlySavings: Money,
    onManage: () -> Unit,
    onOpen: (HoldingLine) -> Unit,
    onEditGoals: () -> Unit,
) {
    sectionHeader("holdings-label", "Sur tes comptes", action = if (holdings.lines.isEmpty()) null else "Gérer", onAction = onManage)
    item(key = "holdings") {
        if (holdings.lines.isEmpty()) {
            GlassCard(contentPadding = 16.dp) {
                Text(
                    "Ajoute tes livrets et tes placements pour voir tout ce que tu as de côté.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onManage) { Text("Ajouter un compte") }
            }
            return@item
        }
        val progress = holdings.progress(goals)
        GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(holdings.total.formatted(), style = MaterialTheme.typography.titleLarge)
                val previous = holdings.history.dropLast(1).lastOrNull()
                val parts = listOfNotNull(
                    previous?.let { "${(holdings.total - it.total).signed()} depuis fin ${it.month.name()}" },
                    holdings.transferredThisMonth.takeIf { it.isPositive }?.let { "${it.formatted()} versés ce mois" },
                )
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (holdings.estimatedTax.isPositive) {
                    Text(
                        "≈ ${holdings.netTotal.formatted()} après l'impôt estimé sur les plus-values (${holdings.estimatedTax.formatted()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GoalsBar(progress, monthlySavings, Modifier.padding(top = 12.dp).clickable(onClick = onEditGoals))
            }
            HoldingGroup("Disponible tout de suite", progress.available, holdings.lines.filter { it.account.type == AccountType.SAVINGS }, onOpen)
            HoldingGroup("Placé, valeur qui fluctue", progress.invested, holdings.lines.filter { it.account.type != AccountType.SAVINGS }, onOpen)
        }
    }
    // Évolution du total sur douze mois : seuls les mois connus (après le premier relevé) comptent.
    if (holdings.history.size > 1) {
        sectionHeader("holdings-history-label", "Tes comptes mois par mois", trailing = "fin de mois")
        item(key = "holdings-history") {
            GlassCard(contentPadding = 16.dp) {
                HoldingsLineChart(holdings.history)
            }
        }
    }
}

/**
 * Le total de côté à la fin de chaque mois connu : une ligne, un point par mois, la première et la
 * dernière valeur écrites. L'échelle part du plus bas, pour lire la pente plutôt que le niveau.
 */
@Composable
private fun HoldingsLineChart(history: List<fr.baptiste.bank.domain.budget.HoldingsMonth>) {
    val accent = envelopeTypeColor(EnvelopeType.SAVINGS)
    val guide = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val values = history.map { it.total.cents }
    val low = values.min()
    val high = values.max()
    val span = (high - low).coerceAtLeast(1L)
    Column {
        Row {
            Text(
                "${history.first().total.formatted()} fin ${history.first().month.name()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(history.last().total.formatted(), style = MaterialTheme.typography.labelMedium)
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 8.dp)) {
            val step = if (values.size > 1) size.width / (values.size - 1) else 0f
            fun point(index: Int) = androidx.compose.ui.geometry.Offset(
                step * index,
                size.height - size.height * (values[index] - low) / span,
            )
            drawLine(guide, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            for (index in 1 until values.size) {
                drawLine(accent, point(index - 1), point(index), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            values.indices.forEach { index -> drawCircle(accent, radius = 4.dp.toPx(), center = point(index)) }
        }
        Row(Modifier.fillMaxWidth()) {
            history.forEach { month ->
                Text(
                    month.month.month.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.FRANCE),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Deux paliers sur une barre : la précaution (livrets) et le total visé. La barre suit le total ;
 * un repère marque la précaution. Toucher la barre règle les objectifs.
 */
@Composable
private fun GoalsBar(progress: GoalsProgress, monthlySavings: Money, modifier: Modifier = Modifier) {
    val status = BankTheme.status
    val accent = envelopeTypeColor(EnvelopeType.SAVINGS)
    val target = progress.goals.total
    val ratio = if (target.isPositive) (progress.total.cents.toFloat() / target.cents).coerceIn(0f, 1f) else 1f
    val mark = if (target.isPositive) (progress.goals.precaution.cents.toFloat() / target.cents).coerceIn(0f, 1f) else 0f
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRoundRect(accent, size = size.copy(width = size.width * ratio), cornerRadius = r)
            val x = size.width * mark
            drawLine(ink, androidx.compose.ui.geometry.Offset(x, -3f), androidx.compose.ui.geometry.Offset(x, size.height + 3f), strokeWidth = 3f)
        }
        Text(
            if (progress.precautionReached) {
                "Précaution atteinte : ${progress.available.formatted()} sur les livrets (objectif ${progress.goals.precaution.formatted()})"
            } else {
                "Précaution : il manque ${progress.precautionMissing.formatted()} sur les livrets (objectif ${progress.goals.precaution.formatted()})"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (progress.precautionReached) status.good else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            when {
                progress.beyond.isPositive -> "Total visé atteint, ${progress.beyond.formatted()} au-delà pour tes projets"
                progress.totalReached -> "Total visé atteint (${progress.goals.total.formatted()})"
                else -> "Total : ${progress.total.formatted()} sur ${progress.goals.total.formatted()}, il manque ${progress.totalMissing.formatted()}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Avec une échéance : ce qu'elle demande par mois, et si l'épargne prévue du mois suit.
        progress.pace(java.time.LocalDate.now(), monthlySavings)?.let { pace ->
            val by = progress.goals.totalBy?.label.orEmpty()
            Text(
                "Pour fin $by : ${pace.monthlyNeeded.formatted()} par mois sur ${pace.monthsLeft} mois",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                when {
                    pace.onTrack -> "Ton épargne prévue ce mois (${pace.monthlyPace.formatted()}) suit ce rythme."
                    pace.reachedAt != null ->
                        "À ton rythme actuel (${pace.monthlyPace.formatted()} par mois), atteint fin ${pace.reachedAt?.label}."
                    else -> "Ce mois-ci, rien n'est mis de côté : à ce rythme, l'objectif n'avance pas."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (pace.onTrack) status.good else status.warning,
            )
        }
    }
}

@Composable
private fun HoldingGroup(title: String, subtotal: Money, lines: List<HoldingLine>, onOpen: (HoldingLine) -> Unit) {
    if (lines.isEmpty()) return
    fr.baptiste.bank.ui.components.RowDivider(0.dp)
    Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(subtotal.formatted(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    lines.forEach { line ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(line) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.account.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    line.detail(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.isStale()) BankTheme.status.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(line.balance?.formatted() ?: "–", style = MaterialTheme.typography.titleMedium)
                // Placement renseigné : ce qui resterait après impôt si tout était retiré aujourd'hui.
                line.estimate?.let { estimate ->
                    Text(
                        "≈ ${estimate.net.formatted()} net",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
