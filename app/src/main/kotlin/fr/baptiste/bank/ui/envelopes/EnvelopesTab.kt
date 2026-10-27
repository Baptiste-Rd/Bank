package fr.baptiste.bank.ui.envelopes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.format.label

/**
 * Modèles proposés pour créer une enveloppe en un geste. Le montant reste vide :
 * c'est à toi de le fixer, l'app ne devine pas ton budget.
 */
private val templates: List<Pair<String, EnvelopeType>> = listOf(
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

private fun template(name: String, type: EnvelopeType) = Envelope(
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
    modifier: Modifier = Modifier,
) {
    val lines = dashboard?.envelopes.orEmpty()
    val usedNames = lines.map { it.envelope.name.lowercase() }.toSet()
    val suggestions = templates.filter { (name, _) -> name.lowercase() !in usedNames }

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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateChips(
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
            suggestions.forEach { suggestion ->
                AssistChip(
                    onClick = { onPick(suggestion) },
                    label = { Text(suggestion.first) },
                )
            }
        }
    }
}
