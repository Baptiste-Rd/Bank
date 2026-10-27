package fr.baptiste.bank.ui.envelopes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label

/**
 * Une enveloppe : restant sur la période en cours, barre de consommation,
 * et projection de fin de période quand le rythme actuel mène à un dépassement.
 */
@Composable
fun EnvelopeRow(line: EnvelopeLine, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val overspent = line.remaining.isNegative
    val warning = overspent || line.projection.isAlert
    val accent = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val ratio = if (line.available.isPositive) {
        (line.spent.cents.toFloat() / line.available.cents).coerceIn(0f, 1f)
    } else if (line.spent.isPositive) {
        1f
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(line.envelope.name, style = MaterialTheme.typography.titleSmall)
            Text(line.remaining.formatted(), style = MaterialTheme.typography.titleSmall, color = accent)
        }
        LinearProgressIndicator(progress = { ratio }, color = accent, modifier = Modifier.fillMaxWidth())
        Text(
            text = "${line.envelope.type.label} · ${line.spent.formatted()} dépensés sur " +
                "${line.available.formatted()} (${line.period.label})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (line.projection.isAlert && !overspent) {
            Text(
                text = "Au rythme actuel : ${line.projection.projected.formatted()}, " +
                    "soit ${line.projection.overrun.formatted()} de dépassement",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
