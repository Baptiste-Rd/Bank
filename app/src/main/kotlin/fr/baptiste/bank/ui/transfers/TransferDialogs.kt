package fr.baptiste.bank.ui.transfers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.ui.components.RowDivider

import fr.baptiste.bank.ui.components.BlurBehindWindow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.local.TransferRow
import fr.baptiste.bank.domain.budget.CoveragePlan
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.ui.components.ChoiceField
import fr.baptiste.bank.ui.components.MoneyField
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.BankTheme

private const val WALLET = "Portefeuille"
private const val WALLET_ID = -1L

/** Nom d'une extrémité de transfert : une enveloppe, ou le portefeuille (null). */
private fun Dashboard.nameOf(envelopeId: Long?): String =
    envelopeId?.let { id -> envelopes.firstOrNull { it.envelope.id == id }?.envelope?.name } ?: WALLET

/**
 * Proposition de couverture d'un dépassement : rien n'est transféré avant validation.
 * Chaque part est affichée avec sa source, dans l'ordre où elle est sollicitée.
 */
@Composable
fun CoverDialog(
    dashboard: Dashboard,
    plan: CoveragePlan,
    onDismiss: () -> Unit,
    onConfirm: (List<TransferLeg>) -> Unit,
) {
    val target = plan.target
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couvrir ${target.envelope.name}") },
        text = {
            BlurBehindWindow()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (target.remaining.isNegative) {
                        "L'enveloppe est dépassée de ${(-target.remaining).formatted()}."
                    } else {
                        "À ce rythme, il manquera ${target.projection.overrun.formatted()} à la fin de la période."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (plan.legs.isEmpty()) {
                    Text(
                        "Aucune source disponible : le portefeuille est vide et les autres enveloppes " +
                            "sont protégées, épuisées ou elles-mêmes à surveiller.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BankTheme.status.over,
                    )
                } else {
                    Text("Pris sur", style = MaterialTheme.typography.labelLarge)
                    plan.legs.forEach { leg ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(dashboard.nameOf(leg.sourceEnvelopeId), modifier = Modifier.weight(1f))
                            Text(leg.amount.formatted(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (plan.shortfall.isPositive) {
                        Text(
                            "Il manque encore ${plan.shortfall.formatted()}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BankTheme.status.over,
                        )
                    }
                    Text(
                        "Le disponible du jour est recalculé à partir d'aujourd'hui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = plan.legs.isNotEmpty(), onClick = { onConfirm(plan.legs) }) {
                Text("Transférer ${(plan.needed - plan.shortfall).formatted()}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Transfert manuel vers une enveloppe ([incoming] = true) ou depuis elle.
 * L'autre extrémité se choisit parmi le portefeuille et les autres enveloppes.
 */
@Composable
fun TransferDialog(
    dashboard: Dashboard,
    line: EnvelopeLine,
    incoming: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (counterpartId: Long?, amount: Money, reason: String) -> Unit,
) {
    val others = dashboard.envelopes.filter { it.envelope.id != line.envelope.id }
    // Le portefeuille est représenté par un identifiant qui ne correspond à aucune enveloppe.
    val options: List<Long> = listOf(WALLET_ID) + others.map { it.envelope.id }
    var counterpart by remember { mutableStateOf(WALLET_ID) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    val amount = parseMoney(amountText)

    fun optionLabel(id: Long): String {
        if (id == WALLET_ID) {
            return dashboard.unallocated?.let { "$WALLET (${it.formatted()})" } ?: WALLET
        }
        val other = others.first { it.envelope.id == id }
        return "${other.envelope.name} (reste ${other.remaining.formatted()})"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (incoming) "Ajouter à ${line.envelope.name}" else "Retirer de ${line.envelope.name}") },
        text = {
            BlurBehindWindow()
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                MoneyField(value = amountText, onValueChange = { amountText = it }, label = "Montant")
                ChoiceField(
                    label = if (incoming) "Pris sur" else "Rendu à",
                    selected = counterpart,
                    options = options,
                    optionLabel = ::optionLabel,
                    onSelect = { counterpart = it },
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif (facultatif)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Compté sur ${line.period.label}, à partir d'aujourd'hui. Les périodes passées ne changent pas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount.isPositive,
                onClick = { if (amount != null) onConfirm(counterpart.takeIf { it != WALLET_ID }, amount, reason) },
            ) { Text(if (incoming) "Ajouter" else "Retirer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Fiche d'une enveloppe : sa situation et les actions possibles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvelopeSheet(
    line: EnvelopeLine,
    transfers: List<TransferRow>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onWithdraw: () -> Unit,
    onCover: (() -> Unit)?,
    onEdit: () -> Unit,
    /** Dépenses rangées dans l'enveloppe sur sa période en cours. */
    operations: List<OperationRow> = emptyList(),
    /** Toucher une dépense : la déplacer vers une autre enveloppe. */
    onMoveOperation: (OperationRow) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BlurBehindWindow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(line.envelope.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${line.envelope.frequency.label.replaceFirstChar { it.uppercase() }}, période en cours : ${line.period.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Figure("Prévu", line.allocated)
                Figure("Transferts", line.transferred, signed = true)
                Figure("Dépensé", line.spent)
                Figure("Reste", line.remaining, emphasis = true)
            }
            if (onCover != null) {
                Button(onClick = onCover, modifier = Modifier.fillMaxWidth()) { Text("Couvrir le dépassement") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Ajouter de l'argent") }
                OutlinedButton(onClick = onWithdraw, modifier = Modifier.weight(1f)) { Text("Retirer") }
            }
            // Les dépenses de la période : on voit d'où vient le chiffre, et on corrige un mauvais rangement.
            if (operations.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dépenses de la période", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${operations.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    operations.forEachIndexed { index, operation ->
                        if (index > 0) RowDivider(inset = 0.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = "Déplacer") { onMoveOperation(operation) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(operation.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    operation.date.formatted(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(Money(operation.amountCents).formatted(), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                Text(
                    "Touche une dépense pour la déplacer vers une autre enveloppe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val mine = transfers.filter {
                !it.cancelled && it.date in line.period &&
                    (it.sourceEnvelopeId == line.envelope.id || it.targetEnvelopeId == line.envelope.id)
            }
            if (mine.isNotEmpty()) {
                Text("Transferts de la période", style = MaterialTheme.typography.labelLarge)
                mine.take(6).forEach { row ->
                    val incoming = row.targetEnvelopeId == line.envelope.id
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (incoming) "Depuis ${row.sourceName ?: WALLET}" else "Vers ${row.targetName ?: WALLET}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            (if (incoming) "+" else "-") + Money(row.amountCents).formatted(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Modifier l'enveloppe") }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Figure(
    label: String,
    amount: Money,
    signed: Boolean = false,
    emphasis: Boolean = false,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (signed && amount.isPositive) "+${amount.formatted()}" else amount.formatted(),
            style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasis && amount.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Journal des transferts, du plus récent au plus ancien. Un transfert annulé reste visible, barré. */
@Composable
fun JournalDialog(
    transfers: List<TransferRow>,
    onDismiss: () -> Unit,
    onCancel: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Journal des transferts") },
        text = {
            BlurBehindWindow()
            if (transfers.isEmpty()) {
                Text(
                    "Aucun transfert. Depuis une enveloppe, Ajouter ou Retirer déplace de l'argent " +
                        "entre elle, le portefeuille et les autres enveloppes.",
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(transfers, key = { it.id }) { row ->
                        JournalRow(row, onCancel = { onCancel(row.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun JournalRow(row: TransferRow, onCancel: () -> Unit) {
    val decoration = if (row.cancelled) TextDecoration.LineThrough else TextDecoration.None
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${row.sourceName ?: WALLET} vers ${row.targetName ?: WALLET}",
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = decoration,
            )
            Text(
                listOfNotNull(row.date.formatted(), row.reason, "annulé".takeIf { row.cancelled }).joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money(row.amountCents).formatted(), style = MaterialTheme.typography.titleMedium, textDecoration = decoration)
        if (!row.cancelled) {
            TextButton(onClick = onCancel) { Text("Annuler") }
        }
    }
}
