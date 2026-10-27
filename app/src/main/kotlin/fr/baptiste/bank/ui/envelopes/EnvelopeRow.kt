package fr.baptiste.bank.ui.envelopes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.period.Scale
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.BankTheme

/**
 * Une enveloppe : ce qu'il reste à droite, en gros ; à gauche le nom et la période.
 * La barre montre la part consommée, dans la couleur de son statut :
 * vert dans le budget, ambre si le rythme actuel mène au dépassement, brique si c'est dépassé.
 */
@Composable
fun EnvelopeRow(line: EnvelopeLine, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val status = BankTheme.status
    val overspent = line.remaining.isNegative
    val color = when {
        overspent -> status.over
        line.projection.isAlert -> status.warning
        else -> status.good
    }
    val ratio = when {
        line.available.isPositive -> (line.spent.cents.toFloat() / line.available.cents).coerceIn(0f, 1f)
        line.spent.isPositive -> 1f
        else -> 0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.envelope.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${line.spent.formatted()} sur ${line.available.formatted()}, ${line.period.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    line.remaining.formatted(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (overspent) status.over else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (overspent) "dépassé" else "restant",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { ratio },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        )
        if (line.projection.isAlert && !overspent) {
            Text(
                "À ce rythme, ${line.projection.overrun.formatted()} de trop en fin de ${periodNoun(line)}.",
                style = MaterialTheme.typography.bodySmall,
                color = status.warning,
            )
        }
    }
}

private fun periodNoun(line: EnvelopeLine): String = when (line.envelope.scale) {
    Scale.WEEK -> "semaine"
    Scale.MONTH -> "mois"
    Scale.YEAR -> "année"
}
