package fr.baptiste.bank.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.baptiste.bank.R
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.ui.components.EnvelopeDialog
import fr.baptiste.bank.ui.components.ExpenseSheet
import fr.baptiste.bank.ui.components.IncomeDialog
import fr.baptiste.bank.ui.envelopes.EnvelopesTab
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.home.HomeTab
import fr.baptiste.bank.ui.operations.OperationsTab
import fr.baptiste.bank.update.UpdateDialog
import fr.baptiste.bank.update.UpdateUiState
import fr.baptiste.bank.update.UpdateViewModel

private enum class Tab(val title: String, @DrawableRes val icon: Int) {
    HOME("Aujourd'hui", R.drawable.ic_home),
    ENVELOPES("Enveloppes", R.drawable.ic_envelope),
    OPERATIONS("Opérations", R.drawable.ic_list),
}

/** Les fenêtres ouvertes sont décrites par un état unique : une seule s'affiche à la fois. */
private sealed interface OpenDialog {
    data object Expense : OpenDialog
    data object Income : OpenDialog
    data object Updates : OpenDialog
    data class EnvelopeEditor(val envelope: Envelope?) : OpenDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory),
    updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var dialog by remember { mutableStateOf<OpenDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val income by viewModel.monthlyIncome.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val updateAvailable = updateState is UpdateUiState.Available

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            BadgedBox(badge = { if (updateAvailable) Badge() }) {
                                Icon(painterResource(R.drawable.ic_more), contentDescription = "Plus d'options")
                            }
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(income?.let { "Salaire mensuel : ${it.formatted()}" } ?: "Indiquer mon salaire")
                                },
                                onClick = {
                                    menuOpen = false
                                    dialog = OpenDialog.Income
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
                                    menuOpen = false
                                    dialog = OpenDialog.Updates
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
        floatingActionButton = {
            val (text, target) = when (tab) {
                Tab.ENVELOPES -> "Enveloppe" to OpenDialog.EnvelopeEditor(null)
                Tab.HOME, Tab.OPERATIONS -> "Dépense" to OpenDialog.Expense
            }
            ExtendedFloatingActionButton(
                text = { Text(text) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = { dialog = target },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (tab) {
            Tab.HOME -> HomeTab(
                dashboard = dashboard,
                onEditIncome = { dialog = OpenDialog.Income },
                onCreateEnvelope = { dialog = OpenDialog.EnvelopeEditor(null) },
                modifier = modifier,
            )
            Tab.ENVELOPES -> EnvelopesTab(
                dashboard = dashboard,
                onEdit = { dialog = OpenDialog.EnvelopeEditor(it) },
                modifier = modifier,
            )
            Tab.OPERATIONS -> OperationsTab(
                operations = operations,
                onDelete = viewModel::deleteExpense,
                modifier = modifier,
            )
        }
    }

    when (val current = dialog) {
        null -> Unit
        OpenDialog.Expense -> ExpenseSheet(
            envelopes = envelopes,
            onDismiss = { dialog = null },
            onCreateEnvelope = {
                tab = Tab.ENVELOPES
                dialog = OpenDialog.EnvelopeEditor(null)
            },
            onSave = { amount, date, label, envelope, wholePeriod ->
                viewModel.addExpense(amount, date, label, envelope, wholePeriod)
                dialog = null
            },
        )
        OpenDialog.Income -> IncomeDialog(
            current = income,
            onDismiss = { dialog = null },
            onSave = {
                viewModel.setMonthlyIncome(it)
                dialog = null
            },
        )
        OpenDialog.Updates -> UpdateDialog(viewModel = updateViewModel, onDismiss = { dialog = null })
        is OpenDialog.EnvelopeEditor -> EnvelopeDialog(
            initial = current.envelope,
            onDismiss = { dialog = null },
            onSave = {
                viewModel.saveEnvelope(it)
                dialog = null
            },
            onArchive = {
                viewModel.archiveEnvelope(it.id)
                dialog = null
            },
        )
    }
}
