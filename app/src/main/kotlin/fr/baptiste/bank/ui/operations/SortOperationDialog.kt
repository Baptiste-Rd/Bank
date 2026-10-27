package fr.baptiste.bank.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.domain.bank.suggestedRuleKeyword
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.format.formatted

/**
 * Ranger une opération de la banque dans une enveloppe.
 *
 * La case « Ranger aussi les prochaines » crée une règle : toute opération dont le libellé
 * contient le texte indiqué ira désormais dans cette enveloppe, à l'import comme parmi celles
 * qui attendent déjà. « Hors budget » sert au salaire, aux virements entre tes comptes... :
 * l'opération reste visible mais ne compte dans aucune enveloppe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortOperationDialog(
    operation: OperationRow,
    envelopes: List<Envelope>,
    onSort: (envelope: Envelope, ruleKeyword: String?) -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentName = operation.envelopeNames?.split(", ")?.firstOrNull()
    var selectedId by rememberSaveable { mutableStateOf(envelopes.firstOrNull { it.name == currentName }?.id) }
    var createRule by rememberSaveable { mutableStateOf(true) }
    var keyword by rememberSaveable { mutableStateOf(suggestedRuleKeyword(operation.label)) }
    val selected = envelopes.firstOrNull { it.id == selectedId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(operation.label) },
        text = {
            BlurBehindWindow()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${Money(operation.amountCents).formatted()}, ${dayTitle(operation.date, java.time.LocalDate.now()).lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Ranger dans", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    envelopes.forEach { envelope ->
                        FilterChip(
                            selected = envelope.id == selectedId,
                            onClick = { selectedId = envelope.id },
                            label = { Text(envelope.name) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = createRule, onCheckedChange = { createRule = it })
                    Text("Ranger aussi les prochaines qui contiennent :", style = MaterialTheme.typography.bodyMedium)
                }
                if (createRule) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = onIgnore, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Hors budget (salaire, virement entre comptes...)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onSort(it, keyword.takeIf { createRule && it.isNotBlank() }) } },
                enabled = selected != null,
            ) { Text("Ranger") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
