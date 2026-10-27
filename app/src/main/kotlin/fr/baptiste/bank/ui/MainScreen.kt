package fr.baptiste.bank.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.baptiste.bank.R
import fr.baptiste.bank.data.UiStyle
import fr.baptiste.bank.ui.envelopes.EnvelopesTab
import fr.baptiste.bank.ui.home.HomeTab
import fr.baptiste.bank.ui.operations.OperationsTab
import fr.baptiste.bank.update.UpdateViewModel

/** Interface classique Material 3 : barre du haut, barre d'onglets et bouton d'action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicMainScreen(
    viewModel: BudgetViewModel,
    updateViewModel: UpdateViewModel,
    onStyleChange: (UiStyle) -> Unit,
    initialTab: Tab = Tab.HOME,
) {
    val controller = rememberAppController(initialTab)
    var menuOpen by remember { mutableStateOf(false) }

    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val archived by viewModel.archivedEnvelopes.collectAsStateWithLifecycle()
    val updateAvailable = rememberUpdateAvailable(updateViewModel)

    Scaffold(
        snackbarHost = { SnackbarHost(controller.snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(controller.tab.title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            BadgedBox(badge = { if (updateAvailable) Badge() }) {
                                Icon(painterResource(R.drawable.ic_more), contentDescription = "Plus d'options")
                            }
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppMenuItems(
                                controller = controller,
                                viewModel = viewModel,
                                updateAvailable = updateAvailable,
                                style = UiStyle.CLASSIC,
                                onStyleChange = onStyleChange,
                                onClose = { menuOpen = false },
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
                        selected = controller.tab == item,
                        onClick = { controller.tab = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
        floatingActionButton = {
            val (text, target) = controller.primaryAction
            ExtendedFloatingActionButton(
                text = { Text(text) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = { controller.dialog = target },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (controller.tab) {
            Tab.HOME -> HomeTab(
                dashboard = dashboard,
                onEditIncome = { controller.dialog = OpenDialog.Income },
                onCreateEnvelope = { controller.dialog = OpenDialog.EnvelopeEditor(null) },
                onCover = { controller.dialog = OpenDialog.Cover(it.envelope.id) },
                onOpenEnvelope = { controller.dialog = OpenDialog.EnvelopeDetail(it.envelope.id) },
                modifier = modifier,
            )
            Tab.ENVELOPES -> EnvelopesTab(
                dashboard = dashboard,
                onEdit = { controller.dialog = OpenDialog.EnvelopeEditor(it) },
                onOpen = { controller.dialog = OpenDialog.EnvelopeDetail(it.envelope.id) },
                onCover = { controller.dialog = OpenDialog.Cover(it.envelope.id) },
                archived = archived,
                onRestore = {
                    viewModel.restoreEnvelope(it.id)
                    controller.notify("${it.name} est de nouveau active.")
                },
                modifier = modifier,
            )
            Tab.OPERATIONS -> OperationsTab(
                operations = operations,
                onDelete = viewModel::deleteExpense,
                modifier = modifier,
            )
        }
    }

    AppDialogs(controller, viewModel, updateViewModel)
}
