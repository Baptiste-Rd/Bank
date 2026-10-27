package fr.baptiste.bank.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import java.time.LocalDate

/** Quand la dépense a eu lieu. [WholePeriod] est le rattrapage : toute la période de l'enveloppe. */
private sealed interface When {
    data object Today : When
    data object Yesterday : When
    data class Other(val date: LocalDate) : When
    data object WholePeriod : When
}

/**
 * Saisie d'une dépense dans un panneau qui monte du bas de l'écran.
 * Le montant a le focus dès l'ouverture ; enveloppe et date se choisissent d'un geste.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseSheet(
    envelopes: List<Envelope>,
    onDismiss: () -> Unit,
    onCreateEnvelope: () -> Unit,
    onSave: (amount: Money, date: LocalDate, label: String, envelope: Envelope, wholePeriod: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var envelopeId by rememberSaveable { mutableStateOf(envelopes.firstOrNull()?.id) }
    var whenChoice by remember { mutableStateOf<When>(When.Today) }
    var pickingDate by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val today = LocalDate.now()
    val envelope = envelopes.firstOrNull { it.id == envelopeId }
    val amount = parseMoney(amountText)
    val canSave = amount != null && amount.isPositive && envelope != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        BlurBehindWindow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Nouvelle dépense", style = MaterialTheme.typography.titleLarge)

            if (envelopes.isEmpty()) {
                Text(
                    "Une dépense se range dans une enveloppe. Crée d'abord tes enveloppes, par exemple Courses ou Essence.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onCreateEnvelope, modifier = Modifier.fillMaxWidth()) {
                    Text("Créer une enveloppe")
                }
                return@Column
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                placeholder = { Text("0,00") },
                suffix = { Text("€") },
                singleLine = true,
                isError = amountText.isNotBlank() && amount == null,
                supportingText = if (amountText.isNotBlank() && amount == null) {
                    { Text("Montant invalide, par exemple 12,50") }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.headlineMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            // Le champ vient d'être composé : on peut lui donner le focus pour ouvrir le clavier.
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

            ChipGroup(title = "Enveloppe") {
                envelopes.forEach { candidate ->
                    FilterChip(
                        selected = candidate.id == envelopeId,
                        onClick = { envelopeId = candidate.id },
                        label = { Text(candidate.name) },
                    )
                }
            }

            ChipGroup(title = "Quand") {
                FilterChip(
                    selected = whenChoice == When.Today,
                    onClick = { whenChoice = When.Today },
                    label = { Text("Aujourd'hui") },
                )
                FilterChip(
                    selected = whenChoice == When.Yesterday,
                    onClick = { whenChoice = When.Yesterday },
                    label = { Text("Hier") },
                )
                val other = whenChoice as? When.Other
                FilterChip(
                    selected = other != null,
                    onClick = { pickingDate = true },
                    label = { Text(other?.date?.formatted() ?: "Autre date") },
                )
                FilterChip(
                    selected = whenChoice == When.WholePeriod,
                    onClick = { whenChoice = When.WholePeriod },
                    label = { Text(wholePeriodLabel(envelope?.periodContaining(today))) },
                )
            }
            if (whenChoice == When.WholePeriod && envelope != null) {
                Text(
                    "Rattrapage : compté comme déjà dépensé sur ${envelope.periodContaining(today).label}, " +
                        "sans réduire ce que tu peux dépenser aujourd'hui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Libellé (facultatif)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = canSave,
                onClick = {
                    if (amount != null && envelope != null) {
                        val date = when (val choice = whenChoice) {
                            When.Today, When.WholePeriod -> today
                            When.Yesterday -> today.minusDays(1)
                            is When.Other -> choice.date
                        }
                        onSave(amount, date, label.ifBlank { envelope.name }, envelope, whenChoice == When.WholePeriod)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer la dépense") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
        }
    }

    if (pickingDate) {
        DatePickerPopup(
            initial = (whenChoice as? When.Other)?.date ?: today,
            onDismiss = { pickingDate = false },
            onPick = {
                whenChoice = if (it == today) When.Today else When.Other(it)
                pickingDate = false
            },
        )
    }
}

private fun wholePeriodLabel(period: Period?): String = when (period) {
    is Period.Month, null -> "Tout le mois"
    is Period.Week -> "Toute la semaine"
    is Period.Year -> "Toute l'année"
    is Period.Span -> "Toute la période"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
