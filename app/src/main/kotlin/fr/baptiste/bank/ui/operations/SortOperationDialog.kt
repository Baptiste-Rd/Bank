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
import androidx.compose.material3.AssistChip
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
import fr.baptiste.bank.domain.bank.labelQuestion
import fr.baptiste.bank.domain.bank.suggestedRuleKeyword
import fr.baptiste.bank.ui.assistant.openClaudeWith
import androidx.compose.ui.platform.LocalContext
import fr.baptiste.bank.domain.budget.HoldingAccount
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.TransactionSource
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
    /** Ouvre la création d'une enveloppe ; on revient ici ensuite, avec elle déjà choisie. */
    onCreateEnvelope: () -> Unit = {},
    preselectedId: Long? = null,
    /** Enveloppe proposée d'après les opérations déjà rangées qui se ressemblent. */
    suggestedId: Long? = null,
    onRename: () -> Unit = {},
    /** Mes livrets et investissements : un virement vers l'un d'eux n'est pas une dépense. */
    accounts: List<HoldingAccount> = emptyList(),
    onTransferToAccount: (Long) -> Unit = {},
) {
    val currentName = operation.envelopeNames?.split(", ")?.firstOrNull()
    val currentId = envelopes.firstOrNull { it.name == currentName }?.id
    var selectedId by rememberSaveable { mutableStateOf(preselectedId ?: currentId ?: suggestedId) }
    val isSuggestion = preselectedId == null && currentId == null && suggestedId != null && selectedId == suggestedId
    // Une saisie manuelle n'a pas de libellé de banque à reconnaître : pas de règle proposée d'office.
    var createRule by rememberSaveable { mutableStateOf(operation.source != TransactionSource.MANUAL) }
    var keyword by rememberSaveable { mutableStateOf(suggestedRuleKeyword(operation.bankLabel)) }
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
                if (operation.label != operation.bankLabel) {
                    Text(
                        "Banque : ${operation.bankLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (operation.source != TransactionSource.MANUAL) {
                    val context = LocalContext.current
                    Row {
                        TextButton(onClick = onRename) { Text("Renommer") }
                        // Un libellé obscur : Claude dit qui est derrière (libellé anonymisé, montant, date).
                        TextButton(onClick = {
                            openClaudeWith(context, labelQuestion(operation.bankLabel, operation.amountCents, operation.date))
                        }) { Text("Demander à Claude") }
                    }
                }
                Text("Ranger dans", style = MaterialTheme.typography.titleSmall)
                if (isSuggestion) {
                    Text(
                        "Proposé d'après tes opérations précédentes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    envelopes.forEach { envelope ->
                        FilterChip(
                            selected = envelope.id == selectedId,
                            onClick = { selectedId = envelope.id },
                            label = { Text(envelope.name) },
                        )
                    }
                    // Aucune enveloppe ne convient : on la crée sans quitter le tri.
                    AssistChip(onClick = onCreateEnvelope, label = { Text("+ Nouvelle enveloppe") })
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
                if (accounts.isNotEmpty()) {
                    Text(
                        if (operation.amountCents < 0) "Ou virement vers un de mes comptes" else "Ou virement depuis un de mes comptes",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { account ->
                            AssistChip(onClick = { onTransferToAccount(account.id) }, label = { Text(account.name) })
                        }
                    }
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
