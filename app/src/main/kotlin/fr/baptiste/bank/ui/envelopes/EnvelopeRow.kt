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
import androidx.compose.ui.draw.alpha
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
import fr.baptiste.bank.ui.format.summary
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.BankTheme

/**
 * Une enveloppe, comme dans le prototype testé (lot « encarts ») :
 * - le point de couleur (avec un [accent]), le nom, et à droite ce qu'il reste ;
 * - la barre de ce qui est consommé ;
 * - une seule ligne dessous : une puce si elle dérape (« Dépassé 0,50 € », « Prévu +32 € »),
 *   sinon « 55 € sur 320 € » ; à droite, ce que le chiffre veut dire (« reste »).
 * Aucun bouton dans la ligne : on agit depuis la fiche de l'enveloppe ([onCover] n'est plus montré).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun EnvelopeRow(
    line: EnvelopeLine,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onCover: (() -> Unit)? = null,
    accent: Color? = null,
    /** Faux dans un arbre : le point est posé par la ligne, à sa place dans la hiérarchie. */
    showDot: Boolean = true,
) {
    val status = BankTheme.status
    val overspent = line.remaining.isNegative
    val color = when {
        overspent -> status.over
        line.projection.isAlert -> status.warning
        else -> accent ?: status.good
    }
    val today = LocalDate.now()
    val display = when (line.envelope.type) {
        EnvelopeType.SAVINGS -> RowDisplay(
            detail = "${line.envelope.amount.summary()} ${line.envelope.frequency.label}",
            value = line.available,
            caption = "ce mois",
            ratio = null,
        )
        // Une seule longue période : pas de dates de période, ce qui reste sur la somme de départ.
        EnvelopeType.LUMP -> RowDisplay(
            detail = "${line.spent.summary()} sur ${line.available.summary()}",
            value = line.remaining,
            caption = "reste",
            ratio = when {
                line.available.isPositive -> (line.spent.cents.toFloat() / line.available.cents).coerceIn(0f, 1f)
                line.spent.isPositive -> 1f
                else -> 0f
            },
        )
        EnvelopeType.PLANNED -> {
            val saved = line.envelope.savedSoFar(today)
            RowDisplay(
                detail = "${line.monthlyShare.summary()} par mois · ${line.envelope.purchaseMonth?.label.orEmpty()}",
                value = saved,
                caption = "sur ${line.envelope.amount.summary()}",
                ratio = if (line.envelope.amount.isPositive) saved.cents.toFloat() / line.envelope.amount.cents else 0f,
            )
        }
        else -> RowDisplay(
            // « 55 € sur 320 € », la période seulement si ce n'est pas le mois affiché.
            detail = "${line.spent.summary()} sur ${line.available.summary()}" +
                if (line.period is fr.baptiste.bank.domain.period.Period.Month) "" else " · ${line.period.shortLabel}",
            value = line.remaining,
            caption = "reste",
            ratio = when {
                line.available.isPositive -> (line.spent.cents.toFloat() / line.available.cents).coerceIn(0f, 1f)
                line.spent.isPositive -> 1f
                else -> 0f
            },
        )
    }

    // Retirée depuis : elle reste dans ce mois passé, en retrait.
    val retiredOn = line.envelope.retiredOn?.takeIf { line.envelope.archived }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null && retiredOn == null) it.clickable(onClick = onClick) else it }
            .let { if (retiredOn != null) it.alpha(0.55f) else it }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (accent != null && showDot) CategoryAvatar(line.envelope.name, accent)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.envelope.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    display.value.summary(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (display.value.isNegative) status.over else MaterialTheme.colorScheme.onSurface,
                )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    retiredOn != null -> Text(
                        "Arrêtée le ${retiredOn.formatted()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    overspent -> Box(Modifier.weight(1f)) { StatusChip("Dépassé ${(-line.remaining).summary()}", StatusKind.OVER) }
                    line.projection.isAlert -> Box(Modifier.weight(1f)) { StatusChip("Prévu +${line.projection.overrun.summary()}", StatusKind.WARNING) }
                    else -> Text(
                        display.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Un dépassement se lit dans la puce : pas de « reste » sous un montant négatif.
                if (!overspent) Text(display.caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Point de couleur de la catégorie, devant le nom. Une initiale n'apportait aucune information
 * (audit de la refonte) ; la couleur suffit à repérer une catégorie, le nom reste l'essentiel.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun CategoryAvatar(name: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
    }
}

/** Ce qu'une ligne d'enveloppe affiche : détail, valeur à droite et sa légende, avancement (null : pas de barre). */
private data class RowDisplay(val detail: String, val value: Money, val caption: String, val ratio: Float?, val extra: String? = null)
