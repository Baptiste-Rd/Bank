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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.R
import fr.baptiste.bank.data.UnsortScope
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.local.RuleRow
import fr.baptiste.bank.domain.bank.LabelAlias
import fr.baptiste.bank.domain.bank.suggestedRuleKeyword
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.components.RowDivider

/*
 * Les fenêtres du tri, en plus de « Ranger » (SortOperationDialog) :
 * - Renommer : un nom lisible à la place du libellé de la banque, pour celle-ci et les suivantes ;
 * - Déclasser : remettre « à trier » des opérations du mois pour les ranger autrement ;
 * - Règles et alias : voir ce que l'app range et renomme seule, et le retirer.
 */

/**
 * Renommer une opération. Le libellé de la banque reste affiché dessous, pour qu'on sache toujours
 * d'où vient l'opération. La case crée un alias : les opérations dont le libellé contient le texte
 * indiqué prendront le même nom, déjà importées (sans nom) comme à venir.
 */
@Composable
fun RenameOperationDialog(
    operation: OperationRow,
    onRename: (alias: String, pattern: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasAlias = operation.label != operation.bankLabel
    var name by rememberSaveable { mutableStateOf(if (hasAlias) operation.label else "") }
    var forNext by rememberSaveable { mutableStateOf(true) }
    var pattern by rememberSaveable { mutableStateOf(suggestedRuleKeyword(operation.bankLabel)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer") },
        text = {
            BlurBehindWindow()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Libellé de la banque : ${operation.bankLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom affiché") },
                    placeholder = { Text("Ex. Forfait mobile") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = forNext, onCheckedChange = { forNext = it })
                    Text("Renommer aussi les prochaines qui contiennent :", style = MaterialTheme.typography.bodyMedium)
                }
                if (forNext) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (hasAlias) {
                    TextButton(onClick = { onRename("", null) }) { Text("Reprendre le libellé de la banque") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name, pattern.takeIf { forNext && it.isNotBlank() }) },
                enabled = name.isNotBlank(),
            ) { Text("Renommer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Ce qu'on choisit de déclasser. */
private enum class UnsortChoice(val label: String) { ALL("Tout le mois"), ENVELOPE("Une enveloppe"), LABEL("Un libellé") }

/**
 * Déclasser les opérations de la banque du mois en cours : elles repassent « à trier ». Les saisies
 * manuelles ne sont pas touchées (elles ont été rangées en les saisissant). Les règles restent :
 * « Appliquer les règles » range de nouveau ce qu'elles reconnaissent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnsortDialog(
    envelopes: List<Envelope>,
    onUnsort: (UnsortScope) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(UnsortChoice.ENVELOPE) }
    var envelopeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    val scope = when (choice) {
        UnsortChoice.ALL -> UnsortScope.All
        UnsortChoice.ENVELOPE -> envelopeId?.let { UnsortScope.Envelope(it) }
        UnsortChoice.LABEL -> text.trim().takeIf { it.isNotEmpty() }?.let { UnsortScope.Containing(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Déclasser ce mois-ci") },
        text = {
            BlurBehindWindow()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Les opérations de la banque choisies repassent à trier. Tes saisies gardent leur enveloppe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnsortChoice.entries.forEach { option ->
                        FilterChip(selected = option == choice, onClick = { choice = option }, label = { Text(option.label) })
                    }
                }
                when (choice) {
                    UnsortChoice.ALL -> Unit
                    UnsortChoice.ENVELOPE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        envelopes.forEach { envelope ->
                            FilterChip(
                                selected = envelope.id == envelopeId,
                                onClick = { envelopeId = envelope.id },
                                label = { Text(envelope.name) },
                            )
                        }
                    }
                    UnsortChoice.LABEL -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Le libellé contient") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope?.let(onUnsort) }, enabled = scope != null) { Text("Déclasser") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Ce que l'app range et renomme seule. Retirer une règle ne déclasse rien : elle ne s'applique plus. */
@Composable
fun RulesDialog(
    rules: List<RuleRow>,
    aliases: List<LabelAlias>,
    onDeleteRule: (Long) -> Unit,
    onDeleteAlias: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Règles et alias") },
        text = {
            BlurBehindWindow()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Rangement", style = MaterialTheme.typography.titleSmall)
                if (rules.isEmpty()) {
                    Hint("Aucune règle. Ranger une opération avec « Ranger aussi les prochaines » en crée une.")
                }
                rules.forEachIndexed { index, rule ->
                    if (index > 0) RowDivider(0.dp)
                    RuleLine("« ${rule.pattern} »", "va dans ${rule.envelopeName}") { onDeleteRule(rule.id) }
                }
                Text("Noms", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                if (aliases.isEmpty()) {
                    Hint("Aucun alias. « Renommer » sur une opération en crée un.")
                }
                aliases.forEachIndexed { index, alias ->
                    if (index > 0) RowDivider(0.dp)
                    RuleLine("« ${alias.pattern} »", "s'affiche ${alias.alias}") { onDeleteAlias(alias.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun RuleLine(pattern: String, effect: String, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(pattern, style = MaterialTheme.typography.bodyMedium)
            Text(effect, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Retirer")
        }
    }
}
