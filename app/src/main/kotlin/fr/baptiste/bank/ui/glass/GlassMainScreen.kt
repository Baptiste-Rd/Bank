package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import fr.baptiste.bank.banking.BankViewModel
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.data.UiStyle
import fr.baptiste.bank.domain.budget.EnvelopeLine
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
import java.time.LocalDate
import java.time.YearMonth

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
    initialBudgetView: BudgetView = BudgetView.MONTH,
    /** Ouvre directement le menu développeur (captures d'écran). */
    initialTuningOpen: Boolean = false,
) {
    val controller = rememberAppController(initialTab, initialDialog)
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val archived by viewModel.archivedEnvelopes.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val unsortedCount by viewModel.unsortedCount.collectAsStateWithLifecycle()
    val savingsHistory by viewModel.savingsHistory.collectAsStateWithLifecycle()
    val income by viewModel.monthlyIncome.collectAsStateWithLifecycle()
    val monthDashboard by viewModel.monthDashboard.collectAsStateWithLifecycle()
    val monthHistory by viewModel.monthSavingsHistory.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val homeShowsDay by viewModel.homeShowsDay.collectAsStateWithLifecycle()
    // Même instance que celle des fenêtres (portée de l'activité) : le solde lu à la banque.
    val bankViewModel: BankViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = BankViewModel.Factory)
    val bankConnection by bankViewModel.connection.collectAsStateWithLifecycle()
    val aliases by viewModel.aliases.collectAsStateWithLifecycle()
    // Lu pour que les propositions suivent l'historique du tri.
    val sortedHistory by viewModel.sortedHistory.collectAsStateWithLifecycle()
    val updateAvailable = rememberUpdateAvailable(updateViewModel)
    var menuOpen by remember { mutableStateOf(false) }

    // Réglages du verre : ceux enregistrés, ou l'essai en cours quand le menu développeur est ouvert.
    val savedTuningText by viewModel.glassTuning.collectAsStateWithLifecycle()
    val savedTuning = remember(savedTuningText) { GlassTuning.decode(savedTuningText) }
    var tuningOpen by remember { mutableStateOf(initialTuningOpen) }
    var draftTuning by remember { mutableStateOf<GlassTuning?>(null) }
    val tuning = if (tuningOpen) draftTuning ?: savedTuning else savedTuning

    val backgroundBackdrop = rememberLayerBackdrop()
    val screenBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalBackgroundBackdrop provides backgroundBackdrop,
        LocalScreenBackdrop provides screenBackdrop,
        LocalGlassTuning provides tuning,
    ) {
        GlassSurfaceColors(onBackground = true) {
        Column(Modifier.fillMaxSize()) {
        // Avec le menu développeur ouvert, l'app occupe le haut de l'écran et suit les réglages en direct.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Couche enregistrée pour les éléments flottants : le fond liquide et le contenu.
            Box(Modifier.fillMaxSize().layerBackdrop(screenBackdrop)) {
                LiquidBackground(Modifier.layerBackdrop(backgroundBackdrop))
                val openEnvelope: (EnvelopeLine) -> Unit = { controller.dialog = OpenDialog.EnvelopeDetail(it.envelope.id) }
                val cover: (EnvelopeLine) -> Unit = { controller.dialog = OpenDialog.Cover(it.envelope.id) }
                when (controller.tab) {
                    Tab.HOME -> GlassHomeContent(
                        dashboard = dashboard,
                        savingsHistory = savingsHistory,
                        unsortedCount = unsortedCount,
                        onAddExpense = { controller.dialog = OpenDialog.Expense },
                        showsDay = homeShowsDay,
                        onShowsDayChange = viewModel::setHomeShowsDay,
                        accountBalance = bankConnection?.balanceCents?.let(::Money),
                        onOpenReview = {
                            if (selectedMonth == YearMonth.now()) viewModel.previousMonth()
                            controller.tab = Tab.BUDGET
                        },
                        onEditIncome = { controller.dialog = OpenDialog.Income },
                        onCover = cover,
                        onOpenEnvelope = openEnvelope,
                        onOpenTab = { controller.tab = it },
                    )
                    Tab.BUDGET -> {
                        val yearOverview by viewModel.yearOverview.collectAsStateWithLifecycle()
                        GlassBudgetContent(
                            dashboard = monthDashboard,
                            monthlyIncome = income,
                            archived = archived,
                            month = selectedMonth,
                            canNextMonth = selectedMonth < YearMonth.now(),
                            onPreviousMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                            yearOverview = yearOverview,
                            year = selectedYear,
                            canNextYear = selectedYear < LocalDate.now().year,
                            onPreviousYear = viewModel::previousYear,
                            onNextYear = viewModel::nextYear,
                            onEditIncome = { controller.dialog = OpenDialog.Income },
                            onOpen = openEnvelope,
                            onCover = cover,
                            onEdit = { controller.dialog = OpenDialog.EnvelopeEditor(it) },
                            onRestore = {
                                viewModel.restoreEnvelope(it.id)
                                controller.notify("${it.name} est de nouveau active.")
                            },
                            initialView = initialBudgetView,
                        )
                    }
                    Tab.SAVINGS -> GlassSavingsContent(
                        dashboard = monthDashboard,
                        history = monthHistory,
                        onPutAside = { controller.dialog = OpenDialog.Move(it.envelope.id, incoming = true) },
                        onPlanPurchase = { controller.dialog = controller.primaryAction.second },
                        onOpenEnvelope = openEnvelope,
                        onCover = cover,
                        selectedMonth = selectedMonth,
                        canNextMonth = selectedMonth < YearMonth.now(),
                        onPreviousMonth = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth,
                        holdings = holdings,
                        onManageAccounts = { controller.dialog = OpenDialog.Accounts },
                        onOpenAccount = { controller.dialog = OpenDialog.AccountEdit(it) },
                    )
                    Tab.OPERATIONS -> GlassOperationsContent(
                        operations = operations,
                        envelopes = envelopes + archived,
                        onDelete = viewModel::deleteExpense,
                        onSort = { controller.dialog = OpenDialog.SortOperation(it) },
                        onOpenBank = { controller.dialog = OpenDialog.Bank },
                        suggestion = { operation ->
                            viewModel.suggestedEnvelopeId(operation, sortedHistory)?.let { id -> envelopes.firstOrNull { it.id == id }?.name }
                        },
                        onApplyRules = {
                            viewModel.applyRules { sorted ->
                                controller.notify(
                                    if (sorted == 0) "Aucune règle ne reconnaît ces opérations." else "$sorted opération(s) rangée(s) par les règles.",
                                )
                            }
                        },
                        onUnsort = { controller.dialog = OpenDialog.Unsort },
                        onOpenRules = { controller.dialog = OpenDialog.Rules },
                        onAssistedSort = { controller.dialog = OpenDialog.AssistedSort() },
                        rulesSummary = "${rules.size} règle(s) · ${aliases.size} alias",
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
                                onOpenTuning = {
                                    draftTuning = savedTuning
                                    tuningOpen = true
                                },
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
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(0.92f),
            )
        }
        if (tuningOpen) {
            GlassTuningPanel(
                tuning = tuning,
                onChange = { draftTuning = it },
                onSave = {
                    val chosen = draftTuning ?: savedTuning
                    viewModel.saveGlassTuning(if (chosen == GlassTuning.Default) null else chosen.encode())
                    tuningOpen = false
                    draftTuning = null
                    controller.notify("Réglages du verre enregistrés.")
                },
                onCancel = {
                    tuningOpen = false
                    draftTuning = null
                },
                onNotify = { controller.notify(it) },
                modifier = Modifier.fillMaxHeight(0.5f),
            )
        }
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
