package fr.baptiste.bank.ui.envelopes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.monthlyShare
import fr.baptiste.bank.domain.budget.purchaseMonth
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.format.endPhrase
import fr.baptiste.bank.ui.theme.BankTheme

/**
 * Une enveloppe : ce qu'il reste à droite, en gros ; à gauche le nom et la période.
 * La barre montre la part consommée, dans la couleur de son statut : [accent] (ou le bleu du
 * thème) dans le budget, couleur d'alerte si le rythme actuel mène au dépassement, rouge si c'est
 * dépassé. Avec un [accent], la ligne commence par une pastille à l'initiale de l'enveloppe.
 */
@Composable
fun EnvelopeRow(
    line: EnvelopeLine,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onCover: (() -> Unit)? = null,
    accent: Color? = null,
) {
    val status = BankTheme.status
    val overspent = line.remaining.isNegative
    val color = when {
        overspent -> status.over
        line.projection.isAlert -> status.warning
        else -> accent ?: status.good
    }
    val ratio = when {
        line.available.isPositive -> (line.spent.cents.toFloat() / line.available.cents).coerceIn(0f, 1f)
        line.spent.isPositive -> 1f
        else -> 0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (accent != null) CategoryAvatar(line.envelope.name, accent)
        EnvelopeDetails(line, ratio, color, overspent, onCover, Modifier.weight(1f))
    }
}

@Composable
private fun EnvelopeDetails(
    line: EnvelopeLine,
    ratio: Float,
    color: Color,
    overspent: Boolean,
    onCover: (() -> Unit)?,
    modifier: Modifier,
) {
    val status = BankTheme.status
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.envelope.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    line.envelope.purchaseMonth?.let { month ->
                        "${line.envelope.monthlyShare.formatted()} par mois, achat en ${month.label}"
                    } ?: "${line.spent.formatted()} sur ${line.available.formatted()}, ${line.period.label}",
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
        if (line.needsCover) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (overspent) {
                        "Dépassé de ${(-line.remaining).formatted()}."
                    } else {
                        "À ce rythme, ${line.projection.overrun.formatted()} de trop ${line.period.endPhrase}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overspent) status.over else status.warning,
                    modifier = Modifier.weight(1f),
                )
                if (onCover != null) {
                    TextButton(onClick = onCover) { Text("Couvrir") }
                }
            }
        }
    }
}

/**
 * Pastille ronde à l'initiale d'un nom, dans la couleur de sa catégorie : un fond de la couleur
 * très atténué et la lettre en couleur pleine. Elle aide à repérer une catégorie d'un coup d'œil ;
 * le nom écrit à côté reste l'information principale.
 */
@Composable
fun CategoryAvatar(name: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = color,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}
