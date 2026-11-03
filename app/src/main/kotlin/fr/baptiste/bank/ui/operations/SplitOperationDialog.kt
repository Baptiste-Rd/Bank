package fr.baptiste.bank.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.OperationSplit
import fr.baptiste.bank.domain.bank.isValidSplit
import fr.baptiste.bank.domain.bank.splitRemainder
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.components.ChoiceField
import fr.baptiste.bank.ui.components.MoneyField
import fr.baptiste.bank.ui.components.toFieldText
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.theme.BankTheme

/*
 * Répartir une opération : la première ligne prend le reste du total, les autres reçoivent un
 * montant. « 10 € dans Maison » suffit : les 23 € restants vont d'eux-mêmes dans Loisirs.
 */

private class Part(envelopeId: Long?, amountText: String) {
    var envelopeId by mutableStateOf(envelopeId)
    var amountText by mutableStateOf(amountText)
}

@Composable
fun SplitOperationDialog(
    label: String,
    load: suspend () -> OperationSplit?,
    envelopes: List<Envelope>,
    /** Enveloppe proposée pour la première ligne (celle choisie dans le tri, sinon la suggestion). */
    preselectedId: Long?,
    onSave: (List<Pair<Envelope, Money>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var split by remember { mutableStateOf<OperationSplit?>(null) }
    var firstId by remember { mutableStateOf<Long?>(null) }
    val others = remember { mutableStateListOf<Part>() }
    LaunchedEffect(Unit) {
        val loaded = load() ?: return@LaunchedEffect
        split = loaded
        val parts = loaded.parts.filter { (id, _) -> envelopes.any { it.id == id } }
        if (parts.size >= 2) {
            firstId = parts.first().first
            parts.drop(1).forEach { (id, amount) -> others += Part(id, amount.toFieldText()) }
        } else {
            firstId = preselectedId ?: parts.firstOrNull()?.first
            others += Part(null, "")
        }
    }
    val total = split?.total
    val amounts = others.map { parseMoney(it.amountText.ifBlank { "0" }) ?: Money(-1) }
    val remainder = total?.let { splitRemainder(it, amounts) }
    val first = envelopes.firstOrNull { it.id == firstId }
    val chosen = others.mapIndexedNotNull { index, part ->
        envelopes.firstOrNull { it.id == part.envelopeId }?.let { it to amounts[index] }
    }
    val parts = if (first != null && remainder != null) listOf(first to remainder) + chosen else emptyList()
    val valid = total != null && chosen.size == others.size && isValidSplit(total, parts.map { it.second })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Répartir") },
        text = {
            BlurBehindWindow()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "$label · ${total?.formatted() ?: "…"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Première ligne : le reste du total.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceField(
                        label = "Enveloppe",
                        selected = first,
                        options = envelopes,
                        optionLabel = { it.name },
                        onSelect = { firstId = it.id },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        remainder?.formatted() ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (remainder?.isPositive == false) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(96.dp),
                    )
                }
                Text("Le reste", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                others.forEachIndexed { index, part ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceField(
                            label = "Enveloppe",
                            selected = envelopes.firstOrNull { it.id == part.envelopeId },
                            options = envelopes,
                            optionLabel = { it.name },
                            onSelect = { part.envelopeId = it.id },
                            modifier = Modifier.weight(1f),
                        )
                        MoneyField(value = part.amountText, onValueChange = { part.amountText = it }, label = "Montant", modifier = Modifier.width(120.dp))
                    }
                    if (others.size > 1) {
                        TextButton(onClick = { others.removeAt(index) }) { Text("Retirer cette part") }
                    }
                }
                TextButton(onClick = { others += Part(null, "") }) { Text("Ajouter une part") }
                if (remainder != null && !remainder.isPositive) {
                    Text("Les parts dépassent le total.", style = MaterialTheme.typography.bodySmall, color = BankTheme.status.over)
                }
                if (valid) {
                    Text(
                        parts.joinToString(" · ") { (envelope, amount) -> "${amount.formatted()} dans ${envelope.name}" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(parts) }) { Text("Répartir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
