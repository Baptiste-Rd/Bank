package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import fr.baptiste.bank.R
import fr.baptiste.bank.data.UiStyle
import fr.baptiste.bank.ui.AppDialogs
import fr.baptiste.bank.ui.AppMenuItems
import fr.baptiste.bank.ui.BudgetViewModel
import fr.baptiste.bank.ui.OpenDialog
import fr.baptiste.bank.ui.Tab
import fr.baptiste.bank.ui.rememberAppController
import fr.baptiste.bank.ui.rememberUpdateAvailable
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.ui.theme.GlassSurfaceColors
import fr.baptiste.bank.update.UpdateViewModel

/** Thème de l'interface Liquid Glass : toujours bleu nuit, texte blanc. */
@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    BankTheme(darkTheme = true, glass = true, content = content)
}

/**
 * Interface Liquid Glass :
 * - le contenu défile sur toute la hauteur de l'écran, sous les éléments flottants ;
 * - les cartes courbent et floutent le fond (couche [LocalBackgroundBackdrop]) ;
 * - la barre d'onglets, les boutons et les messages courbent et floutent le fond et le contenu
 *   qui passe dessous (couche [LocalScreenBackdrop]) ;
 * - changer d'onglet remplace le contenu sans animation ; seul l'indicateur de la barre glisse.
 */
@Composable
fun GlassMainScreen(
    viewModel: BudgetViewModel,
    updateViewModel: UpdateViewModel,
    onStyleChange: (UiStyle) -> Unit,
    initialTab: Tab = Tab.HOME,
    initialDialog: OpenDialog? = null,
) {
    val controller = rememberAppController(initialTab, initialDialog)
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val archived by viewModel.archivedEnvelopes.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val unsortedCount by viewModel.unsortedCount.collectAsStateWithLifecycle()
    val savingsHistory by viewModel.savingsHistory.collectAsStateWithLifecycle()
    val updateAvailable = rememberUpdateAvailable(updateViewModel)
    var menuOpen by remember { mutableStateOf(false) }

    val backgroundBackdrop = rememberLayerBackdrop()
    val screenBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalBackgroundBackdrop provides backgroundBackdrop,
        LocalScreenBackdrop provides screenBackdrop,
    ) {
        GlassSurfaceColors(onBackground = true) {
        Box(Modifier.fillMaxSize()) {
            // Couche enregistrée pour les éléments flottants : le fond liquide et le contenu.
            Box(Modifier.fillMaxSize().layerBackdrop(screenBackdrop)) {
                LiquidBackground(Modifier.layerBackdrop(backgroundBackdrop))
                when (controller.tab) {
                    Tab.HOME -> GlassHomeContent(
                        dashboard = dashboard,
                        savingsHistory = savingsHistory,
                        onAddExpense = { controller.dialog = OpenDialog.Expense },
                        onEditIncome = { controller.dialog = OpenDialog.Income },
                        onOpenEnvelope = { controller.dialog = OpenDialog.EnvelopeDetail(it.envelope.id) },
                        onCover = { controller.dialog = OpenDialog.Cover(it.envelope.id) },
                    )
                    Tab.ENVELOPES -> GlassEnvelopesContent(
                        dashboard = dashboard,
                        archived = archived,
                        onOpen = { controller.dialog = OpenDialog.EnvelopeDetail(it.envelope.id) },
                        onCover = { controller.dialog = OpenDialog.Cover(it.envelope.id) },
                        onEdit = { controller.dialog = OpenDialog.EnvelopeEditor(it) },
                        onRestore = {
                            viewModel.restoreEnvelope(it.id)
                            controller.notify("${it.name} est de nouveau active.")
                        },
                    )
                    Tab.OPERATIONS -> GlassOperationsContent(
                        operations = operations,
                        envelopes = envelopes + archived,
                        unsortedCount = unsortedCount,
                        onDelete = viewModel::deleteExpense,
                        onSort = { controller.dialog = OpenDialog.SortOperation(it) },
                        onOpenBank = { controller.dialog = OpenDialog.Bank },
                    )
                }
            }

            // Voile sous la barre d'état, pour que le contenu ne passe pas sous l'heure.
            StatusBarScrim(
                modifier = Modifier.align(Alignment.TopCenter),
                height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
            )

            // En haut à droite : l'action de l'onglet (+) et le menu ⋮, comme dans les apps iOS.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val (label, target) = controller.primaryAction
                GlassIconButton(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Ajouter : $label",
                    onClick = { controller.dialog = target },
                    accent = true,
                )
                Box {
                    GlassIconButton(
                        painter = painterResource(R.drawable.ic_more),
                        contentDescription = "Plus d'options",
                        onClick = { menuOpen = true },
                        badge = updateAvailable,
                    )
                    // Le menu s'affiche dans une fenêtre à part : il reprend les couleurs « sur verre ».
                    GlassSurfaceColors(onBackground = false) {
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppMenuItems(
                                controller = controller,
                                viewModel = viewModel,
                                updateAvailable = updateAvailable,
                                style = UiStyle.GLASS,
                                onStyleChange = onStyleChange,
                                onClose = { menuOpen = false },
                            )
                        }
                    }
                }
            }

            // Messages de confirmation, en verre, au-dessus de la barre d'onglets.
            SnackbarHost(
                hostState = controller.snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
            ) { data -> GlassSurfaceColors(onBackground = false) { GlassSnackbar(data) } }

            // Barre d'onglets flottante et centrée, comme celle d'iOS 26.
            GlassTabBar(
                items = Tab.entries.map { GlassTabItem(it.title, painterResource(it.icon)) },
                selectedIndex = controller.tab.ordinal,
                onSelect = { controller.tab = Tab.entries[it] },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(0.86f),
            )
        }
        }

        // Les fenêtres (dialogues, panneaux) reprennent les couleurs « sur verre » du thème.
        AppDialogs(controller, viewModel, updateViewModel)
    }
}

/** Message de confirmation, dans une capsule de verre. */
@Composable
private fun GlassSnackbar(data: SnackbarData) {
    GlassBanner {
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        val action = data.visuals.actionLabel
        if (action != null) {
            TextButton(onClick = { data.performAction() }) { Text(action) }
        } else {
            TextButton(onClick = { data.dismiss() }) { Text("OK") }
        }
    }
}
