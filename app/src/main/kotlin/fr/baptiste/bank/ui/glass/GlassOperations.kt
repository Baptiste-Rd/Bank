package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.clickable
import fr.baptiste.bank.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.banking.PendingOperation
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.envelopes.CategoryAvatar
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.operations.dayTitle
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.ui.theme.envelopeTypeColor
import java.time.LocalDate

/*
 * Onglet Opérations : « que s'est-il passé ? ».
 * 1. À trier, en tête et seulement s'il y en a : les opérations de la banque rangées nulle part.
 * 2. L'historique par jour, une carte par jour, lignes séparées par un filet.
 * Toucher une saisie manuelle propose de la supprimer ; toucher une opération de la banque
 * permet de la ranger ou de la reclasser.
 */

@Composable
fun GlassOperationsContent(
    operations: List<OperationRow>,
    envelopes: List<Envelope>,
    onDelete: (Long) -> Unit,
    onSort: (OperationRow) -> Unit,
    onOpenBank: () -> Unit,
    /** Toutes les opérations à trier (la file « À valider »), pas seulement les récentes. */
    toValidate: List<OperationRow> = emptyList(),
    /** Ce que l'app propose pour une opération à trier (null : rien à proposer). */
    proposal: (OperationRow) -> SortProposal? = { null },
    /** Valide la proposition d'une opération, d'un geste. */
    onAccept: (OperationRow, SortProposal) -> Unit = { _, _ -> },
    /** Valide toutes les propositions. */
    onAcceptAll: (List<Pair<OperationRow, SortProposal>>) -> Unit = {},
    /** Range ce qui est à trier avec les règles existantes. */
    onApplyRules: () -> Unit = {},
    onUnsort: () -> Unit = {},
    onOpenRules: () -> Unit = {},
    rulesSummary: String? = null,
    /** Tri assisté par Claude des opérations à trier. */
    onAssistedSort: () -> Unit = {},
    /** Opérations en attente à la banque : montrées, pas encore importées. */
    pending: List<PendingOperation> = emptyList(),
    /** En-tête de l'écran (titre, bascule Analyse | Liste) ; par défaut le titre « Opérations ». */
    header: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
) {
    var pendingDelete by remember { mutableStateOf<OperationRow?>(null) }
    val toSortColor = BankTheme.status.warning
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val today = LocalDate.now()
    val typeByName = remember(envelopes) { envelopes.associate { it.name to it.type } }
    val sorted = operations.filterNot {
        it.envelopeNames == null && !it.ignored && it.source != TransactionSource.MANUAL
    }
    val toSort = toValidate
    // Une proposition par opération, calculée une fois par liste.
    val proposals = remember(toValidate, proposal) { toValidate.associate { it.id to proposal(it) } }
    val ready = toValidate.mapNotNull { operation -> proposals[operation.id]?.let { operation to it } }

    fun colorOf(operation: OperationRow): Color =
        operation.envelopeNames?.split(", ")?.firstOrNull()?.let { typeByName[it] }?.let(::envelopeTypeColor) ?: quiet

    fun clickOf(operation: OperationRow): () -> Unit =
        if (operation.source == TransactionSource.MANUAL) {
            { pendingDelete = operation }
        } else {
            { onSort(operation) }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header != null) header() else largeTitle("Opérations")
        if (operations.isEmpty()) {
            item(key = "empty") {
                GlassCard(strong = true) {
                    Text("Aucune opération", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tes saisies et les opérations de ta banque apparaîtront ici.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = quiet,
                    )
                    TextButton(onClick = onOpenBank) { Text("Connecter ma banque") }
                }
            }
        }
        // À valider : chaque opération arrive avec sa proposition ; une coche la valide, toucher la
        // ligne permet de corriger. « Tout valider » prend toutes les propositions d'un coup.
        if (toSort.isNotEmpty()) {
            sectionHeader(
                "to-sort-label",
                "À trier · ${toSort.size}",
                dot = toSortColor,
                action = if (ready.size > 1) "Tout valider (${ready.size})" else null,
                onAction = { onAcceptAll(ready) },
            )
            item(key = "to-sort") {
                GlassRowsCard(toSort.take(QUEUE_LIMIT)) { operation ->
                    QueueRow(
                        operation = operation,
                        proposal = proposals[operation.id],
                        onAccept = { proposals[operation.id]?.let { onAccept(operation, it) } },
                        onOpen = clickOf(operation),
                    )
                }
            }
            if (toSort.size > QUEUE_LIMIT) {
                item(key = "to-sort-more") {
                    Text(
                        "Et ${toSort.size - QUEUE_LIMIT} plus anciennes : valide celles-ci, les suivantes apparaissent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = quiet,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        // Le tri se règle ici : ce que l'app range seule (règles, alias) et le retour en arrière.
        if (operations.any { it.source != TransactionSource.MANUAL }) {
            item(key = "sort-tools") {
                GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
                    if (toSort.size > ready.size) {
                        ToolRow(
                            "Trier avec Claude",
                            "${toSort.size - ready.size} sans proposition : Claude identifie et propose",
                            onAssistedSort,
                        )
                        RowDivider(0.dp)
                    }
                    if (toSort.isNotEmpty()) {
                        ToolRow("Appliquer les règles", "Ranger ce que tes règles reconnaissent", onApplyRules)
                        RowDivider(0.dp)
                    }
                    ToolRow("Règles et alias", rulesSummary, onOpenRules)
                    RowDivider(0.dp)
                    ToolRow("Déclasser ce mois-ci", "Tout, une enveloppe ou un libellé", onUnsort)
                }
            }
        }
        // Pas encore comptabilisées (un retrait du dimanche, un paiement d'hier) : visibles tout de
        // suite, elles seront importées et à trier quand la banque les aura passées.
        if (pending.isNotEmpty()) {
            sectionHeader("pending-label", "En attente à la banque · ${pending.size}", trailing = Money(pending.sumOf { it.amountCents }).formatted())
            item(key = "pending") {
                GlassRowsCard(pending.sortedByDescending { it.date }) { operation ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar("…", quiet)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(operation.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${dayTitle(operation.date, today).lowercase()} · pas encore comptabilisée",
                                style = MaterialTheme.typography.bodySmall,
                                color = quiet,
                            )
                        }
                        Text(Money(operation.amountCents).formatted(), style = MaterialTheme.typography.titleMedium, color = quiet)
                    }
                }
            }
        }
        sorted.groupBy { it.date }.forEach { (date, dayOperations) ->
            sectionHeader("label-$date", dayTitle(date, today), trailing = Money(dayOperations.sumOf { it.amountCents }).formatted())
            item(key = "day-$date") {
                GlassRowsCard(dayOperations) { operation ->
                    GlassOperationRow(operation, colorOf(operation), onClick = clickOf(operation))
                }
            }
        }
    }

    pendingDelete?.let { operation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer cette dépense ?") },
            text = {
                BlurBehindWindow()
                Text("${operation.label}, ${Money(-operation.amountCents).formatted()}")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(operation.id)
                    pendingDelete = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Annuler") } },
        )
    }
}

/** Opérations montrées à la fois dans la file : au-delà, la liste deviendrait une corvée. */
private const val QUEUE_LIMIT = 30

/** Ce que l'app propose pour une opération à trier : une enveloppe, ou hors budget ([envelope] null). */
data class SortProposal(val envelope: Envelope?, val label: String)

/**
 * Une opération à valider : sa proposition sous le libellé, une coche pour l'accepter. Sans
 * proposition, la ligne mène au choix. Toucher la ligne permet toujours de corriger.
 */
@Composable
private fun QueueRow(operation: OperationRow, proposal: SortProposal?, onAccept: () -> Unit, onOpen: () -> Unit) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Choisir l'enveloppe", onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                operation.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    proposal?.label ?: "À choisir",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (proposal != null) MaterialTheme.colorScheme.onSurface else BankTheme.status.warning,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    " · ${dayTitle(operation.date, LocalDate.now()).lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = quiet,
                    maxLines = 1,
                )
            }
        }
        Text(
            Money(operation.amountCents).formatted(),
            style = MaterialTheme.typography.titleMedium,
            color = if (operation.amountCents > 0) BankTheme.status.gain else MaterialTheme.colorScheme.onSurface,
        )
        if (proposal != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
                    .clickable(onClickLabel = "Valider : ${proposal.label}", role = Role.Button, onClick = onAccept),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = "Valider : ${proposal.label}",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Chevron()
        }
    }
}

/** Ligne d'outil : titre, détail, chevron. */
@Composable
private fun ToolRow(title: String, detail: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Chevron()
    }
}

/** Une opération : pastille, libellé et une ligne de détail, montant à droite. */
@Composable
private fun GlassOperationRow(
    operation: OperationRow,
    color: Color,
    onClick: () -> Unit,
    showDate: Boolean = false,
    suggestion: String? = null,
) {
    val manual = operation.source == TransactionSource.MANUAL
    val detail = buildList {
        add(
            operation.envelopeNames
                ?: operation.transferAccount?.let { if (operation.amountCents < 0) "Vers $it" else "Depuis $it" }
                ?: if (operation.ignored) "Hors budget" else suggestion?.let { "Proposé : $it" } ?: "À trier",
        )
        if (showDate) add(dayTitle(operation.date, LocalDate.now()).lowercase())
        if (operation.wholePeriod) {
            val period = operation.periodKey?.let { runCatching { Period.parse(it) }.getOrNull() }
            add(if (period != null) "rattrapage ${period.label}" else "rattrapage")
        } else if (manual) {
            add("saisie")
        }
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (manual) "Supprimer" else "Ranger", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryAvatar(operation.envelopeNames ?: if (operation.ignored) "–" else "?", color)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                operation.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            Money(operation.amountCents).formatted(),
            style = MaterialTheme.typography.titleMedium,
            color = if (operation.amountCents > 0) BankTheme.status.gain else MaterialTheme.colorScheme.onSurface,
        )
    }
}
