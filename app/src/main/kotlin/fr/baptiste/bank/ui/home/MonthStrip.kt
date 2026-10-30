package fr.baptiste.bank.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.DayCell
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.theme.BankTheme

/**
 * Le mois en un coup d'œil : une barre par jour, de hauteur proportionnelle à la dépense
 * quotidienne de ce jour. La ligne pointillée marque la part moyenne (budget du mois / jours).
 * Une barre au-dessus de la ligne est un jour plus cher que la moyenne ; aujourd'hui est
 * souligné, les jours à venir sont de simples repères.
 */
@Composable
fun MonthStrip(
    days: List<DayCell>,
    averageShare: Money,
    modifier: Modifier = Modifier,
) {
    val bar = fr.baptiste.bank.ui.theme.envelopeTypeColor(fr.baptiste.bank.domain.model.EnvelopeType.DAILY)
    val future = MaterialTheme.colorScheme.outlineVariant
    val guide = MaterialTheme.colorScheme.outline
    val today = MaterialTheme.colorScheme.onSurface
    val spentOver = days.count { !it.isFuture && it.spent > averageShare && averageShare.isPositive }

    // La plus haute barre occupe toute la hauteur, mais la ligne de moyenne reste au plus aux 2/3.
    val maxSpent = maxOf(days.maxOfOrNull { it.spent.cents } ?: 0L, averageShare.cents * 3 / 2, 1L)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = "Dépenses du mois jour par jour. $spentOver jour(s) au-dessus de la part moyenne."
                },
        ) {
            val slot = size.width / days.size
            val barWidth = slot * 0.62f
            val baseline = size.height - 6.dp.toPx()
            val usable = baseline
            days.forEachIndexed { index, day ->
                val x = index * slot + (slot - barWidth) / 2
                if (day.isFuture) {
                    drawRoundRect(
                        color = future,
                        topLeft = Offset(x, baseline - 3.dp.toPx()),
                        size = Size(barWidth, 3.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                } else {
                    val ratio = day.spent.cents.toFloat() / maxSpent
                    val height = maxOf(usable * ratio, 3.dp.toPx())
                    // Une seule couleur : la hauteur dit tout, le pointillé sert de repère. Les
                    // couleurs de statut restent réservées aux enveloppes (dans le budget, dépassé).
                    val color = if (averageShare.isPositive) bar else guide
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, baseline - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
                if (day.isToday) {
                    drawRect(
                        color = today,
                        topLeft = Offset(x, size.height - 2.dp.toPx()),
                        size = Size(barWidth, 2.dp.toPx()),
                    )
                }
            }
            if (averageShare.isPositive) {
                val y = baseline - usable * (averageShare.cents.toFloat() / maxSpent)
                drawLine(
                    color = guide,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                "1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (averageShare.isPositive) {
                Text(
                    "pointillés : ${averageShare.formatted()} par jour en moyenne",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                days.size.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}
