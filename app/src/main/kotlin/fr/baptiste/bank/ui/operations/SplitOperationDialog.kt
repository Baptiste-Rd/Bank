package fr.baptiste.bank.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import fr.baptiste.bank.domain.bank.equalParts
import fr.baptiste.bank.domain.bank.isValidSplit
import fr.baptiste.bank.domain.bank.percentParts
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.ui.components.ChoiceField
import fr.baptiste.bank.ui.components.MoneyField
import fr.baptiste.bank.ui.components.SheetHeader
import fr.baptiste.bank.ui.components.toFieldText
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.theme.BankTheme

/*
 * Répartir une opération, comme dans le prototype testé : autant de parts que l'on veut, en
 * montants ou en pourcentages ; « Parts égales » et « Reste dans la dernière » font le calcul.
 * On n'enregistre que quand le compte est bon, chaque enveloppe une seule fois.
 */

private class Part(envelopeId: Long?, value: String) {
    var envelopeId by mutableStateOf(envelopeId)
    var value by mutableStateOf(value)
}

private enum class SplitMode { AMOUNTS, PERCENTS }

/** « 33,5 » → 3350 centièmes de pour cent (le même analyseur que les montants). */
private fun parsePercent(text: String): Long? = parseMoney(text.ifBlank { "0" })?.cents

private fun Long.percentText(): String = Money(this).toFieldText()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitOperationDialog(
    label: String,
    load: suspend () -> OperationSplit?,
    envelopes: List<Envelope>,
    /** Enveloppe proposée pour la première part (celle choisie dans le tri, sinon la suggestion). */
    preselectedId: Long?,
    onSave: (List<Pair<Envelope, Money>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var split by remember { mutableStateOf<OperationSplit?>(null) }
    var mode by remember { mutableStateOf(SplitMode.AMOUNTS) }
    val parts = remember { mutableStateListOf<Part>() }
    LaunchedEffect(Unit) {
        val loaded = load() ?: return@LaunchedEffect
        split = loaded
        val existing = loaded.parts.filter { (id, _) -> envelopes.any { it.id == id } }
        if (existing.size >= 2) {
            existing.forEach { (id, amount) -> parts += Part(id, amount.toFieldText()) }
        } else {
            val first = preselectedId ?: existing.firstOrNull()?.first
            parts += Part(first, "")
            parts += Part(envelopes.firstOrNull { it.id != first }?.id, "")
        }
    }
    val total = split?.total
    fun amounts(): List<Money?> = when (mode) {
        SplitMode.AMOUNTS -> parts.map { parseMoney(it.value.ifBlank { "0" }) }
        SplitMode.PERCENTS -> {
            val points = parts.map { parsePercent(it.value) }
            if (total == null || points.any { it == null }) points.map { null } else percentParts(total, points.map { it!! })
        }
    }
    val values = amounts()
    val known = values.filterNotNull()
    val rest = if (total != null && known.size == values.size) total - known.fold(Money.ZERO) { sum, it -> sum + it } else null
    val chosen = parts.mapIndexedNotNull { index, part ->
        val envelope = envelopes.firstOrNull { it.id == part.envelopeId } ?: return@mapIndexedNotNull null
        values[index]?.let { envelope to it }
    }
    val distinct = parts.mapNotNull { it.envelopeId }.toSet().size == parts.size
    val valid = total != null && chosen.size == parts.size && distinct && isValidSplit(total, chosen.map { it.second })

    fun setValues(amounts: List<Money>) {
        val totalCents = total?.cents ?: return
        amounts.forEachIndexed { index, amount ->
            parts[index].value = when (mode) {
                SplitMode.AMOUNTS -> amount.toFieldText()
                SplitMode.PERCENTS -> if (totalCents == 0L) "" else Math.round(amount.cents * 10_000.0 / totalCents).percentText()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetHeader(title = "Répartir", onCancel = onDismiss, action = "Répartir", enabled = valid, onAction = { onSave(chosen) })
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(total?.formatted() ?: "…", style = MaterialTheme.typography.headlineMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SplitMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = {
                            if (mode != option) {
                                // On garde les mêmes parts, exprimées autrement.
                                val before = amounts()
                                mode = option
                                if (before.all { it != null }) setValues(before.map { it!! })
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, SplitMode.entries.size),
                    ) { Text(if (option == SplitMode.AMOUNTS) "Montants" else "Pourcentages") }
                }
            }
            parts.forEachIndexed { index, part ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceField(
                        label = "Enveloppe",
                        selected = envelopes.firstOrNull { it.id == part.envelopeId },
                        options = envelopes,
                        optionLabel = { it.name },
                        onSelect = { part.envelopeId = it.id },
                        modifier = Modifier.weight(1f),
                    )
                    MoneyField(
                        value = part.value,
                        onValueChange = { part.value = it },
                        label = if (mode == SplitMode.AMOUNTS) "Montant" else "%",
                        modifier = Modifier.width(112.dp),
                    )
                }
                // L'autre unité, pour vérifier d'un coup d'œil.
                val amount = values.getOrNull(index)
                if (amount != null && total != null && total.isPositive && amount.isPositive) {
                    Text(
                        if (mode == SplitMode.AMOUNTS) "${Math.round(amount.cents * 100.0 / total.cents)} %" else amount.formatted(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (parts.size > 2) {
                    TextButton(onClick = { parts.removeAt(index) }) { Text("Retirer cette part") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val used = parts.mapNotNull { it.envelopeId }.toSet()
                    parts += Part(envelopes.firstOrNull { it.id !in used }?.id, "")
                }) { Text("Ajouter une part") }
                OutlinedButton(onClick = { total?.let { setValues(equalParts(it, parts.size)) } }) { Text("Parts égales") }
                OutlinedButton(onClick = {
                    val others = values.dropLast(1)
                    if (total != null && others.all { it != null }) {
                        val left = total - others.fold(Money.ZERO) { sum, it -> sum + it!! }
                        if (left.isPositive) setValues(others.map { it!! } + left)
                    }
                }) { Text("Reste dans la dernière") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val (text, color) = when {
                    rest == null -> "Un montant n'est pas lisible" to BankTheme.status.warning
                    (rest.cents == 0L) && distinct -> "Le compte est bon" to BankTheme.status.gain
                    (rest.cents == 0L) -> "Une enveloppe est choisie deux fois" to BankTheme.status.warning
                    rest.isPositive -> "Reste à répartir" to BankTheme.status.warning
                    else -> "En trop" to BankTheme.status.over
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
                if (rest != null && !(rest.cents == 0L)) {
                    Text((if (rest.isNegative) -rest else rest).formatted(), style = MaterialTheme.typography.titleMedium, color = color)
                }
            }
        }
    }
}
