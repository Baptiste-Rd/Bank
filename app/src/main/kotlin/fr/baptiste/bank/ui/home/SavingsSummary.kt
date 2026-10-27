package fr.baptiste.bank.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.MonthSavings
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.dailyCapForTarget
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.theme.BankTheme
import java.time.format.TextStyle
import java.util.Locale

private fun Money.signed(): String = if (isNegative) "−${(-this).formatted()}" else "+${formatted()}"

private fun java.time.YearMonth.monthName(): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)

/**
 * Bloc Épargne de l'accueil : la prévision de fin de mois, comparée à l'objectif et au mois
 * précédent, l'historique des six derniers mois, le détail du calcul et le conseil qui en découle.
 *
 * Deux codes couleur seulement : [accent] pour l'épargne elle-même, le statut (bleu en avance,
 * ambre en retard) pour les écarts. Chaque écart est aussi écrit avec son signe : la couleur
 * n'est jamais seule à porter l'information.
 */
@Composable
fun SavingsSummary(dashboard: Dashboard, history: List<MonthSavings>, accent: Color, modifier: Modifier = Modifier) {
    val outlook = dashboard.savings
    val status = BankTheme.status
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    val currentMonth = java.time.YearMonth.from(dashboard.today)
    val previous = history.dropLast(1).lastOrNull()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(
                if (outlook.isFinal) "Épargné en ${currentMonth.monthName()}" else "Épargne prévue fin ${currentMonth.monthName()}",
                style = MaterialTheme.typography.labelMedium,
                color = variant,
            )
            Text(
                outlook.forecast.formatted(),
                style = MaterialTheme.typography.headlineMedium,
                color = if (outlook.forecast.isNegative) status.over else MaterialTheme.colorScheme.onSurface,
            )
        }

        // Les deux comparaisons demandées : à l'objectif du mois, et au mois précédent.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Comparison(
                label = "Objectif",
                reference = outlook.target,
                delta = outlook.gapToTarget,
                modifier = Modifier.weight(1f),
            )
            if (previous != null) {
                Comparison(
                    label = previous.month.monthName().replaceFirstChar { it.titlecase(Locale.FRANCE) },
                    reference = previous.outlook.forecast,
                    delta = outlook.forecast - previous.outlook.forecast,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (history.size > 1) SavingsBars(history, outlook.target, accent)

        // Le détail du calcul, pour comprendre d'où vient le chiffre.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Breakdown("À l'abri (objectif et transferts du mois)", outlook.secured.formatted())
            Breakdown(
                if (outlook.isFinal) "Reste du budget du quotidien" else "Reste prévu du quotidien, au rythme actuel",
                outlook.dailyMargin.signed(),
            )
            if (outlook.overruns.isPositive) Breakdown("Dépassements et retraits d'épargne", "−${outlook.overruns.formatted()}")
        }

        val cap = dashboard.dailyCapForTarget()
        val advice = when {
            cap == null -> null
            outlook.gapToTarget.isNegative && cap.isPositive ->
                "Pour atteindre l'objectif : au plus ${cap.formatted()} par jour pour le quotidien d'ici la fin du mois."
            outlook.gapToTarget.isNegative -> "L'objectif n'est plus atteignable ce mois-ci, même sans autre dépense."
            else -> "En avance de ${outlook.gapToTarget.formatted()} sur l'objectif. Plafond pour le rester : ${cap.formatted()} par jour."
        }
        if (advice != null) Text(advice, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Déplacer de l'argent vers l'épargne le met à l'abri tout de suite ; la prévision, elle, ne monte " +
                "que si tu dépenses moins que ton rythme actuel.",
            style = MaterialTheme.typography.bodySmall,
            color = variant,
        )
    }
}

@Composable
private fun Comparison(label: String, reference: Money, delta: Money, modifier: Modifier = Modifier) {
    val status = BankTheme.status
    Column(modifier) {
        Text("$label : ${reference.formatted()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            delta.signed(),
            style = MaterialTheme.typography.titleMedium,
            color = if (delta.isNegative) status.warning else status.good,
        )
    }
}

@Composable
private fun Breakdown(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Six barres, une par mois : l'épargne réelle des mois passés, la prévision du mois en cours
 * (plus pâle, puisqu'elle peut encore bouger), et l'objectif du mois en pointillés.
 * Une épargne négative descend sous la ligne de base.
 */
@Composable
private fun SavingsBars(history: List<MonthSavings>, target: Money, accent: Color) {
    val values = history.map { it.outlook.forecast.cents }
    val top = maxOf(values.maxOrNull() ?: 0, target.cents, 1)
    val bottom = minOf(values.minOrNull() ?: 0, 0)
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    val negative = BankTheme.status.over
    Column {
        Canvas(Modifier.fillMaxWidth().height(72.dp).padding(top = 4.dp)) {
            val span = (top - bottom).toFloat()
            fun y(cents: Long) = size.height * (top - cents) / span
            val slot = size.width / values.size
            val barWidth = slot * 0.56f
            values.forEachIndexed { index, cents ->
                val current = index == values.lastIndex
                val zero = y(0)
                val valueY = y(cents)
                val color = (if (cents < 0) negative else accent).copy(alpha = if (current) 0.45f else 0.9f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(slot * index + (slot - barWidth) / 2, minOf(zero, valueY)),
                    size = Size(barWidth, kotlin.math.abs(zero - valueY).coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
            val targetY = y(target.cents)
            drawLine(
                color = guide,
                start = Offset(0f, targetY),
                end = Offset(size.width, targetY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            history.forEach { month ->
                Text(
                    month.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.FRANCE).take(4),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "Pointillés : objectif mensuel. Barre pâle : prévision du mois en cours.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
