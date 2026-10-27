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
import fr.baptiste.bank.R
import fr.baptiste.bank.data.UiStyle
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.budget.coveragePlan
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.EnvelopeDialog
import fr.baptiste.bank.ui.components.ExpenseSheet
import fr.baptiste.bank.ui.components.IncomeDialog
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.transfers.CoverDialog
import fr.baptiste.bank.ui.transfers.EnvelopeSheet
import fr.baptiste.bank.ui.transfers.JournalDialog
import fr.baptiste.bank.ui.transfers.TransferDialog
import fr.baptiste.bank.update.UpdateDialog
import fr.baptiste.bank.update.UpdateUiState
import fr.baptiste.bank.update.UpdateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Ce qui est commun aux deux styles d'interface (classique et Liquid Glass) : les onglets,
 * les fenêtres, les messages de confirmation et le menu ⋮. Chaque style ne fait que la mise en page.
 */

enum class Tab(val title: String, @DrawableRes val icon: Int) {
    HOME("Aujourd'hui", R.drawable.ic_home),
    ENVELOPES("Enveloppes", R.drawable.ic_envelope),
    OPERATIONS("Opérations", R.drawable.ic_list),
}

/** Les fenêtres ouvertes sont décrites par un état unique : une seule s'affiche à la fois. */
sealed interface OpenDialog {
    data object Expense : OpenDialog
    data object Income : OpenDialog
    data object Updates : OpenDialog
    data object Journal : OpenDialog
    data class EnvelopeEditor(val envelope: Envelope?) : OpenDialog

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

    /** L'action principale de l'onglet : une dépense, ou une enveloppe dans l'onglet Enveloppes. */
    val primaryAction: Pair<String, OpenDialog>
        get() = when (tab) {
            Tab.ENVELOPES -> "Enveloppe" to OpenDialog.EnvelopeEditor(null)
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
fun rememberAppController(initialTab: Tab = Tab.HOME): AppController {
    var savedTab by rememberSaveable { mutableStateOf(initialTab) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val controller = remember { AppController(savedTab, snackbar, scope) }
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

    fun close() {
        controller.dialog = null
    }
    fun nameOf(envelopeId: Long?): String =
        envelopeId?.let { id -> envelopes.firstOrNull { it.id == id }?.name } ?: "Portefeuille"

    when (val current = controller.dialog) {
        null -> Unit
        OpenDialog.Expense -> ExpenseSheet(
            envelopes = envelopes,
            onDismiss = ::close,
            onCreateEnvelope = {
                controller.tab = Tab.ENVELOPES
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
        OpenDialog.Journal -> JournalDialog(
            transfers = journal,
            onDismiss = ::close,
            onCancel = viewModel::cancelTransfer,
        )
        is OpenDialog.EnvelopeDetail -> {
            val line = dashboard?.envelopes?.firstOrNull { it.envelope.id == current.envelopeId }
            if (line == null) {
                // L'enveloppe a disparu (archivée) : on ferme la fenêtre, hors de la composition.
                LaunchedEffect(current) { close() }
            } else {
                EnvelopeSheet(
                    line = line,
                    transfers = journal,
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
                        val total = legs.fold(Money.ZERO) { sum, leg -> sum + leg.amount }
                        controller.notify(
                            "${total.formatted()} ajoutés à ${plan.target.envelope.name}. Disponible du jour recalculé.",
                        )
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
            onDismiss = ::close,
            onSave = {
                viewModel.saveEnvelope(it)
                close()
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
