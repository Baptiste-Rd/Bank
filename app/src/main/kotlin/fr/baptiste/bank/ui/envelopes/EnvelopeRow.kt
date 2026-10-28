package fr.baptiste.bank.ui.envelopes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.monthlyShare
import fr.baptiste.bank.domain.budget.purchaseMonth
import fr.baptiste.bank.domain.budget.savedSoFar
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.format.shortLabel
import java.time.LocalDate
import fr.baptiste.bank.ui.components.RowActionButton
import fr.baptiste.bank.ui.components.StatusChip
import fr.baptiste.bank.ui.components.StatusKind
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.BankTheme

/**
 * Une enveloppe, lue de gauche à droite comme une ligne de relevé :
 * - la pastille de sa catégorie (avec un [accent]) ;
 * - son nom, et une ligne de détail : « 306,60 € sur 320,00 € · octobre 2026 » ;
 * - à droite, ce qu'il reste, le chiffre qu'on cherche ;
 * - la barre de ce qui est consommé ;
 * - si elle dérape : une puce de statut (« Dépassé 0,50 € », « Prévu +32,03 € ») et le bouton
 *   Couvrir, au lieu d'une phrase.
 * La couleur de la barre et des puces suit le statut : [accent] (ou le bleu) dans le budget,
 * ambre si le rythme mène au dépassement, rouge si c'est dépassé.
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
    // Ce que la ligne montre dépend de la famille : ce qu'il reste pour une enveloppe de
    // dépense, ce qui est mis de côté pour l'épargne et pour un achat prévu.
    val today = LocalDate.now()
    val display = when (line.envelope.type) {
        EnvelopeType.SAVINGS -> RowDisplay(
            detail = "${line.envelope.amount.formatted()} ${line.envelope.frequency.label}",
            value = line.available,
            caption = "ce mois",
            ratio = null,
        )
        EnvelopeType.PLANNED -> {
            val saved = line.envelope.savedSoFar(today)
            RowDisplay(
                detail = "${line.envelope.monthlyShare.formatted()} par mois · achat en ${line.envelope.purchaseMonth?.label.orEmpty()}",
                value = saved,
                caption = "sur ${line.envelope.amount.formatted()}",
                ratio = if (line.envelope.amount.isPositive) saved.cents.toFloat() / line.envelope.amount.cents else 0f,
            )
        }
        else -> RowDisplay(
            // Un transfert (couverture, ajout) se lit à part : « 550 € sur 400 € +150 € ».
            detail = if (line.transferred.cents == 0L) {
                "${line.spent.formatted()} sur ${line.available.formatted()} · ${line.period.shortLabel}"
            } else {
                val sign = if (line.transferred.isNegative) "−" else "+"
                "${line.spent.formatted()} sur ${line.allocated.formatted()} $sign${Money(kotlin.math.abs(line.transferred.cents)).formatted()} · " +
                    line.period.shortLabel
            },
            value = line.remaining,
            caption = "reste",
            ratio = when {
                line.available.isPositive -> (line.spent.cents.toFloat() / line.available.cents).coerceIn(0f, 1f)
                line.spent.isPositive -> 1f
                else -> 0f
            },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (accent != null) CategoryAvatar(line.envelope.name, accent)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(line.envelope.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text(
                        display.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        display.value.formatted(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (display.value.isNegative) status.over else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(display.caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val ratio = display.ratio
            if (ratio != null) LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            if (line.needsCover) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (overspent) {
                        StatusChip("Dépassé ${(-line.remaining).formatted()}", StatusKind.OVER)
                    } else {
                        StatusChip("Prévu +${line.projection.overrun.formatted()}", StatusKind.WARNING)
                    }
                    Spacer(Modifier.weight(1f))
                    if (onCover != null) RowActionButton("Couvrir", onClick = onCover)
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

/** Ce qu'une ligne d'enveloppe affiche : détail, valeur à droite et sa légende, avancement (null : pas de barre). */
private data class RowDisplay(val detail: String, val value: Money, val caption: String, val ratio: Float?)
