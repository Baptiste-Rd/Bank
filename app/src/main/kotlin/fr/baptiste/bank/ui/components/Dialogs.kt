package fr.baptiste.bank.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.domain.period.Scale
import fr.baptiste.bank.ui.format.hint
import fr.baptiste.bank.ui.format.label
import java.time.LocalDate

/** Montant de départ d'un champ : vide pour zéro, sinon « 12,50 ». */
private fun Money.toFieldText(): String =
    if (cents == 0L) "" else "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

@Composable
fun ExpenseDialog(
    envelopes: List<Envelope>,
    onDismiss: () -> Unit,
    onSave: (amount: Money, date: LocalDate, label: String, envelope: Envelope) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var envelope by remember { mutableStateOf(envelopes.firstOrNull()) }
    val amount = parseMoney(amountText)
    val canSave = amount != null && amount.isPositive && envelope != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle dépense") },
        text = {
            if (envelopes.isEmpty()) {
                Text("Crée d'abord une enveloppe dans l'onglet Enveloppes.")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    MoneyField(value = amountText, onValueChange = { amountText = it }, label = "Montant")
                    ChoiceField(
                        label = "Enveloppe",
                        selected = envelope,
                        options = envelopes,
                        optionLabel = { it.name },
                        onSelect = { envelope = it },
                    )
                    DateField(label = "Date", date = date, onDateChange = { date = it })
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Libellé (facultatif)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val chosen = envelope
                    if (amount != null && chosen != null) {
                        onSave(amount, date, label.ifBlank { chosen.name }, chosen)
                    }
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Création ou modification d'une enveloppe. Changer le type propose l'échelle et le statut
 * habituels de ce type (une charge fixe est mensuelle et protégée, un projet est annuel...).
 */
@Composable
fun EnvelopeDialog(
    initial: Envelope?,
    onDismiss: () -> Unit,
    onSave: (Envelope) -> Unit,
    onArchive: (Envelope) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: EnvelopeType.DAILY) }
    var scale by rememberSaveable { mutableStateOf(initial?.scale ?: type.defaultScale) }
    var amountText by rememberSaveable { mutableStateOf(initial?.amount?.toFieldText().orEmpty()) }
    val amount = if (amountText.isBlank()) Money.ZERO else parseMoney(amountText)
    val canSave = name.isNotBlank() && amount != null && !amount.isNegative

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvelle enveloppe" else "Modifier l'enveloppe") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceField(
                    label = "Type",
                    selected = type,
                    options = EnvelopeType.entries,
                    optionLabel = { it.label },
                    onSelect = {
                        type = it
                        scale = it.defaultScale
                    },
                )
                Text(type.hint, style = MaterialTheme.typography.bodySmall)
                MoneyField(value = amountText, onValueChange = { amountText = it }, label = "Montant")
                ChoiceField(
                    label = "Échelle",
                    selected = scale,
                    options = Scale.entries,
                    optionLabel = { it.label },
                    onSelect = { scale = it },
                )
                if (initial != null) {
                    TextButton(onClick = { onArchive(initial) }) { Text("Archiver cette enveloppe") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = onClick@{
                    if (amount == null) return@onClick
                    val base = initial ?: Envelope(
                        id = 0,
                        name = "",
                        type = type,
                        scale = scale,
                        amount = Money.ZERO,
                        status = type.defaultStatus,
                    )
                    val statusChanged = initial == null || initial.type != type
                    onSave(
                        base.copy(
                            name = name.trim(),
                            type = type,
                            scale = scale,
                            amount = amount,
                            status = if (statusChanged) type.defaultStatus else base.status,
                        ),
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun IncomeDialog(
    current: Money?,
    onDismiss: () -> Unit,
    onSave: (Money) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(current?.toFieldText().orEmpty()) }
    val amount = parseMoney(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salaire mensuel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Montant net versé chaque mois. Il sert à calculer ce qui reste à répartir.",
                    style = MaterialTheme.typography.bodySmall,
                )
                MoneyField(value = amountText, onValueChange = { amountText = it }, label = "Salaire net")
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount.isPositive,
                onClick = { if (amount != null) onSave(amount) },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
