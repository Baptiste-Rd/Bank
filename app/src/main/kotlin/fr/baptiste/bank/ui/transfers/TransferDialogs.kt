package fr.baptiste.bank.ui.transfers

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.ui.components.RowDivider

import fr.baptiste.bank.ui.components.BlurBehindWindow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import fr.baptiste.bank.domain.budget.monthLeft
import fr.baptiste.bank.domain.budget.monthLeftAfterTransfer
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.isProvision
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.ui.components.ChoiceField
import fr.baptiste.bank.ui.components.MoneyField
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.summary
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.BankTheme

private const val WALLET = "À répartir ce mois-ci"
private const val WALLET_ID = -1L
private const val RESERVE_ID = -2L

/** Nom d'une extrémité de transfert : une enveloppe, ou le portefeuille (null). */
private fun Dashboard.nameOf(envelopeId: Long?): String =
    envelopeId?.let { id -> envelopes.firstOrNull { it.envelope.id == id }?.envelope?.name } ?: WALLET

/**
 * Proposition de couverture d'un dépassement : rien n'est transféré avant validation.
 * Chaque part est affichée avec sa source, dans l'ordre où elle est sollicitée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverDialog(
    dashboard: Dashboard,
    plan: CoveragePlan,
    onDismiss: () -> Unit,
    onConfirm: (List<TransferLeg>) -> Unit,
) {
    val target = plan.target
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
            fr.baptiste.bank.ui.components.SheetHeader(
                title = target.envelope.name,
                onCancel = onDismiss,
                action = "Ajouter",
                enabled = plan.legs.isNotEmpty(),
                onAction = { onConfirm(plan.legs) },
            )
            Column(
                Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Ajouter ${(plan.needed - plan.shortfall).summary()}", style = MaterialTheme.typography.headlineSmall)
                // On couvre jusqu'à la fin de la période, pas seulement le constaté : sinon l'enveloppe
                // revient dans « À faire » dès le lendemain.
                // Une phrase : combien il manque, puis d'où vient l'argent.
                Text(
                    "Il manque ${plan.needed.summary()} pour finir ${target.period.label}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (plan.legs.isEmpty()) {
                    Text(
                        "Aucune enveloppe ne peut en donner pour l'instant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BankTheme.status.over,
                    )
                } else {
                    Text("Pris sur", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val before = dashboard.monthLeft
                    val after = plan.legs.fold(before) { left, leg ->
                        left + (dashboard.monthLeftAfterTransfer(leg.sourceEnvelopeId, target.envelope.id, leg.amount) - before)
                    }
                    Text(
                        if (after == before) "Ton reste du mois ne change pas." else "Reste du mois : ${before.summary()} → ${after.summary()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (after.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

/**
 * Transfert manuel vers une enveloppe ([incoming] = true) ou depuis elle.
 * L'autre extrémité se choisit parmi le portefeuille et les autres enveloppes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(
    dashboard: Dashboard,
    line: EnvelopeLine,
    incoming: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (counterpartId: Long?, amount: Money, reason: String, fromReserve: Boolean) -> Unit,
    /** Réserve : l'argent déjà sur le compte, hors budget du mois (null : inconnue ou vide). */
    reserve: Money? = null,
) {
    val others = dashboard.envelopes.filter { it.envelope.id != line.envelope.id }
    // Le portefeuille et la réserve sont représentés par des identifiants sans enveloppe.
    // La réserve n'est proposée qu'en apport : on y met de côté, on n'y rend rien.
    val withReserve = incoming && reserve != null && reserve.isPositive
    val options: List<Long> = listOfNotNull(RESERVE_ID.takeIf { withReserve }, WALLET_ID) + others.map { it.envelope.id }
    // Pour l'épargne et les provisions, la réserve d'abord : y mettre de côté ne touche pas au mois.
    val prefersReserve = withReserve && line.envelope.type != fr.baptiste.bank.domain.model.EnvelopeType.DAILY
    // La réserve peut arriver après l'ouverture (solde du compte en cours de lecture) : le choix
    // par défaut suit, tant que rien d'autre n'a été choisi.
    var counterpart by remember(prefersReserve) { mutableStateOf(if (prefersReserve) RESERVE_ID else WALLET_ID) }
    val fromReserve = counterpart == RESERVE_ID
    var amountText by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    val amount = parseMoney(amountText)

    fun optionLabel(id: Long): String {
        if (id == RESERVE_ID) return "Disponible sur le compte (${reserve?.formatted()})"
        if (id == WALLET_ID) {
            return dashboard.unallocated?.let { "$WALLET (${it.formatted()})" } ?: WALLET
        }
        val other = others.first { it.envelope.id == id }
        return "${other.envelope.name} (reste ${other.remaining.formatted()})"
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
            fr.baptiste.bank.ui.components.SheetHeader(
                title = if (incoming) "Ajouter à ${line.envelope.name}" else "Retirer de ${line.envelope.name}",
                onCancel = onDismiss,
                action = if (incoming) "Ajouter" else "Retirer",
                enabled = amount != null && amount.isPositive,
                onAction = {
                    if (amount != null) onConfirm(counterpart.takeIf { it != WALLET_ID && it != RESERVE_ID }, amount, reason, fromReserve)
                },
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
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
                // Ce que le transfert fait au reste du mois, avant de le valider : 1 000 € pris
                // sur un mois déjà dépensé ne doivent pas surprendre après coup.
                if (amount != null && amount.isPositive) {
                    val other = counterpart.takeIf { it != WALLET_ID && it != RESERVE_ID }
                    val source = if (incoming) other else line.envelope.id
                    val target = if (incoming) line.envelope.id else other
                    val before = dashboard.monthLeft
                    val after = dashboard.monthLeftAfterTransfer(source, target, amount, fromReserve)
                    if (fromReserve) {
                        Text(
                            if (reserve != null && amount > reserve) {
                                "C'est plus que ce qui est disponible (${reserve.formatted()}) : le compte ne contient pas tout ça hors budget du mois."
                            } else {
                                "Pris sur l'argent déjà sur le compte : ton budget du mois et ton épargne du mois ne changent pas."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (reserve != null && amount > reserve) BankTheme.status.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (after != before) {
                        Text(
                            "Ton reste du mois passera de ${before.formatted()} à ${after.formatted()}." +
                                if (after.isNegative) " Ce mois-ci, tu dépenserais plus que ton budget." else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (after.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
    }
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
    /** Accepte le rattrapage proposé (provision suivie depuis moins longtemps que sa période). */
    onCatchUp: ((java.time.YearMonth) -> Unit)? = null,
    /** Périodes enregistrées, la plus récente en premier : le budget de chacune se corrige ici. */
    periods: List<fr.baptiste.bank.data.BudgetRepository.PeriodLine> = emptyList(),
    onSetPeriodAmount: (key: String, amount: Money) -> Unit = { _, _ -> },
) {
    var editing by remember { mutableStateOf<fr.baptiste.bank.data.BudgetRepository.PeriodLine?>(null) }
    editing?.let { line ->
        var text by remember(line) { mutableStateOf(line.period.amount.cents.let { "${it / 100},${(it % 100).toString().padStart(2, '0')}" }) }
        val parsed = fr.baptiste.bank.domain.money.parseMoney(text)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Budget · ${line.period.toPeriod(fr.baptiste.bank.domain.period.Scale.MONTH).label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Corrige le budget de cette période seulement. Les autres ne bougent pas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    fr.baptiste.bank.ui.components.MoneyField(value = text, onValueChange = { text = it }, label = "Budget")
                }
            },
            confirmButton = {
                TextButton(enabled = parsed != null && !parsed.isNegative, onClick = {
                    parsed?.let { onSetPeriodAmount(line.period.key, it) }
                    editing = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Comme le prototype testé : le reste en grand, une ligne, une puce si elle dérape.
            var showHistory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.envelope.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Modifier") }
            }
            Text("Reste", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                line.remaining.summary(),
                style = MaterialTheme.typography.displaySmall,
                color = if (line.remaining.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
            )
            // Une provision dit ce qui est mis de côté ; les autres, ce qui est dépensé (une seule ligne).
            val isProvision = line.envelope.type.isProvision && line.envelope.type != fr.baptiste.bank.domain.model.EnvelopeType.PLANNED &&
                line.period.endInclusive.isAfter(line.period.start.plusMonths(1))
            Text(
                if (isProvision) "${line.funded.summary()} mis de côté sur ${line.allocated.summary()} · ${line.period.label}"
                else "${line.spent.summary()} sur ${line.available.summary()} · ${line.period.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                line.remaining.isNegative -> fr.baptiste.bank.ui.components.StatusChip("Dépassé ${(-line.remaining).summary()}", fr.baptiste.bank.ui.components.StatusKind.OVER)
                line.projection.isAlert -> fr.baptiste.bank.ui.components.StatusChip("Prévu +${line.projection.overrun.summary()}", fr.baptiste.bank.ui.components.StatusKind.WARNING)
                isProvision && line.catchUp != null -> fr.baptiste.bank.ui.components.StatusChip("En retard", fr.baptiste.bank.ui.components.StatusKind.WARNING)
            }
            if (onCover != null) {
                // « Couvrir » ne disait pas ce qu'il faisait : on ajoute de l'argent, pris sur l'épargne.
                fr.baptiste.bank.ui.components.PrimaryButton("Ajouter de l'argent", onClick = onCover, modifier = Modifier.fillMaxWidth())
            }
            // Provision en retard : l'action principale de la fiche.
            val proposal = line.catchUp
            if (isProvision && proposal != null && onCatchUp != null) {
                fr.baptiste.bank.ui.components.PrimaryButton("Mettre ${proposal.monthlyShare.summary()} de côté par mois", onClick = { onCatchUp(proposal.from) }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Ajouter") }
                OutlinedButton(onClick = onWithdraw, modifier = Modifier.weight(1f)) { Text("Retirer") }
            }
            // Les dépenses de la période : on voit d'où vient le chiffre, et on corrige un mauvais rangement.
            if (operations.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dépenses", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
                                    // Antérieure au départ : rangée ici, mais hors du reste (voir Envelope.start).
                                    if (line.envelope.countsOn(operation.date)) {
                                        operation.date.dayShort()
                                    } else {
                                        "${operation.date.dayShort()} · avant le départ, non comptée"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(Money(operation.amountCents).formatted(), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            // L'historique, replié : le détail du chiffre, les périodes passées, les ajouts et retraits.
            // « Historique » est réservé à la liste des annulations (Réglages) ; les dépenses de la
            // période sont déjà au-dessus : le repli garde le détail du chiffre et les périodes passées.
            TextButton(onClick = { showHistory = !showHistory }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(if (showHistory) "Masquer le détail" else "Détail du budget")
            }
            if (showHistory) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Figure("Budget", line.allocated)
                    Figure("Ajouts", line.transferred, signed = true)
                    Figure("Dépensé", line.spent)
                    Figure("Reste", line.remaining, emphasis = true)
                }
            }
            if (showHistory && periods.size > 1) {
                Text("Périodes", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    periods.forEachIndexed { index, row ->
                        if (index > 0) RowDivider(inset = 0.dp)
                        val left = row.period.amount - row.spent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = "Corriger le budget") { editing = row }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.period.toPeriod(line.envelope.frequency.scale).label.replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${row.spent.formatted()} sur ${row.period.amount.formatted()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                (if (left.isNegative) "−" + Money(-left.cents).formatted() else left.formatted()),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (left.isNegative) fr.baptiste.bank.ui.theme.BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            val mine = transfers.filter {
                !it.cancelled && it.date in line.period &&
                    (it.sourceEnvelopeId == line.envelope.id || it.targetEnvelopeId == line.envelope.id)
            }
            if (showHistory && mine.isNotEmpty()) {
                Text("Ajouts et retraits de la période", style = MaterialTheme.typography.labelLarge)
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
        title = { Text("Ajouts et retraits") },
        text = {
            BlurBehindWindow()
            if (transfers.isEmpty()) {
                Text(
                    "Aucun transfert. Depuis une enveloppe, Ajouter ou Retirer déplace de l'argent " +
                        "entre elle, ce qui est à répartir et les autres enveloppes.",
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

/** « 4 nov. » : l'année se devine, sauf pour une autre année que la courante. */
private fun java.time.LocalDate.dayShort(): String =
    format(java.time.format.DateTimeFormatter.ofPattern(if (year == java.time.LocalDate.now().year) "d MMM" else "d MMM yyyy", java.util.Locale.FRANCE))
