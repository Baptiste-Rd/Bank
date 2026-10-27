package fr.baptiste.bank.ui.envelopes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.LocalGlassStyle

/**
 * Modèles proposés pour créer une enveloppe en un geste. Le montant reste vide :
 * c'est à toi de le fixer, l'app ne devine pas ton budget.
 */
internal val envelopeTemplates: List<Pair<String, EnvelopeType>> = listOf(
    "Courses" to EnvelopeType.DAILY,
    "Essence" to EnvelopeType.DAILY,
    "Sorties" to EnvelopeType.DAILY,
    "Coiffeur" to EnvelopeType.RECURRING,
    "Abonnements" to EnvelopeType.FIXED,
    "Prêt" to EnvelopeType.FIXED,
    "Projets" to EnvelopeType.ANNUAL,
    "CBD" to EnvelopeType.ANNUAL,
    "Épargne" to EnvelopeType.SAVINGS,
)

internal fun template(name: String, type: EnvelopeType) = Envelope(
    id = 0,
    name = name,
    type = type,
    frequency = type.defaultFrequency,
    amount = Money.ZERO,
    status = type.defaultStatus,
)

@Composable
fun EnvelopesTab(
    dashboard: Dashboard?,
    onEdit: (Envelope) -> Unit,
    onOpen: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
    archived: List<Envelope>,
    onRestore: (Envelope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = dashboard?.envelopes.orEmpty()
    val usedNames = lines.map { it.envelope.name.lowercase() }.toSet()
    val suggestions = envelopeTemplates.filter { (name, _) -> name.lowercase() !in usedNames }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
    ) {
        if (lines.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text("Aucune enveloppe", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Une enveloppe est un montant réservé à un usage. Commence par tes courses " +
                            "et ton essence : ce sont elles qui fixent ce que tu peux dépenser chaque jour.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        lines.groupBy { it.envelope.type }.forEach { (type, typeLines) ->
            item(key = "header-$type") {
                Text(
                    type.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
            }
            items(typeLines, key = { it.envelope.id }) { line ->
                EnvelopeRow(line, onClick = { onOpen(line) }, onCover = { onCover(line) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (suggestions.isNotEmpty()) {
            item(key = "templates") {
                TemplateChips(suggestions, onPick = { (name, type) -> onEdit(template(name, type)) })
            }
        }
        if (archived.isNotEmpty()) {
            item(key = "archived-header") {
                Text(
                    "Archivées",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
                )
            }
            items(archived, key = { "archived-${it.id}" }) { envelope ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(envelope.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${envelope.type.label}, ${envelope.amount.formatted()} ${envelope.frequency.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRestore(envelope) }) { Text("Restaurer") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TemplateChips(
    suggestions: List<Pair<String, EnvelopeType>>,
    onPick: (Pair<String, EnvelopeType>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            "Ajouter rapidement",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            // En verre : pastilles pleines légèrement claires, sans contour.
            val glass = LocalGlassStyle.current
            suggestions.forEach { suggestion ->
                AssistChip(
                    onClick = { onPick(suggestion) },
                    label = { Text(suggestion.first) },
                    border = if (glass) null else AssistChipDefaults.assistChipBorder(enabled = true),
                    colors = if (glass) {
                        AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.10f))
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
        }
    }
}
