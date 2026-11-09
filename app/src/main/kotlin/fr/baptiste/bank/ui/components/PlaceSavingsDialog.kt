package fr.baptiste.bank.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.SavingsGoalLine
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.ui.format.summary
import fr.baptiste.bank.ui.theme.BankTheme

/**
 * Répartir de l'argent entre les objectifs : les livrets au départ, ou ce qui est arrivé sur les
 * comptes. [total] négatif : un retrait, chaque montant saisi est alors retiré de l'objectif.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSavingsDialog(
    title: String,
    detail: String,
    goals: List<SavingsGoalLine>,
    total: Money,
    proposal: Map<Long, Money>,
    onDismiss: () -> Unit,
    onConfirm: (Map<Long, Money>) -> Unit,
) {
    val withdrawal = total.isNegative
    val target = if (withdrawal) Money(-total.cents) else total
    val texts = remember { mutableStateMapOf<Long, String>().apply { goals.forEach { put(it.envelope.id, proposal[it.envelope.id]?.toFieldText().orEmpty()) } } }
    val parts = goals.associate { line -> line.envelope.id to (texts[line.envelope.id]?.takeIf { it.isNotBlank() }?.let(::parseMoney)) }
    val valid = parts.values.all { it == null || !it.isNegative } && texts.values.all { it.isBlank() || parseMoney(it) != null }
    val placed = parts.values.fold(Money.ZERO) { sum, it -> sum + (it ?: Money.ZERO) }
    val left = target - placed
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            goals.forEach { line ->
                val missing = line.missing?.takeIf { it.isPositive && !withdrawal }?.let { " · il manque ${it.summary()}" }.orEmpty()
                MoneyField(
                    value = texts[line.envelope.id].orEmpty(),
                    onValueChange = { texts[line.envelope.id] = it },
                    label = "${line.envelope.name} · ${line.held.summary()}$missing",
                )
            }
            Text(
                if (left.isNegative) "${Money(-left.cents).summary()} de trop" else "Reste à répartir : ${left.summary()}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (left.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onConfirm(parts.mapNotNull { (id, part) -> part?.takeIf { it.isPositive }?.let { id to if (withdrawal) Money(-it.cents) else it } }.toMap()) },
                enabled = valid && !left.isNegative && placed.isPositive,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text(if (withdrawal) "Retirer" else "Répartir") }
        }
    }
}
