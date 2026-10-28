package fr.baptiste.bank.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.baptiste.bank.R
import fr.baptiste.bank.banking.BankViewModel
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.UiStyle
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.budget.coveragePlan
import fr.baptiste.bank.domain.budget.plannedPurchase
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.EnvelopeDialog
import fr.baptiste.bank.ui.components.ExpenseSheet
import fr.baptiste.bank.ui.components.IncomeDialog
import fr.baptiste.bank.ui.bank.BankDialog
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.ui.assistant.AssistedSortDialog
import fr.baptiste.bank.ui.accounts.AccountEditDialog
import fr.baptiste.bank.ui.accounts.AccountsDialog
import fr.baptiste.bank.ui.accounts.CurrentBalanceDialog
import fr.baptiste.bank.ui.accounts.SavingsGoalsDialog
import fr.baptiste.bank.domain.budget.HoldingLine
import fr.baptiste.bank.ui.operations.RenameOperationDialog
import fr.baptiste.bank.ui.operations.RulesDialog
import fr.baptiste.bank.ui.operations.SortOperationDialog
import fr.baptiste.bank.ui.operations.UnsortDialog
import fr.baptiste.bank.ui.transfers.CoverDialog
import fr.baptiste.bank.ui.transfers.EnvelopeSheet
import fr.baptiste.bank.ui.transfers.JournalDialog
import fr.baptiste.bank.ui.transfers.TransferDialog
import fr.baptiste.bank.update.UpdateDialog
import fr.baptiste.bank.update.UpdateUiState
import fr.baptiste.bank.update.UpdateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/*
 * Ce qui est commun aux deux styles d'interface (classique et Liquid Glass) : les onglets,
 * les fenêtres, les messages de confirmation et le menu ⋮. Chaque style ne fait que la mise en page.
 */

/** Quatre onglets, une question chacun (docs/refonte-ux.md). */
enum class Tab(val title: String, @DrawableRes val icon: Int) {
    /** Puis-je dépenser aujourd'hui ? */
    HOME("Aujourd'hui", R.drawable.ic_home),

    /** Où va mon salaire ? */
    BUDGET("Budget", R.drawable.ic_budget),

    /** Combien vais-je mettre de côté ? */
    SAVINGS("Épargne", R.drawable.ic_savings),

    /** Que s'est-il passé ? */
    OPERATIONS("Opérations", R.drawable.ic_list),
}

/** Les fenêtres ouvertes sont décrites par un état unique : une seule s'affiche à la fois. */
sealed interface OpenDialog {
    data object Expense : OpenDialog
    data object Income : OpenDialog
    data object Updates : OpenDialog
    data object Journal : OpenDialog
    /** [returnToSort] : création lancée depuis le tri d'une opération, où l'on revient ensuite. */
    data class EnvelopeEditor(val envelope: Envelope?, val returnToSort: OperationRow? = null) : OpenDialog
    data object Bank : OpenDialog
    data class SortOperation(val operation: OperationRow, val preselectedEnvelopeId: Long? = null) : OpenDialog
    data class RenameOperation(val operation: OperationRow) : OpenDialog
    data object Unsort : OpenDialog

    /** Livrets et investissements : la liste, puis un compte (null : nouveau). */
    data object Accounts : OpenDialog
    data object CurrentBalance : OpenDialog
    data object SavingsGoals : OpenDialog
    data class AccountEdit(val line: HoldingLine?) : OpenDialog

    /** Tri assisté par Claude ; [demoReply] ouvre directement la validation (captures). */
    data class AssistedSort(val demoReply: String? = null) : OpenDialog
    data object Rules : OpenDialog

    /** Les fenêtres liées à une enveloppe gardent son identifiant, pas sa ligne : elles suivent ainsi
     *  les derniers chiffres du tableau de bord pendant qu'elles sont ouvertes. */
    data class EnvelopeDetail(val envelopeId: Long) : OpenDialog
    data class Cover(val envelopeId: Long) : OpenDialog
    data class Move(val envelopeId: Long, val incoming: Boolean) : OpenDialog
}

/** État de navigation partagé et message de confirmation en bas de l'écran. */
@Stable
class AppController(
    initialTab: Tab,
    val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    var tab by mutableStateOf(initialTab)
    var dialog by mutableStateOf<OpenDialog?>(null)

    /** Ce que crée le bouton + : une dépense, une enveloppe (Budget) ou un achat prévu (Épargne). */
    val primaryAction: Pair<String, OpenDialog>
        get() = when (tab) {
            Tab.BUDGET -> "Enveloppe" to OpenDialog.EnvelopeEditor(null)
            Tab.SAVINGS -> "Achat prévu" to OpenDialog.EnvelopeEditor(
                plannedPurchase("", Money.ZERO, YearMonth.now().plusMonths(5), LocalDate.now()),
            )
            Tab.HOME, Tab.OPERATIONS -> "Dépense" to OpenDialog.Expense
        }

    fun notify(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = action,
                withDismissAction = action == null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

@Composable
fun rememberAppController(initialTab: Tab = Tab.HOME, initialDialog: OpenDialog? = null): AppController {
    var savedTab by rememberSaveable { mutableStateOf(initialTab) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val controller = remember { AppController(savedTab, snackbar, scope).also { it.dialog = initialDialog } }
    LaunchedEffect(controller.tab) { savedTab = controller.tab }
    return controller
}

/** Entrées du menu ⋮, identiques dans les deux styles. */
@Composable
fun AppMenuItems(
    controller: AppController,
    viewModel: BudgetViewModel,
    updateAvailable: Boolean,
    style: UiStyle,
    onStyleChange: (UiStyle) -> Unit,
    onClose: () -> Unit,
    /** Menu développeur : réglages du verre (style Liquid Glass seulement). */
    onOpenTuning: (() -> Unit)? = null,
) {
    val income by viewModel.monthlyIncome.collectAsStateWithLifecycle()
    DropdownMenuItem(
        text = { Text(income?.let { "Salaire mensuel : ${it.formatted()}" } ?: "Indiquer mon salaire") },
        onClick = {
            onClose()
            controller.dialog = OpenDialog.Income
        },
    )
    DropdownMenuItem(
        text = { Text("Banque") },
        onClick = {
            onClose()
            controller.dialog = OpenDialog.Bank
        },
    )
    DropdownMenuItem(
        text = { Text("Journal des transferts") },
        onClick = {
            onClose()
            controller.dialog = OpenDialog.Journal
        },
    )
    DropdownMenuItem(
        text = {
            Text(if (style == UiStyle.GLASS) "Passer au style classique" else "Passer au style Liquid Glass")
        },
        onClick = {
            onClose()
            onStyleChange(if (style == UiStyle.GLASS) UiStyle.CLASSIC else UiStyle.GLASS)
        },
    )
    if (onOpenTuning != null) {
        DropdownMenuItem(
            text = { Text("Réglages du verre (développeur)") },
            onClick = {
                onClose()
                onOpenTuning()
            },
        )
    }
    DropdownMenuItem(
        text = { Text(if (updateAvailable) "Nouvelle version disponible" else "Mises à jour") },
        trailingIcon = if (updateAvailable) {
            { Badge() }
        } else {
            null
        },
        onClick = {
            onClose()
            controller.dialog = OpenDialog.Updates
        },
    )
}

/** Toutes les fenêtres de l'app, ouvertes selon [AppController.dialog]. */
@Composable
fun AppDialogs(
    controller: AppController,
    viewModel: BudgetViewModel,
    updateViewModel: UpdateViewModel,
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val income by viewModel.monthlyIncome.collectAsStateWithLifecycle()
    val journal by viewModel.transferJournal.collectAsStateWithLifecycle()
    val archivedEnvelopes by viewModel.archivedEnvelopes.collectAsStateWithLifecycle()
    val holdingAccounts by viewModel.holdingAccounts.collectAsStateWithLifecycle()

    // Connexion bancaire : synchronisation discrète à l'ouverture, et son compte rendu en bas de l'écran.
    val bankViewModel: BankViewModel = viewModel(factory = BankViewModel.Factory)
    val bankMessage by bankViewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { bankViewModel.syncIfDue() }
    LaunchedEffect(bankMessage) {
        bankMessage?.let {
            controller.notify(it)
            bankViewModel.messageShown()
        }
    }

    fun close() {
        controller.dialog = null
    }
    fun nameOf(envelopeId: Long?): String =
        envelopeId?.let { id -> envelopes.firstOrNull { it.id == id }?.name } ?: "Non réparti"

    when (val current = controller.dialog) {
        null -> Unit
        OpenDialog.Expense -> ExpenseSheet(
            envelopes = envelopes,
            onDismiss = ::close,
            onCreateEnvelope = {
                controller.tab = Tab.BUDGET
                controller.dialog = OpenDialog.EnvelopeEditor(null)
            },
            onSave = { amount, date, label, envelope, wholePeriod ->
                viewModel.addExpense(amount, date, label, envelope, wholePeriod)
                controller.notify("Dépense de ${amount.formatted()} enregistrée dans ${envelope.name}.")
                close()
            },
        )
        OpenDialog.Income -> IncomeDialog(
            current = income,
            onDismiss = ::close,
            onSave = {
                viewModel.setMonthlyIncome(it)
                close()
            },
        )
        OpenDialog.Updates -> UpdateDialog(viewModel = updateViewModel, onDismiss = ::close)
        OpenDialog.Bank -> BankDialog(viewModel = bankViewModel, onDismiss = ::close)
        is OpenDialog.SortOperation -> SortOperationDialog(
            operation = current.operation,
            envelopes = envelopes,
            onSort = { envelope, keyword ->
                viewModel.sortOperation(current.operation.id, envelope, keyword) { others ->
                    controller.notify(
                        buildString {
                            append("Rangée dans ${envelope.name}.")
                            if (others > 0) append(" La règle a rangé $others autre(s) opération(s).")
                        },
                    )
                }
                close()
            },
            onIgnore = {
                viewModel.ignoreOperation(current.operation.id)
                controller.notify("Opération mise hors budget.")
                close()
            },
            onDismiss = ::close,
            onCreateEnvelope = { controller.dialog = OpenDialog.EnvelopeEditor(null, returnToSort = current.operation) },
            preselectedId = current.preselectedEnvelopeId,
            suggestedId = remember(current.operation.id) { viewModel.suggestedEnvelopeId(current.operation) }
                ?.takeIf { id -> envelopes.any { it.id == id } },
            onRename = { controller.dialog = OpenDialog.RenameOperation(current.operation) },
            accounts = holdingAccounts,
            onTransferToAccount = { accountId, keyword ->
                val name = holdingAccounts.firstOrNull { it.id == accountId }?.name.orEmpty()
                viewModel.markTransferToAccount(current.operation.id, accountId, keyword) { others ->
                    controller.notify(
                        buildString {
                            append("Virement vers $name : hors budget, compté dans ce que tu as de côté.")
                            if (others > 0) append(" $others autre(s) reconnu(s).")
                        },
                    )
                }
                close()
            },
        )
        is OpenDialog.RenameOperation -> RenameOperationDialog(
            operation = current.operation,
            onRename = { alias, pattern ->
                viewModel.renameOperation(current.operation.id, alias, pattern) { others ->
                    controller.notify(
                        buildString {
                            append(if (alias.isBlank()) "Libellé de la banque repris." else "Renommée « ${alias.trim()} ».")
                            if (others > 0) append(" $others autre(s) opération(s) renommée(s).")
                        },
                    )
                }
                // Retour au tri, avec le nouveau nom.
                val renamed = current.operation.copy(label = alias.trim().ifEmpty { current.operation.bankLabel })
                controller.dialog = OpenDialog.SortOperation(renamed)
            },
            onDismiss = { controller.dialog = OpenDialog.SortOperation(current.operation) },
        )
        OpenDialog.Unsort -> UnsortDialog(
            envelopes = envelopes,
            onUnsort = { scope ->
                viewModel.unsortMonth(scope) { count ->
                    if (count == 0) {
                        controller.notify("Aucune opération de la banque à déclasser.")
                    } else {
                        controller.notify("$count opération(s) à trier.", action = "Appliquer les règles") {
                            viewModel.applyRules { sorted -> controller.notify("$sorted opération(s) rangée(s) par les règles.") }
                        }
                    }
                }
                close()
            },
            onDismiss = ::close,
        )
        is OpenDialog.AssistedSort -> {
            val operations by viewModel.operations.collectAsStateWithLifecycle()
            val toSort = remember(operations) {
                operations.filter { it.envelopeNames == null && !it.ignored && it.source != TransactionSource.MANUAL }
            }
            AssistedSortDialog(
                toSort = toSort,
                envelopes = envelopes,
                initialReply = current.demoReply,
                accounts = holdingAccounts.map { it.name },
                onApply = { choices ->
                    viewModel.applyAssistedSort(choices) { result ->
                        controller.notify(
                            buildString {
                                append("${result.sorted} opération(s) rangée(s).")
                                if (result.createdEnvelopes.isNotEmpty()) {
                                    append(" Créée(s) à 0 € : ${result.createdEnvelopes.joinToString(", ")}, à budgéter.")
                                }
                            },
                        )
                    }
                    close()
                },
                onDismiss = ::close,
            )
        }
        OpenDialog.CurrentBalance -> {
            val balance by viewModel.currentAccountBalance.collectAsStateWithLifecycle()
            CurrentBalanceDialog(
                initial = balance?.balance,
                onSave = {
                    viewModel.setCurrentAccountBalance(it)
                    controller.notify("Solde enregistré.")
                    close()
                },
                onDismiss = ::close,
            )
        }
        OpenDialog.SavingsGoals -> {
            val goals by viewModel.savingsGoals.collectAsStateWithLifecycle()
            SavingsGoalsDialog(
                precaution = goals.precaution,
                total = goals.total,
                totalBy = goals.totalBy,
                onSave = { precaution, total, by ->
                    viewModel.setSavingsGoals(fr.baptiste.bank.domain.budget.SavingsGoals(precaution, total, by))
                    close()
                },
                onDismiss = ::close,
            )
        }
        OpenDialog.Accounts -> {
            val holdings by viewModel.holdings.collectAsStateWithLifecycle()
            AccountsDialog(
                lines = holdings?.lines.orEmpty(),
                onOpen = { controller.dialog = OpenDialog.AccountEdit(it) },
                onAdd = { controller.dialog = OpenDialog.AccountEdit(null) },
                onDismiss = ::close,
            )
        }
        is OpenDialog.AccountEdit -> AccountEditDialog(
            line = current.line,
            onSave = { name, type, balance ->
                val line = current.line
                if (line == null) {
                    viewModel.addManualAccount(name, type, balance ?: Money.ZERO)
                    controller.notify("${name.trim()} ajouté.")
                } else {
                    viewModel.saveAccount(line.account.id, name, type, balance)
                }
                controller.dialog = OpenDialog.Accounts
            },
            onArchive = current.line?.let { line ->
                {
                    viewModel.archiveAccount(line.account.id)
                    controller.notify("${line.account.name} retiré.")
                    controller.dialog = OpenDialog.Accounts
                }
            },
            onDismiss = { controller.dialog = OpenDialog.Accounts },
        )
        OpenDialog.Rules -> {
            val rules by viewModel.rules.collectAsStateWithLifecycle()
            val aliases by viewModel.aliases.collectAsStateWithLifecycle()
            val transferRules by viewModel.transferRules.collectAsStateWithLifecycle()
            RulesDialog(
                rules = rules,
                aliases = aliases,
                onDeleteRule = viewModel::deleteRule,
                onDeleteAlias = viewModel::deleteAlias,
                transferRules = transferRules,
                onDeleteTransferRule = viewModel::deleteTransferRule,
                onDismiss = ::close,
            )
        }
        OpenDialog.Journal -> JournalDialog(
            transfers = journal,
            onDismiss = ::close,
            onCancel = viewModel::cancelTransfer,
        )
        is OpenDialog.EnvelopeDetail -> {
            val line = dashboard?.envelopes?.firstOrNull { it.envelope.id == current.envelopeId }
            if (line == null) {
                // L'enveloppe a disparu (archivée) : on ferme la fenêtre, hors de la composition.
                // Pendant le premier chargement (tableau de bord pas encore prêt), on attend.
                if (dashboard != null) LaunchedEffect(current) { close() }
            } else {
                val periodOperations by remember(line.envelope.id, line.period.key) {
                    viewModel.envelopeOperations(line.envelope.id, line.period.key)
                }.collectAsStateWithLifecycle(null)
                // La fiche s'ouvre une fois ses dépenses chargées : sa hauteur est alors la bonne
                // (un panneau ouvert ne remonte pas quand son contenu grandit après coup).
                val loadedOperations = periodOperations
                if (loadedOperations != null) EnvelopeSheet(
                    line = line,
                    transfers = journal,
                    operations = loadedOperations,
                    onMoveOperation = { controller.dialog = OpenDialog.SortOperation(it) },
                    onDismiss = ::close,
                    onAdd = { controller.dialog = OpenDialog.Move(line.envelope.id, incoming = true) },
                    onWithdraw = { controller.dialog = OpenDialog.Move(line.envelope.id, incoming = false) },
                    onCover = if (line.needsCover) {
                        { controller.dialog = OpenDialog.Cover(line.envelope.id) }
                    } else {
                        null
                    },
                    onEdit = { controller.dialog = OpenDialog.EnvelopeEditor(line.envelope) },
                )
            }
        }
        is OpenDialog.Cover -> {
            val board = dashboard
            val line = board?.envelopes?.firstOrNull { it.envelope.id == current.envelopeId }
            val plan = line?.let { board.coveragePlan(it) }
            if (board == null || plan == null) {
                LaunchedEffect(current) { close() }
            } else {
                CoverDialog(
                    dashboard = board,
                    plan = plan,
                    onDismiss = ::close,
                    onConfirm = { legs ->
                        viewModel.addTransfers(legs, plan.target.envelope.id, incoming = true, reason = "Couverture")
                        // Dire d'où vient l'argent : un dépassement couvert ne disparaît pas, il est
                        // payé par le non réparti ou par d'autres enveloppes.
                        val sources = legs.joinToString(", ") { leg ->
                            "${leg.amount.formatted()} de ${leg.sourceEnvelopeId?.let(::nameOf) ?: "Non réparti"}"
                        }
                        // Pris sur le non réparti pour une enveloppe hors quotidien : le budget du quotidien
                        // baisse. Pour une enveloppe du quotidien, rien ne bouge : le dépassement le
                        // réduisait déjà, l'argent change seulement d'étiquette.
                        val fromWallet = legs.any { it.sourceEnvelopeId == null }
                        val daily = plan.target.envelope.type == fr.baptiste.bank.domain.model.EnvelopeType.DAILY
                        controller.notify(
                            "${plan.target.envelope.name} couverte : $sources." +
                                when {
                                    fromWallet && !daily -> " Le reste du jour et l'épargne prévue baissent d'autant."
                                    fromWallet -> " Le budget du quotidien ne change pas : il comptait déjà ce dépassement."
                                    else -> ""
                                },
                            action = "Journal",
                        ) { controller.dialog = OpenDialog.Journal }
                        close()
                    },
                )
            }
        }
        is OpenDialog.Move -> {
            val board = dashboard
            val line = board?.envelopes?.firstOrNull { it.envelope.id == current.envelopeId }
            if (board == null || line == null) {
                LaunchedEffect(current) { close() }
            } else {
                TransferDialog(
                    dashboard = board,
                    line = line,
                    incoming = current.incoming,
                    onDismiss = ::close,
                    onConfirm = { counterpart, amount, reason ->
                        viewModel.addTransfers(
                            legs = listOf(TransferLeg(counterpart, amount)),
                            envelopeId = line.envelope.id,
                            incoming = current.incoming,
                            reason = reason,
                        )
                        val from = if (current.incoming) nameOf(counterpart) else line.envelope.name
                        val to = if (current.incoming) line.envelope.name else nameOf(counterpart)
                        controller.notify("${amount.formatted()} déplacés de $from vers $to.")
                        close()
                    },
                )
            }
        }
        is OpenDialog.EnvelopeEditor -> EnvelopeDialog(
            initial = current.envelope,
            groups = envelopes.mapNotNull { it.group }.distinct().sorted(),
            takenNames = (envelopes + archivedEnvelopes)
                .filter { it.id != current.envelope?.id }
                .map { it.name },
            onDismiss = {
                val operation = current.returnToSort
                controller.dialog = if (operation != null) OpenDialog.SortOperation(operation) else null
            },
            onSave = { envelope ->
                val operation = current.returnToSort
                if (operation != null) {
                    // Retour au tri, la nouvelle enveloppe déjà choisie.
                    viewModel.saveEnvelope(envelope) { id -> controller.dialog = OpenDialog.SortOperation(operation, id) }
                } else {
                    viewModel.saveEnvelope(envelope)
                    close()
                }
            },
            onArchive = { archivedEnvelope ->
                viewModel.archiveEnvelope(archivedEnvelope.id)
                controller.notify("${archivedEnvelope.name} archivée.", action = "Annuler") {
                    viewModel.restoreEnvelope(archivedEnvelope.id)
                }
                close()
            },
        )
    }
}

/** Raccourci : la nouvelle version est-elle prête ? */
@Composable
fun rememberUpdateAvailable(updateViewModel: UpdateViewModel): Boolean {
    val state by updateViewModel.state.collectAsStateWithLifecycle()
    return state is UpdateUiState.Available
}
