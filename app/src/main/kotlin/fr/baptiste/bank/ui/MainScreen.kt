package fr.baptiste.bank.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import fr.baptiste.bank.ui.components.ExpenseDialog
import fr.baptiste.bank.ui.components.IncomeDialog
import fr.baptiste.bank.ui.envelopes.EnvelopesTab
import fr.baptiste.bank.ui.home.HomeTab
import fr.baptiste.bank.ui.operations.OperationsTab

private enum class Tab(val title: String, @DrawableRes val icon: Int) {
    HOME("Accueil", R.drawable.ic_home),
    ENVELOPES("Enveloppes", R.drawable.ic_envelope),
    OPERATIONS("Opérations", R.drawable.ic_list),
}

/** Les dialogues ouverts sont décrits par un état unique : un seul peut être affiché à la fois. */
private sealed interface OpenDialog {
    data object Expense : OpenDialog
    data object Income : OpenDialog
    data class EnvelopeEditor(val envelope: Envelope?) : OpenDialog
}

@Composable
fun MainScreen(viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory)) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var dialog by remember { mutableStateOf<OpenDialog?>(null) }

    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val income by viewModel.monthlyIncome.collectAsStateWithLifecycle()

    Scaffold(
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
        OpenDialog.Expense -> ExpenseDialog(
            envelopes = envelopes,
            onDismiss = { dialog = null },
            onSave = { amount, date, label, envelope ->
                viewModel.addExpense(amount, date, label, envelope)
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
