package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.R
import fr.baptiste.bank.ui.components.RowDivider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Briques de mise en page communes aux quatre onglets de l'interface verre.
 * Règles (docs/refonte-ux.md) : titres de un à trois mots, mot informatif d'abord ; un en-tête
 * de section peut porter un lien vers l'écran détaillé ; les lignes d'une carte sont séparées
 * par un filet fin, décalé après la pastille.
 */

/** Le contenu défile sous la barre d'état et sous la barre d'onglets flottante, comme sur iOS. */
@Composable
internal fun glassContentPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 112.dp)
}

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)

/** « Octobre 2026 ». */
internal fun LocalDate.monthTitle(): String = format(monthTitle).replaceFirstChar { it.titlecase(Locale.FRANCE) }

/** Titre d'écran, qui défile avec le contenu. La place à droite est celle des boutons flottants. */
internal fun LazyListScope.largeTitle(title: String, subtitle: String? = null) {
    item(key = "title") {
        Column(modifier = Modifier.padding(start = 4.dp, end = 120.dp, top = 8.dp, bottom = 4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * En-tête de section : une pastille facultative, un titre court, puis à droite soit une valeur
 * ([trailing]), soit un lien vers l'écran détaillé ([action], avec chevron).
 */
internal fun LazyListScope.sectionHeader(
    key: String,
    title: String,
    trailing: String? = null,
    dot: Color? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    item(key = key) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(dot))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null && onAction != null) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onAction, role = Role.Button)
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Une carte de verre qui empile des lignes, séparées par un filet décalé de [inset]. */
@Composable
internal fun <T> GlassRowsCard(items: List<T>, inset: Dp = 48.dp, row: @Composable (T) -> Unit) {
    GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
        items.forEachIndexed { index, item ->
            if (index > 0) RowDivider(inset)
            row(item)
        }
    }
}

/** Ligne qui ouvre un écran : chevron à droite. */
@Composable
internal fun Chevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

/** Ligne de légende : pastille de couleur, libellé, valeur ; un détail facultatif dessous. */
@Composable
internal fun LegendRow(color: Color, label: String, value: String, detail: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
