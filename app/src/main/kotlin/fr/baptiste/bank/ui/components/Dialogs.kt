package fr.baptiste.bank.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import fr.baptiste.bank.ui.format.hint
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.format.unitName
import java.time.LocalDate

/** Montant de départ d'un champ : vide pour zéro, sinon « 12,50 ». */
private fun Money.toFieldText(): String =
    if (cents == 0L) "" else "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

/**
 * Création ou modification d'une enveloppe. Changer le type propose la fréquence et le statut
 * habituels de ce type (une charge fixe est mensuelle et protégée, un projet est annuel...).
 * La période se règle librement : par mois, toutes les 6 semaines, tous les 10 jours...
 */
@Composable
fun EnvelopeDialog(
    initial: Envelope?,
    onDismiss: () -> Unit,
    onSave: (Envelope) -> Unit,
    onArchive: (Envelope) -> Unit,
) {
    val today = LocalDate.now()
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: EnvelopeType.DAILY) }
    var scale by rememberSaveable { mutableStateOf(initial?.frequency?.scale ?: type.defaultFrequency.scale) }
    var everyText by rememberSaveable { mutableStateOf((initial?.frequency?.every ?: 1).toString()) }
    var anchor by remember { mutableStateOf(initial?.anchor ?: today) }
    var status by rememberSaveable { mutableStateOf(initial?.status ?: type.defaultStatus) }
    var amountText by rememberSaveable { mutableStateOf(initial?.amount?.toFieldText().orEmpty()) }

    val amount = if (amountText.isBlank()) Money.ZERO else parseMoney(amountText)
    val every = everyText.toIntOrNull()?.takeIf { it in 1..999 }
    val frequency = every?.let { Frequency(it, scale) }
    val canSave = name.isNotBlank() && amount != null && !amount.isNegative && frequency != null
    // Un modèle (id 0) pré-remplit le formulaire mais reste une création.
    val isExisting = initial != null && initial.id != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExisting) "Modifier l'enveloppe" else "Nouvelle enveloppe") },
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
                        scale = it.defaultFrequency.scale
                        everyText = it.defaultFrequency.every.toString()
                        status = it.defaultStatus
                    },
                )
                Text(type.hint, style = MaterialTheme.typography.bodySmall)
                MoneyField(value = amountText, onValueChange = { amountText = it }, label = "Montant par période")

                Text("Période", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (scale == Scale.WEEK) "Toutes les" else "Tous les", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = everyText,
                        onValueChange = { value -> everyText = value.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        isError = every == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(72.dp),
                    )
                    ChoiceField(
                        label = "",
                        selected = scale,
                        options = Scale.entries,
                        optionLabel = { it.unitName(every ?: 1) },
                        onSelect = { scale = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (frequency != null && !frequency.isCalendar) {
                    DateField(label = "Début d'une période", date = anchor, onDateChange = { anchor = it })
                    Text(
                        "Période en cours : ${frequency.periodContaining(today, anchor).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (frequency != null) {
                    Text(
                        "Suit le calendrier : ${frequency.periodContaining(today, anchor).label}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Protégée", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Jamais proposée comme source pour couvrir une autre enveloppe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = status == EnvelopeStatus.PROTECTED,
                        onCheckedChange = { status = if (it) EnvelopeStatus.PROTECTED else EnvelopeStatus.FLEXIBLE },
                    )
                }

                if (isExisting && initial != null) {
                    TextButton(onClick = { onArchive(initial) }) { Text("Archiver cette enveloppe") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (amount != null && frequency != null) {
                        val base = initial ?: Envelope(
                            id = 0,
                            name = "",
                            type = type,
                            frequency = frequency,
                            amount = Money.ZERO,
                            status = status,
                        )
                        onSave(
                            base.copy(
                                name = name.trim(),
                                type = type,
                                frequency = frequency,
                                amount = amount,
                                status = status,
                                anchor = if (frequency.isCalendar) null else anchor,
                            ),
                        )
                    }
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
