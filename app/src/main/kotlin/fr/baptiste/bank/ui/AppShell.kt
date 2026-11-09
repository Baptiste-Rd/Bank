package fr.baptiste.bank.ui

import fr.baptiste.bank.domain.budget.perDayWithReserved
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
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.budget.coveragePlan
import fr.baptiste.bank.domain.budget.plannedPurchase
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.EnvelopeDialog
import fr.baptiste.bank.ui.components.ExpenseSheet
import fr.baptiste.bank.ui.components.SalaryDialog
import fr.baptiste.bank.ui.bank.BankDialog
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.format.summary
import fr.baptiste.bank.ui.assistant.AssistedSortDialog
import fr.baptiste.bank.ui.accounts.AccountEditDialog
import fr.baptiste.bank.ui.plan.PlanSimulatorSheet
import fr.baptiste.bank.ui.accounts.AccountsDialog
import fr.baptiste.bank.ui.accounts.CurrentBalanceDialog
import fr.baptiste.bank.ui.components.FinishLumpDialog
import fr.baptiste.bank.ui.components.PlaceSavingsDialog
import fr.baptiste.bank.domain.budget.HoldingLine
import fr.baptiste.bank.ui.operations.RenameOperationDialog
import fr.baptiste.bank.ui.operations.RulesDialog
import fr.baptiste.bank.ui.operations.SortOperationDialog
import fr.baptiste.bank.ui.operations.DateCheckDialog
import fr.baptiste.bank.ui.operations.SplitOperationDialog
import fr.baptiste.bank.ui.assistant.ClaudeControlDialog
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
    /** Où j'en suis ce mois ? */
    HOME("Résumé", R.drawable.ic_home),

    /** Où va mon salaire ? */
    BUDGET("Budget", R.drawable.ic_budget),

    /** Qu'ai-je dépensé ? */
    OPERATIONS("Dépenses", R.drawable.ic_list),

    /** Combien j'épargne ? */
    SAVINGS("Épargne", R.drawable.ic_savings),
}

/** Les fenêtres ouvertes sont décrites par un état unique : une seule s'affiche à la fois. */
sealed interface OpenDialog {
    /** Une fenêtre sur une enveloppe connue par son nom (captures de la démo) : ouverte une fois chargée. */
    data class NamedEnvelope(val name: String, val open: (Long) -> OpenDialog) : OpenDialog
    data object Expense : OpenDialog
    data object Income : OpenDialog
    data object Updates : OpenDialog
    data object Journal : OpenDialog
    /** Les dix derniers changements, annulables. */
    data object History : OpenDialog
    /** Le tri carte par carte des opérations à trier. */
    data object SortCards : OpenDialog
    /** [returnToSort] : création lancée depuis le tri d'une opération, où l'on revient ensuite. */
    data class EnvelopeEditor(val envelope: Envelope?, val returnToSort: OperationRow? = null) : OpenDialog
    data object Bank : OpenDialog
    data class SortOperation(val operation: OperationRow, val preselectedEnvelopeId: Long? = null) : OpenDialog
    data class RenameOperation(val operation: OperationRow, val selectedEnvelopeId: Long? = null) : OpenDialog
    data class SplitOperation(val operation: OperationRow, val selectedEnvelopeId: Long? = null) : OpenDialog
    data object Unsort : OpenDialog

    /** « Vérifier les dates » : débit de la carte, jours à corriger. */
    data object DateCheck : OpenDialog

    /** Contrôle avec Claude : export anonymisé, puis plan de corrections à valider. */
    data class ClaudeControl(val demoReply: String? = null) : OpenDialog

    /** Livrets et investissements : la liste, puis un compte (null : nouveau). */
    data object Accounts : OpenDialog
    data object CurrentBalance : OpenDialog
    /** Répartir dans les objectifs ; [start] : la répartition de départ des livrets. */
    data class PlaceSavings(val start: Boolean) : OpenDialog

    /** Terminer une somme mise à part. */
    data class FinishLump(val envelopeId: Long) : OpenDialog
    data class AccountEdit(val line: HoldingLine?) : OpenDialog

    /** Essayer un plan (projet, épargne) sur le plan du mois, sans rien changer. */
    data class TryPlan(val name: String = "", val project: Money? = null) : OpenDialog

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

/** Toutes les fenêtres de l'app, ouvertes selon [AppController.dialog]. */
@Composable
fun AppDialogs(
    controller: AppController,
    viewModel: BudgetViewModel,
    updateViewModel: UpdateViewModel,
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
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
        envelopeId?.let { id -> envelopes.firstOrNull { it.id == id }?.name } ?: "À répartir ce mois-ci"

    when (val current = controller.dialog) {
        null -> Unit
        is OpenDialog.NamedEnvelope -> {
            val id = envelopes.firstOrNull { it.name == current.name }?.id
            if (id != null) LaunchedEffect(current) { controller.dialog = current.open(id) }
        }
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
        OpenDialog.Income -> {
            val candidates by viewModel.salaryCandidates.collectAsStateWithLifecycle()
            val salary by viewModel.salary.collectAsStateWithLifecycle()
            val monthIncome by viewModel.monthIncome.collectAsStateWithLifecycle()
            val typed by viewModel.monthlyIncome.collectAsStateWithLifecycle()
            SalaryDialog(
                candidates = candidates,
                salary = salary,
                income = monthIncome,
                typed = typed.takeIf { salary == null },
                onConfirm = { candidate, next ->
                    viewModel.confirmSalary(candidate, next)
                    controller.notify("Salaire lu à la banque : plus rien à taper.")
                },
                onNextMonthChange = viewModel::setSalaryNextMonth,
                onForget = { viewModel.confirmSalary(null, true) },
                onDismiss = ::close,
            )
        }
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
                        "Annuler",
                    ) { viewModel.undoLast() }
                }
                close()
            },
            onIgnore = {
                viewModel.ignoreOperation(current.operation.id)
                controller.notify("Opération mise hors budget.", "Annuler") { viewModel.undoLast() }
                close()
            },
            onDismiss = ::close,
            onCreateEnvelope = { controller.dialog = OpenDialog.EnvelopeEditor(null, returnToSort = current.operation) },
            preselectedId = current.preselectedEnvelopeId,
            suggestedId = remember(current.operation.id) { viewModel.suggestedEnvelopeId(current.operation) }
                ?.takeIf { id -> envelopes.any { it.id == id } },
            onRename = { selected -> controller.dialog = OpenDialog.RenameOperation(current.operation, selected) },
            onSplit = { selected -> controller.dialog = OpenDialog.SplitOperation(current.operation, selected) },
            onUnsort = {
                viewModel.unsortOperations(listOf(current.operation.id))
                controller.notify("${current.operation.label} : remise à trier.", "Annuler") { viewModel.undoLast() }
                close()
            },
            onDelete = {
                viewModel.deleteExpense(current.operation.id)
                controller.notify("${current.operation.label} supprimée.", "Annuler") { viewModel.undoLast() }
                close()
            },
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
        is OpenDialog.SplitOperation -> SplitOperationDialog(
            label = current.operation.label,
            load = { viewModel.operationSplit(current.operation.id) },
            envelopes = envelopes,
            preselectedId = current.selectedEnvelopeId,
            onSave = { parts ->
                viewModel.splitOperation(current.operation.id, parts) { done ->
                    if (done) {
                        controller.notify(parts.joinToString(" · ") { (envelope, amount) -> "${amount.formatted()} dans ${envelope.name}" }, "Annuler") { viewModel.undoLast() }
                    } else {
                        controller.notify("La répartition ne correspond pas au total.")
                    }
                }
                close()
            },
            onDismiss = { controller.dialog = OpenDialog.SortOperation(current.operation, current.selectedEnvelopeId) },
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
                // L'enveloppe choisie avant de renommer est gardée : il ne reste qu'à valider.
                val renamed = current.operation.copy(label = alias.trim().ifEmpty { current.operation.bankLabel })
                controller.dialog = OpenDialog.SortOperation(renamed, current.selectedEnvelopeId)
            },
            onDismiss = { controller.dialog = OpenDialog.SortOperation(current.operation, current.selectedEnvelopeId) },
        )
        is OpenDialog.ClaudeControl -> ClaudeControlDialog(
            onExport = viewModel::exportForClaude,
            parse = viewModel::parseClaudePlan,
            loadContext = viewModel::claudeContext,
            onApply = { actions ->
                viewModel.applyClaudePlan(actions) { count -> controller.notify("$count action(s) de Claude appliquée(s).") }
                close()
            },
            onDismiss = ::close,
            initialReply = current.demoReply,
            unsortedCount = viewModel.unsortedOperations.collectAsStateWithLifecycle().value.size,
            onAssistedSort = { controller.dialog = OpenDialog.AssistedSort() },
        )
        OpenDialog.DateCheck -> {
            val report by viewModel.dateCheck.collectAsStateWithLifecycle()
            DateCheckDialog(
                report = report,
                onApply = { corrections ->
                    viewModel.applyDateCorrections(corrections) { count ->
                        controller.notify("$count date(s) corrigée(s). Les dépenses ont suivi leur nouveau jour.")
                    }
                    close()
                },
                onDismiss = ::close,
            )
        }
        OpenDialog.Unsort -> UnsortDialog(
            envelopes = envelopes,
            month = viewModel.selectedMonth.collectAsStateWithLifecycle().value,
            onUnsort = { scope ->
                viewModel.unsortMonth(scope) { count ->
                    if (count == 0) {
                        controller.notify("Aucune opération de la banque à remettre à trier.")
                    } else {
                        controller.notify("$count opération(s) remise(s) à trier.", action = "Appliquer les règles") {
                            viewModel.applyRules { sorted -> controller.notify("$sorted opération(s) rangée(s) par les règles.") }
                        }
                    }
                }
                close()
            },
            onDismiss = ::close,
        )
        is OpenDialog.AssistedSort -> {
            // La même liste que « À trier » : toutes les opérations à trier, pas seulement les 200 dernières
            // (après la récupération de l'historique, les plus anciennes en étaient absentes : écran vide).
            val toSort by viewModel.unsortedOperations.collectAsStateWithLifecycle()
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
        is OpenDialog.FinishLump -> {
            val line = dashboard?.envelopes?.firstOrNull { it.envelope.id == current.envelopeId }
            val goals = viewModel.savingsState.collectAsStateWithLifecycle().value?.goals.orEmpty()
                .filter { !it.envelope.archived }.map { it.envelope.id to it.envelope.name }
            if (line != null) {
                FinishLumpDialog(
                    name = line.envelope.name,
                    left = line.remaining,
                    goals = goals,
                    onDismiss = ::close,
                    onFinish = { goalId ->
                        viewModel.finishLump(line.envelope.id, goalId)
                        val left = line.remaining
                        val message = if (left.isPositive && goalId != null) {
                            "${line.envelope.name} terminée. Vire ${left.summary()} sur ton livret."
                        } else {
                            "${line.envelope.name} terminée."
                        }
                        controller.notify(message, action = "Annuler") { viewModel.undoLast() }
                        close()
                    },
                )
            }
        }
        is OpenDialog.PlaceSavings -> {
            val state = viewModel.savingsState.collectAsStateWithLifecycle().value
            if (state != null) {
                // Refaire la répartition peut porter sur plus que les livrets (placements, solde pas à jour).
                val held = state.goals.fold(Money.ZERO) { sum, it -> sum + it.held }
                val total = when {
                    current.start && (state.started || held != Money.ZERO) -> maxOf(state.livrets, held)
                    current.start -> state.livrets
                    else -> state.toPlace
                }
                PlaceSavingsDialog(
                    title = if (current.start) "Répartir tes livrets" else if (total.isNegative) "Retirer des objectifs" else "Ranger ${total.summary()}",
                    detail = if (current.start) {
                        "Ce que chaque objectif contient aujourd'hui."
                    } else {
                        "Ce qui est arrivé sur tes comptes, rangé dans tes objectifs."
                    },
                    goals = state.goals,
                    total = total,
                    // Refaire la répartition part de ce que contient chaque objectif aujourd'hui.
                    proposal = if (current.start && (state.started || held != Money.ZERO)) state.goals.associate { it.envelope.id to it.held }
                    else if (current.start) fr.baptiste.bank.domain.budget.fillInOrder(state.goals, total) else fr.baptiste.bank.domain.budget.proposeSplit(state.goals, state.received, total),
                    action = if (current.start) "Répartir" else if (total.isNegative) "Retirer" else "Ranger",
                    onDismiss = ::close,
                    onConfirm = { parts ->
                        viewModel.placeSavings(parts, current.start)
                        controller.notify("Objectifs mis à jour.", action = "Annuler") { viewModel.undoLast() }
                        close()
                    },
                )
            }
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
        is OpenDialog.TryPlan -> {
            val history by viewModel.dailySpendingHistory.collectAsStateWithLifecycle()
            val board = dashboard
            val loaded = history
            if (board != null && loaded != null) {
                PlanSimulatorSheet(
                    dashboard = board,
                    history = loaded,
                    initialName = current.name,
                    initialProject = current.project,
                    savingsEditable = envelopes.count { it.type == fr.baptiste.bank.domain.model.EnvelopeType.SAVINGS } == 1,
                    onApply = { name, project, months, savings, reductions ->
                        viewModel.applyPlan(name, project, months, savings, reductions)
                        controller.notify(
                            when {
                                project.isPositive && name.isNotBlank() -> "${name.trim()} : achat prévu créé."
                                reductions.isNotEmpty() -> "Enveloppes réduites pour $months mois."
                                else -> "Épargne mensuelle mise à jour."
                            },
                        )
                        close()
                    },
                    onDismiss = ::close,
                )
            }
        }
        is OpenDialog.AccountEdit -> AccountEditDialog(
            line = current.line,
            onSave = { name, type, balance, investment ->
                val line = current.line
                if (line == null) {
                    viewModel.addManualAccount(name, type, balance ?: Money.ZERO, investment)
                    controller.notify("${name.trim()} ajouté.")
                } else {
                    viewModel.saveAccount(line.account.id, name, type, balance, investment)
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
        OpenDialog.SortCards -> {
            val toSort by viewModel.unsortedOperations.collectAsStateWithLifecycle()
            fun proposalFor(operation: fr.baptiste.bank.data.local.OperationRow) =
                viewModel.suggestedEnvelopeId(operation)
                    ?.let { id -> envelopes.firstOrNull { it.id == id } }
                    ?.let { fr.baptiste.bank.ui.glass.SortProposal(it, it.name) }
                    ?: if (operation.amountCents > 0) fr.baptiste.bank.ui.glass.SortProposal(null, "Hors budget") else null
            fr.baptiste.bank.ui.operations.SortCardsSheet(
                operations = toSort,
                proposal = ::proposalFor,
                // Raccourcis : le quotidien d'abord (les dépenses à trier y vont le plus souvent).
                alternatives = { _, suggested ->
                    envelopes.filter { it.id != suggested?.id && it.type != fr.baptiste.bank.domain.model.EnvelopeType.SAVINGS }
                        .sortedBy { if (it.type == fr.baptiste.bank.domain.model.EnvelopeType.DAILY) 0 else 1 }
                        .take(3)
                },
                onAccept = { operation, proposal ->
                    viewModel.acceptProposals(listOf(operation.id to proposal.envelope))
                    controller.notify("${operation.label} → ${proposal.label}", "Annuler") { viewModel.undoLast() }
                },
                onChoose = { operation, envelope ->
                    viewModel.acceptProposals(listOf(operation.id to envelope))
                    // Un choix à la main : proposer d'en faire la règle des prochaines.
                    controller.notify("Les prochaines « ${operation.label} » dans ${envelope.name} ?", "Oui") {
                        viewModel.sortOperation(operation.id, envelope, fr.baptiste.bank.domain.bank.suggestedRuleKeyword(operation.bankLabel)) { others ->
                            controller.notify(if (others > 0) "Règle créée, $others autre(s) rangée(s)." else "Règle créée.")
                        }
                    }
                },
                onSplit = { operation -> controller.dialog = OpenDialog.SplitOperation(operation, viewModel.suggestedEnvelopeId(operation)) },
                onOther = { operation -> controller.dialog = OpenDialog.SortOperation(operation) },
                onDismiss = ::close,
            )
        }
        OpenDialog.History -> {
            val entries by viewModel.journal.collectAsStateWithLifecycle()
            fr.baptiste.bank.ui.glass.HistorySheet(
                entries = entries,
                onUndo = { entry ->
                    viewModel.undo(entry.id) { label -> if (label != null) controller.notify("Annulé : $label") }
                },
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
                val periods by remember(line.envelope.id) { viewModel.envelopePeriods(line.envelope.id) }
                    .collectAsStateWithLifecycle(emptyList())
                val loadedOperations = periodOperations
                if (loadedOperations != null) EnvelopeSheet(
                    periods = periods,
                    onSetPeriodAmount = { key, amount -> viewModel.setPeriodAmount(line.envelope.id, key, amount) },
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
                    // Le formulaire part du montant saisi, pas de la part propre vue par les calculs.
                    onEdit = { controller.dialog = OpenDialog.EnvelopeEditor((viewModel.envelopes.value + viewModel.archivedEnvelopes.value).firstOrNull { it.id == line.envelope.id } ?: line.envelope) },
                    onFinish = if (line.envelope.type == fr.baptiste.bank.domain.model.EnvelopeType.LUMP) {
                        { controller.dialog = OpenDialog.FinishLump(line.envelope.id) }
                    } else {
                        null
                    },
                    onCatchUp = { from ->
                        viewModel.startFunding(line.envelope.id, from)
                        val share = line.catchUp?.monthlyShare?.summary()
                        controller.notify(
                            if (share != null) "${line.envelope.name} : $share de côté par mois à partir de ${from.label}." else "${line.envelope.name} : rattrapage à partir de ${from.label}.",
                            "Annuler",
                        ) { viewModel.undoLast() }
                    },
                )
            }
        }
        is OpenDialog.Cover -> {
            val reserve by viewModel.currentAccount.collectAsStateWithLifecycle()
            val board = dashboard
            val line = board?.envelopes?.firstOrNull { it.envelope.id == current.envelopeId }
            val plan = line?.let { board.coveragePlan(it) }
            if (board == null || plan == null) {
                // Pendant le premier chargement, on attend ; plus rien à couvrir ferme la fenêtre.
                if (board != null) LaunchedEffect(current) { close() }
            } else {
                CoverDialog(
                    dashboard = board,
                    target = line,
                    reserve = reserve?.free?.takeIf { it.isPositive },
                    onDismiss = ::close,
                    onConfirm = { legs ->
                        viewModel.addTransfers(legs, plan.target.envelope.id, incoming = true, reason = "Couverture")
                        // Dire d'où vient l'argent : un dépassement couvert ne disparaît pas, il est
                        // payé par le non réparti, par le compte ou par d'autres enveloppes.
                        val sources = legs.joinToString(", ") { leg ->
                            "${leg.amount.formatted()} de ${leg.sourceEnvelopeId?.let(::nameOf) ?: if (leg.fromReserve) "Disponible sur le compte" else "À répartir ce mois-ci"}"
                        }
                        // Pris sur le non réparti pour une enveloppe hors quotidien : le budget du quotidien
                        // baisse. Pour une enveloppe du quotidien, rien ne bouge : le dépassement le
                        // réduisait déjà, l'argent change seulement d'étiquette.
                        val fromWallet = legs.any { it.sourceEnvelopeId == null && !it.fromReserve }
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
            val reserve by viewModel.currentAccount.collectAsStateWithLifecycle()
            val board = dashboard
            val line = board?.envelopes?.firstOrNull { it.envelope.id == current.envelopeId }
            if (board == null || line == null) {
                // Pendant le premier chargement, on attend ; une enveloppe disparue ferme la fenêtre.
                if (board != null) LaunchedEffect(current) { close() }
            } else {
                TransferDialog(
                    dashboard = board,
                    line = line,
                    incoming = current.incoming,
                    onDismiss = ::close,
                    reserve = reserve?.free?.takeIf { it.isPositive },
                    onConfirm = { counterpart, amount, reason, fromReserve ->
                        if (fromReserve) {
                            viewModel.addReserveTransfer(line.envelope.id, amount, reason)
                        } else {
                            viewModel.addTransfers(
                                legs = listOf(TransferLeg(counterpart, amount)),
                                envelopeId = line.envelope.id,
                                incoming = current.incoming,
                                reason = reason,
                            )
                        }
                        val from = if (fromReserve) "Disponible sur le compte" else if (current.incoming) nameOf(counterpart) else line.envelope.name
                        val to = if (current.incoming) line.envelope.name else nameOf(counterpart)
                        controller.notify("${amount.formatted()} déplacés de $from vers $to.")
                        close()
                    },
                )
            }
        }
        is OpenDialog.EnvelopeEditor -> EnvelopeDialog(
            initial = current.envelope,
            // Toutes les catégories connues (enregistrées, même vides, et portées par une enveloppe), en arbre.
            groups = (viewModel.categories.collectAsStateWithLifecycle().value.map { it.name } + envelopes.mapNotNull { it.group }).distinct(),
            categoryParents = viewModel.categoryParents.collectAsStateWithLifecycle().value,
            trackingStart = viewModel.trackingStart.collectAsStateWithLifecycle().value,
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
                // Un seul chemin pour annuler : l'Historique (le message en défait la dernière entrée).
                controller.notify("${archivedEnvelope.name} arrêtée.", action = "Annuler") { viewModel.undoLast() }
                close()
            },
            held = viewModel.savingsState.collectAsStateWithLifecycle().value?.goals?.firstOrNull { it.envelope.id == current.envelope?.id }?.held ?: Money.ZERO,
            accounts = viewModel.holdings.collectAsStateWithLifecycle().value?.lines.orEmpty().map { it.account.id to it.account.name },
            goals = viewModel.savingsState.collectAsStateWithLifecycle().value?.goals.orEmpty()
                .filter { !it.envelope.archived }.map { it.envelope.id to it.envelope.name },
            onTakeFromGoal = { goalId, amount -> viewModel.placeSavings(mapOf(goalId to Money(-amount.cents)), start = false) },
            perDay = viewModel.dashboard.collectAsStateWithLifecycle().value.let { board ->
                { extra: Money -> board?.let { b -> b.perDayWithReserved(extra)?.let { b.dailyAllowance.allowance to it } } }
            },
            suggest = { category, frequency -> viewModel.suggestAmount(current.envelope?.id, category, frequency) },
            usage = { current.envelope?.id?.takeIf { it != 0L }?.let { viewModel.envelopeUsage(it) } },
            onDelete = { deleted ->
                viewModel.deleteEnvelope(deleted.id) { controller.notify("${deleted.name} supprimée définitivement.") }
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
