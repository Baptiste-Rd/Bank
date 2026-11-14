package fr.baptiste.bank.ui.glass

import androidx.compose.runtime.LaunchedEffect
import fr.baptiste.bank.domain.model.category
import fr.baptiste.bank.ui.format.summary
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import fr.baptiste.bank.domain.budget.message
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.ui.AppDialogs
import fr.baptiste.bank.ui.BudgetViewModel
import fr.baptiste.bank.ui.OpenDialog
import fr.baptiste.bank.ui.Tab
import fr.baptiste.bank.ui.rememberAppController
import fr.baptiste.bank.ui.rememberUpdateAvailable
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.format.plural
import fr.baptiste.bank.domain.bank.suggestedRuleKeyword
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
    initialTab: Tab = Tab.HOME,
    initialDialog: OpenDialog? = null,
    initialBudgetView: BudgetView = BudgetView.MONTH,
    /** Ouvre directement le menu développeur (captures d'écran). */
    initialTuningOpen: Boolean = false,
    initialSpendingView: SpendingTabView = SpendingTabView.ANALYSIS,
    /** Sheet ouverte au lancement (captures) : ADD ou SETTINGS. */
    initialSheet: String? = null,
    /** Captures : une catégorie dépliée, et le panneau Déplacer d'une enveloppe. */
    initialExpand: String? = null,
    initialMoveEnvelopeId: Long? = null,
) {
    val controller = rememberAppController(initialTab, initialDialog)
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val monthOperations by viewModel.monthOperations.collectAsStateWithLifecycle()
    val monthCumulative by viewModel.monthCumulative.collectAsStateWithLifecycle()
    val unsortedOperations by viewModel.unsortedOperations.collectAsStateWithLifecycle()
    val recurringExpenses by viewModel.recurringExpenses.collectAsStateWithLifecycle()
    // Épargne depuis janvier (année en cours) : affichée dans Épargne.
    val yearOverviewForSavings by viewModel.yearOverview.collectAsStateWithLifecycle()
    val archived by viewModel.archivedEnvelopes.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val unsortedCount by viewModel.unsortedCount.collectAsStateWithLifecycle()
    val savingsHistory by viewModel.savingsHistory.collectAsStateWithLifecycle()
    val income by viewModel.monthlyIncome.collectAsStateWithLifecycle()
    val monthIncome by viewModel.monthIncome.collectAsStateWithLifecycle()
    val monthDashboard by viewModel.monthDashboard.collectAsStateWithLifecycle()
    val monthHistory by viewModel.monthSavingsHistory.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val transferRules by viewModel.transferRules.collectAsStateWithLifecycle()
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val previousYearSpending by viewModel.monthPreviousYearSpending.collectAsStateWithLifecycle()
    val homeShowsDay by viewModel.homeShowsDay.collectAsStateWithLifecycle()
    val currentBalance by viewModel.currentAccountBalance.collectAsStateWithLifecycle()
    // Même instance que celle des fenêtres (portée de l'activité) : les opérations en attente.
    val bankViewModel: BankViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = BankViewModel.Factory)
    val bankConnection by bankViewModel.connection.collectAsStateWithLifecycle()
    val savingsState by viewModel.savingsState.collectAsStateWithLifecycle()
    val trackingStart by viewModel.trackingStart.collectAsStateWithLifecycle()
    val firstOperationDate by viewModel.firstOperationDate.collectAsStateWithLifecycle()
    val salaryCandidates by viewModel.salaryCandidates.collectAsStateWithLifecycle()
    val salaryKeyConfirmed by viewModel.salary.collectAsStateWithLifecycle()
    // Catégories imbriquées : glisser une ligne, « Déplacer » et « Renommer ».
    val categoryParents by viewModel.categoryParents.collectAsStateWithLifecycle()
    val treeGroups by viewModel.treeGroups.collectAsStateWithLifecycle()
    var moveSubject by remember { mutableStateOf<fr.baptiste.bank.ui.components.MoveSubject?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var expandCategory by remember { mutableStateOf(initialExpand) }
    LaunchedEffect(envelopes, initialMoveEnvelopeId) {
        val envelope = envelopes.firstOrNull { it.id == initialMoveEnvelopeId } ?: return@LaunchedEffect
        if (moveSubject == null) moveSubject = fr.baptiste.bank.ui.components.MoveSubject.EnvelopeSubject(envelope.id, envelope.name, envelope.category)
    }
    val moveEnvelope: (fr.baptiste.bank.domain.model.Envelope) -> Unit = { envelope ->
        moveSubject = fr.baptiste.bank.ui.components.MoveSubject.EnvelopeSubject(envelope.id, envelope.name, envelope.category)
    }
    // Ranger dans l'arbre, par glissement ou depuis « Déplacer » : un seul chemin, un seul message.
    val dropInTree: (Long, fr.baptiste.bank.domain.model.TreeDrop) -> Unit = { id, drop ->
        viewModel.moveInTree(id, drop) { result ->
            if (result == null) {
                controller.notify("Impossible : une enveloppe ne va pas dans ce qu'elle contient.")
            } else if (result.label.isNotEmpty()) {
                controller.notify(result.message { it.summary() }, "Annuler") { viewModel.undoLast() }
            }
        }
    }
    val rowActions = CategoryRowActions(
        onMoveEnvelope = moveEnvelope,
        onEditEnvelope = { edited -> controller.dialog = OpenDialog.EnvelopeEditor((envelopes + archived).firstOrNull { it.id == edited.id } ?: edited) },
        onMoveCategory = { name -> moveSubject = fr.baptiste.bank.ui.components.MoveSubject.CategorySubject(name, fr.baptiste.bank.domain.model.ancestorsOf(name, categoryParents).firstOrNull()) },
        onRenameCategory = { renaming = it },
    )
    val monthTransfers by remember(selectedMonth) { viewModel.transfersToAccounts(selectedMonth) }
        .collectAsStateWithLifecycle(null)
    val aliases by viewModel.aliases.collectAsStateWithLifecycle()
    // Lu pour que les propositions suivent l'historique du tri.
    val sortedHistory by viewModel.sortedHistory.collectAsStateWithLifecycle()
    val updateAvailable = rememberUpdateAvailable(updateViewModel)
    var spendingView by rememberSaveable { mutableStateOf(initialSpendingView) }
    // Sheets de l'ossature : Ajouter (+), Réglages (roue), Année (barre des mois).
    var addOpen by rememberSaveable { mutableStateOf(initialSheet == "ADD") }
    var settingsOpen by rememberSaveable { mutableStateOf(initialSheet == "SETTINGS") }
    var yearOpen by rememberSaveable { mutableStateOf(initialBudgetView == BudgetView.YEAR) }
    var searchOpen by rememberSaveable { mutableStateOf(initialSheet == "SEARCH") }
    val dateCheck by viewModel.dateCheck.collectAsStateWithLifecycle()
    // En-tête de l'onglet Dépenses, commun aux deux vues : le mois, puis la vue (comme un
    // contrôle segmenté d'iOS). La recherche est la loupe de la barre du haut ; les dates à
    // corriger sont dans la vue Opérations, avec les opérations qu'elles concernent.
    val corrections = dateCheck?.corrections.orEmpty().size
    val spendingHeader: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        item(key = "month-strip") { MonthStripBar() }
        item(key = "spending-view") {
            SegmentedToggle(
                listOf("Enveloppes", if (unsortedCount > 0 && selectedMonth == YearMonth.now()) "Opérations · $unsortedCount" else "Opérations"),
                if (spendingView == SpendingTabView.LIST) 1 else 0,
            ) { index -> spendingView = if (index == 1) SpendingTabView.LIST else SpendingTabView.ANALYSIS }
        }
    }
    // Les mois proposés : depuis le début du suivi (douze mois au moins, trois ans au plus).
    val now = YearMonth.now()
    val firstMonth = maxOf(minOf(trackingStart ?: now.minusMonths(11), now.minusMonths(11)), now.minusMonths(36))
    val monthNavigation = MonthNavigation(
        selected = selectedMonth,
        months = generateSequence(firstMonth) { it.plusMonths(1) }.takeWhile { it <= now }.toList(),
        onSelect = viewModel::selectMonth,
        onYear = { yearOpen = true },
    )

    // Réglages du verre : ceux enregistrés, ou l'essai en cours quand le menu développeur est ouvert.
    val savedTuningText by viewModel.glassTuning.collectAsStateWithLifecycle()
    val savedTuning = remember(savedTuningText) { GlassTuning.decode(savedTuningText) }
    var tuningOpen by remember { mutableStateOf(initialTuningOpen) }
    var draftTuning by remember { mutableStateOf<GlassTuning?>(null) }
    val tuning = if (tuningOpen) draftTuning ?: savedTuning else savedTuning

    // Un état de défilement par écran (et par vue de Dépenses) : le titre compact suit l'écran affiché.
    val listStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val screenKey = if (controller.tab == Tab.OPERATIONS) "${controller.tab}-$spendingView" else controller.tab.name
    val listState = listStates.getOrPut(screenKey) { androidx.compose.foundation.lazy.LazyListState() }
    // Le grand titre est passé sous la barre du haut : on affiche le titre compact.
    val collapsed by remember(listState) {
        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 }
    }

    val backgroundBackdrop = rememberLayerBackdrop()
    val screenBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalBackgroundBackdrop provides backgroundBackdrop,
        LocalScreenBackdrop provides screenBackdrop,
        LocalGlassTuning provides tuning,
        LocalTabListState provides listState,
        LocalMonthNavigation provides monthNavigation,
        LocalFirstOperationDate provides firstOperationDate,
        fr.baptiste.bank.ui.theme.LocalCategoryParents provides categoryParents,
        fr.baptiste.bank.ui.theme.LocalExcludedMonths provides viewModel.excludedMonths.collectAsStateWithLifecycle().value,
    ) {
        GlassSurfaceColors(onBackground = true) {
        Column(Modifier.fillMaxSize()) {
        // Avec le menu développeur ouvert, l'app occupe le haut de l'écran et suit les réglages en direct.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Couche enregistrée pour les éléments flottants : le fond liquide et le contenu.
            // Glisser horizontalement change de mois (Résumé, Dépenses).
            val swipeMonths = controller.tab != Tab.SAVINGS && controller.tab != Tab.BUDGET
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(screenBackdrop)
                    .then(
                        if (!swipeMonths) Modifier else Modifier.pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total < -120f) viewModel.nextMonth()
                                    if (total > 120f) viewModel.previousMonth()
                                },
                            ) { _, dragAmount -> total += dragAmount }
                        },
                    ),
            ) {
                LiquidBackground(Modifier.layerBackdrop(backgroundBackdrop))
                val openEnvelope: (EnvelopeLine) -> Unit = { controller.dialog = OpenDialog.EnvelopeDetail(it.envelope.id) }
                val cover: (EnvelopeLine) -> Unit = { controller.dialog = OpenDialog.Cover(it.envelope.id) }
                when (controller.tab) {
                    Tab.HOME -> if (selectedMonth != YearMonth.now()) {
                        // Un mois terminé : ce qu'il a coûté et son bilan.
                        val monthYearSpending by viewModel.monthYearSpending.collectAsStateWithLifecycle()
                        GlassPastMonthContent(
                            month = selectedMonth,
                            report = monthYearSpending?.months?.firstOrNull { it.month == selectedMonth },
                            dashboard = monthDashboard,
                            transfers = monthTransfers,
                            trackingStart = trackingStart,
                            onEdit = { edited -> controller.dialog = OpenDialog.EnvelopeEditor((envelopes + archived).firstOrNull { it.id == edited.id } ?: edited) },
                            onOpenTab = { controller.tab = it },
                            onTrackMonth = { month ->
                                viewModel.setTrackingStart(month)
                                controller.notify("Suivi depuis ${month.label}.")
                            },
                        )
                    } else {
                        val pace by viewModel.monthPace.collectAsStateWithLifecycle()
                        val recap by viewModel.previousMonthRecap.collectAsStateWithLifecycle()
                        GlassHomeContent(
                            dashboard = dashboard,
                            recap = recap,
                            onOpenRecap = { recap?.let { viewModel.selectMonth(it.month) } },
                            onDismissRecap = viewModel::dismissRecap,
                            unsortedCount = unsortedCount,
                            onOpenEnvelope = openEnvelope,
                            onSortCards = { controller.dialog = OpenDialog.SortCards },
                            onCover = cover,
                            onOpenTab = {
                                // Le lien « à trier » de l'accueil mène à la liste, pas à l'analyse.
                                if (it == Tab.OPERATIONS) spendingView = SpendingTabView.LIST
                                controller.tab = it
                            },
                            accountBalance = currentBalance?.balance,
                            // Dernière lecture du solde : synchronisation de la banque, ou saisie datée.
                            balanceCheckedAt = bankConnection?.takeIf { it.isConnected }?.lastSync
                                ?: currentBalance?.date?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant(),
                            bankConnected = bankConnection?.isConnected == true,
                            onSync = {
                                bankViewModel.syncNow()
                                controller.notify("Synchronisation en cours…")
                            },
                            onSetBalance = { controller.dialog = OpenDialog.CurrentBalance },
                            pace = pace,
                            holdings = holdings,
                            onOpenAccount = { controller.dialog = OpenDialog.AccountEdit(it) },
                            income = monthIncome,
                            recent = operations,
                            onOpenOperation = { controller.dialog = OpenDialog.SortOperation(it) },
                            salary = income,
                        )
                    }
                    Tab.BUDGET -> {
                        // Budget est le prévisionnel : toujours le plan du mois en cours.
                        GlassBudgetContent(
                            dashboard = dashboard,
                            monthlyIncome = dashboard?.income,
                            archived = archived,
                            onEditIncome = { controller.dialog = OpenDialog.Income },
                            onEdit = { edited -> controller.dialog = OpenDialog.EnvelopeEditor((envelopes + archived).firstOrNull { it.id == edited.id } ?: edited) },
                            onRestore = {
                                viewModel.restoreEnvelope(it.id)
                                controller.notify("${it.name} reprise.")
                            },
                            trackingStart = trackingStart,
                            onNewEnvelope = { controller.dialog = OpenDialog.EnvelopeEditor(null) },
                            onMoveEnvelope = moveEnvelope,
                            envelopes = envelopes,
                            groups = treeGroups,
                            onRenameGroup = { renaming = it },
                            onMoveGroup = rowActions.onMoveCategory,
                            onDeleteGroup = { name ->
                                viewModel.deleteCategory(name) { done ->
                                    if (done) controller.notify("Catégorie $name supprimée, son contenu remonte", "Annuler") { viewModel.undoLast() }
                                }
                            },
                            onDrop = dropInTree,
                        )
                    }
                    // Épargne couvre tous les mois : elle reste sur le mois en cours.
                    Tab.SAVINGS -> GlassSavingsContent(
                        dashboard = dashboard,
                        history = savingsHistory,
                        onPutAside = { controller.dialog = OpenDialog.Move(it.envelope.id, incoming = true) },
                        onPlanPurchase = { controller.dialog = controller.primaryAction.second },
                        onOpenEnvelope = openEnvelope,
                        onCover = cover,
                        selectedMonth = YearMonth.now(),
                        holdings = holdings,
                        onManageAccounts = { controller.dialog = OpenDialog.Accounts },
                        onOpenAccount = { controller.dialog = OpenDialog.AccountEdit(it) },
                        savings = savingsState,
                        onPlace = { controller.dialog = OpenDialog.PlaceSavings(it) },
                        onNewGoal = {
                            controller.dialog = OpenDialog.EnvelopeEditor(
                                fr.baptiste.bank.domain.model.Envelope(
                                    0, "", fr.baptiste.bank.domain.model.EnvelopeType.SAVINGS, fr.baptiste.bank.domain.period.Frequency.MONTHLY,
                                    fr.baptiste.bank.domain.money.Money.ZERO, fr.baptiste.bank.domain.model.EnvelopeStatus.PROTECTED,
                                ),
                            )
                        },
                        onOpenGoal = { goal -> controller.dialog = OpenDialog.EnvelopeEditor(goal) },
                        trackingStart = trackingStart,
                        onTrackMonth = { month ->
                            viewModel.setTrackingStart(month)
                            controller.notify("Suivi depuis ${month.label}.")
                        },
                        bank = bankConnection?.let {
                            BankStatus(
                                connected = it.isConnected,
                                bankName = it.bankName,
                                lastSync = it.lastSync,
                                validUntil = it.validUntil,
                                expired = it.isExpired,
                            )
                        },
                        yearSavings = yearOverviewForSavings?.takeIf { it.year == LocalDate.now().year }?.savings,
                        onTryPlan = { controller.dialog = OpenDialog.TryPlan() },
                    )
                    Tab.OPERATIONS -> if (spendingView == SpendingTabView.ANALYSIS) {
                        val monthYearSpending by viewModel.monthYearSpending.collectAsStateWithLifecycle()
                        val yearSpending by viewModel.yearSpending.collectAsStateWithLifecycle()
                        val analysisPace by viewModel.monthPace.collectAsStateWithLifecycle()
                        GlassAnalysisContent(
                            header = spendingHeader,
                            pace = analysisPace,
                            month = selectedMonth,
                            monthYear = monthYearSpending,
                            year = selectedYear,
                            yearSpending = yearSpending,
                            canNextYear = selectedYear < LocalDate.now().year,
                            onPreviousYear = viewModel::previousYear,
                            onNextYear = viewModel::nextYear,
                            onSelectMonth = viewModel::selectMonth,
                            trackingStart = trackingStart,
                            onTrackMonth = { month ->
                                viewModel.setTrackingStart(month)
                                controller.notify("Suivi depuis ${month.label}.")
                            },
                            onSort = { spendingView = SpendingTabView.LIST },
                            envelopeOperations = viewModel::envelopeMonthOperations,
                            expandCategory = expandCategory,
                            board = monthDashboard,
                            groups = treeGroups,
                            onOpenLine = openEnvelope,
                            onSetMonthCounted = { month, counted ->
                                viewModel.setMonthCounted(month, counted)
                                controller.notify("${month.label.replaceFirstChar { it.uppercase() }} : ${if (counted) "mois de référence" else "hors référence"}")
                            },
                            showYear = false,
                            onShowMonth = {},
                            monthReview = viewModel::monthReview,
                            onSaveReview = { month, text ->
                                viewModel.saveMonthReview(month, text)
                                if (text != null) controller.notify("Avis gardé avec ${month.label}.")
                            },
                            savings = monthDashboard?.savings,
                            recurring = recurringExpenses,
                            monthOutflows = viewModel::monthOutflows,
                            income = income,
                            previousYear = previousYearSpending,
                        )
                    } else GlassOperationsContent(
                        header = spendingHeader,
                        operations = monthOperations,
                        cumulativeByDay = monthCumulative,
                        envelopes = envelopes + archived,
                        onSort = { controller.dialog = OpenDialog.SortOperation(it) },
                        onOpenBank = { controller.dialog = OpenDialog.Bank },
                        toValidate = unsortedOperations,
                        onSortCards = { controller.dialog = OpenDialog.SortCards },
                        dateCorrections = corrections,
                        onCheckDates = { controller.dialog = OpenDialog.DateCheck },
                        // Proposition : l'enveloppe de ce libellé d'après l'historique ; une rentrée
                        // d'argent sans historique (salaire, remboursement) va hors budget.
                        proposal = { operation ->
                            val salary = salaryCandidates.firstOrNull { operation.amountCents > 0 && it.key == fr.baptiste.bank.domain.budget.salaryKey(operation.bankLabel) }
                            // Une rentrée d'au moins 300 € : peut-être le salaire, même vu une seule fois.
                            (salary ?: fr.baptiste.bank.domain.budget.SalaryCandidate(
                                label = operation.bankLabel,
                                key = fr.baptiste.bank.domain.budget.salaryKey(operation.bankLabel),
                                lastAmount = fr.baptiste.bank.domain.money.Money(operation.amountCents),
                                lastDate = operation.date,
                                months = 1,
                                typicalDay = operation.date.dayOfMonth,
                            ).takeIf { operation.amountCents >= 30_000 && salaryKeyConfirmed == null })
                                ?.let { SortProposal(null, "C'est le salaire ?", salary = it) }
                                ?: viewModel.suggestedEnvelopeId(operation, sortedHistory)
                                    ?.let { id -> envelopes.firstOrNull { it.id == id } }
                                    ?.let { SortProposal(it, it.name) }
                                ?: if (operation.amountCents > 0) SortProposal(null, "Hors budget") else null
                        },
                        onAccept = onAccept@{ operation, proposal ->
                            proposal.salary?.let { candidate ->
                                viewModel.confirmSalary(candidate, candidate.paysNextMonth)
                                controller.notify("Salaire reconnu : ${candidate.lastAmount.summary()}.")
                                return@onAccept
                            }
                            viewModel.acceptProposals(listOf(operation.id to proposal.envelope))
                            val envelope = proposal.envelope
                            if (envelope != null) {
                                // Apprendre : « Toujours » retient la règle pour les prochaines.
                                controller.notify("${operation.label} → ${envelope.name}", "Toujours") {
                                    viewModel.sortOperation(operation.id, envelope, suggestedRuleKeyword(operation.bankLabel)) { others ->
                                        controller.notify(
                                            if (others > 0) "Règle retenue, $others autre(s) rangée(s)." else "Règle retenue pour les prochaines.",
                                        )
                                    }
                                }
                            } else {
                                controller.notify("${operation.label} : hors budget", "Annuler") { viewModel.undoLast() }
                            }
                        },
                        onAcceptAll = { all ->
                            // Le salaire se confirme à part, d'un geste explicite.
                            val ready = all.filter { it.second.salary == null }
                            viewModel.acceptProposals(ready.map { (operation, proposal) -> operation.id to proposal.envelope }) { done ->
                                controller.notify("$done opération(s) rangée(s).", "Annuler") { viewModel.undoLast() }
                            }
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
                        pending = bankConnection?.pending.orEmpty(),
                        rulesSummary = "${plural(aliases.size, "nom", "noms")} · ${plural(rules.size + transferRules.size, "règle", "règles")}",
                    )
                }
            }

            // Voile sous la barre d'état, pour que le contenu ne passe pas sous l'heure.
            StatusBarScrim(
                modifier = Modifier.align(Alignment.TopCenter),
                height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 92.dp,
            )
            // Barre du haut : la roue des Réglages, le titre de l'onglet, le + qui ouvre « Ajouter ».
            GlassTopBar(
                title = controller.tab.title,
                onSettings = { settingsOpen = true },
                onAdd = { addOpen = true },
                onSearch = { searchOpen = true },
                settingsBadge = updateAvailable,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Messages de confirmation, en verre, au-dessus de la barre d'onglets.
            SnackbarHost(
                hostState = controller.snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
            ) { data -> GlassSurfaceColors(onBackground = false) { GlassSnackbar(data) } }

            // Barre d'onglets flottante, et le bouton Claude à sa droite (comme la recherche d'iOS 26).
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            GlassTabBar(
                items = Tab.entries.map { GlassTabItem(it.title, painterResource(it.icon)) },
                selectedIndex = controller.tab.ordinal,
                onSelect = {
                    val tab = Tab.entries[it]
                    // Avec des opérations à valider, Dépenses s'ouvre sur la file.
                    if (tab == Tab.OPERATIONS && controller.tab != Tab.OPERATIONS && unsortedCount > 0) {
                        spendingView = SpendingTabView.LIST
                    }
                    controller.tab = tab
                },
                modifier = Modifier.weight(1f),
            )
            ClaudeButton(onClick = { controller.dialog = OpenDialog.ClaudeControl() })
            }
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

        if (addOpen) {
            GlassSurfaceColors(onBackground = false) {
                AddSheet(
                    onPick = { choice ->
                        addOpen = false
                        controller.dialog = when (choice) {
                            AddChoice.EXPENSE -> OpenDialog.Expense
                            AddChoice.PLANNED -> OpenDialog.EnvelopeEditor(
                                fr.baptiste.bank.domain.budget.plannedPurchase("", fr.baptiste.bank.domain.money.Money.ZERO, YearMonth.now().plusMonths(5), LocalDate.now()),
                            )
                            AddChoice.ENVELOPE -> OpenDialog.EnvelopeEditor(null)
                            AddChoice.ASIDE -> envelopes.firstOrNull { it.type == fr.baptiste.bank.domain.model.EnvelopeType.SAVINGS }
                                ?.let { OpenDialog.Move(it.id, incoming = true) }
                                ?: OpenDialog.EnvelopeEditor(null)
                        }
                    },
                    onDismiss = { addOpen = false },
                )
            }
        }
        if (settingsOpen) {
            val salary by viewModel.salary.collectAsStateWithLifecycle()
            val journal by viewModel.journal.collectAsStateWithLifecycle()
            val open: (OpenDialog) -> () -> Unit = { dialog -> { settingsOpen = false; controller.dialog = dialog } }
            GlassSurfaceColors(onBackground = false) {
                SettingsSheet(
                    entries = listOf(
                        SettingsEntry("Salaire", if (salary != null) "Lu à la banque" else "À confirmer", onClick = open(OpenDialog.Income)),
                        SettingsEntry("Banque et synchronisation", bankConnection?.bankName, onClick = open(OpenDialog.Bank)),
                        SettingsEntry("Comptes", "Livrets et placements", onClick = open(OpenDialog.Accounts)),
                        SettingsEntry("Noms et règles", "${plural(aliases.size, "nom", "noms")} · ${plural(rules.size + transferRules.size, "règle", "règles")}", onClick = open(OpenDialog.Rules)),
                        SettingsEntry("Historique", plural(journal.count { !it.undone }, "changement", "changements"), onClick = open(OpenDialog.History)),
                        SettingsEntry("Ajouts et retraits", "Mouvements entre enveloppes", onClick = open(OpenDialog.Journal)),
                        SettingsEntry("Vérifier les dates", if (corrections > 0) plural(corrections, "date à corriger", "dates à corriger") else null, badge = corrections > 0, onClick = open(OpenDialog.DateCheck)),
                        SettingsEntry("Sauvegarder", onClick = open(OpenDialog.Backup)),
                        SettingsEntry("Restaurer", onClick = open(OpenDialog.Restore())),
                        SettingsEntry("Mises à jour", if (updateAvailable) "Nouvelle version disponible" else null, badge = updateAvailable, onClick = open(OpenDialog.Updates)),
                    ),
                    developer = listOf(
                        SettingsEntry("Réglages du verre", onClick = {
                            settingsOpen = false
                            draftTuning = savedTuning
                            tuningOpen = true
                        }),
                    ),
                    version = "Bank · build ${fr.baptiste.bank.BuildConfig.VERSION_CODE}",
                    onDismiss = { settingsOpen = false },
                )
            }
        }
        moveSubject?.let { subject ->
            val subjectId = when (subject) {
                is fr.baptiste.bank.ui.components.MoveSubject.EnvelopeSubject -> subject.envelopeId
                is fr.baptiste.bank.ui.components.MoveSubject.CategorySubject -> treeGroups.firstOrNull { it.name.equals(subject.name, ignoreCase = true) }?.id
            }
            val month = YearMonth.now()
            val tree = fr.baptiste.bank.domain.budget.treeOf(envelopes + archived, treeGroups, month, trackingStart)
            val allIds = (envelopes.map { it.id } + treeGroups.map { it.id }).toSet()
            // Les mêmes destinations que le glissement.
            val destinations = fr.baptiste.bank.domain.budget.budgetRows(envelopes + archived, treeGroups, month, trackingStart, allIds)
                .filter { row -> subjectId == null || tree.canMoveInto(subjectId, row.id) }
                .map { fr.baptiste.bank.ui.components.MoveDestination(it.id, it.name, it.depth, fr.baptiste.bank.ui.theme.categoryColor(it.rootName)) }
            val currentParent = subjectId?.let { id -> (envelopes + archived).firstOrNull { it.id == id }?.parentId ?: treeGroups.firstOrNull { it.id == id }?.parentId }
            GlassSurfaceColors(onBackground = false) {
                fr.baptiste.bank.ui.components.MoveSheet(
                    subject = subject,
                    destinations = destinations,
                    currentParent = currentParent,
                    onPick = { target ->
                        moveSubject = null
                        // La ligne cochée : rien ne change, le panneau se ferme.
                        if (target != currentParent) subjectId?.let { dropInTree(it, fr.baptiste.bank.domain.model.TreeDrop.Into(target)) }
                    },
                    onCreate = { name ->
                        moveSubject = null
                        when (subject) {
                            is fr.baptiste.bank.ui.components.MoveSubject.EnvelopeSubject ->
                                viewModel.moveEnvelope(subject.envelopeId, name) {
                                    controller.notify("${subject.name} rangée dans $name", "Annuler") { viewModel.undoLast() }
                                }
                            is fr.baptiste.bank.ui.components.MoveSubject.CategorySubject ->
                                viewModel.moveCategory(subject.name, name) { done ->
                                    if (done) controller.notify("${subject.name} rangée dans $name", "Annuler") { viewModel.undoLast() }
                                    else controller.notify("Impossible : une catégorie ne va pas dans ses sous-catégories.")
                                }
                        }
                    },
                    onDismiss = { moveSubject = null },
                )
            }
        }
        renaming?.let { name ->
            GlassSurfaceColors(onBackground = false) {
                fr.baptiste.bank.ui.components.RenameCategorySheet(
                    name = name,
                    onRename = { newName ->
                        renaming = null
                        viewModel.renameCategory(name, newName) { done ->
                            if (done) controller.notify("$name renommée $newName", "Annuler") { viewModel.undoLast() }
                        }
                    },
                    onDismiss = { renaming = null },
                )
            }
        }
        if (searchOpen) {
            GlassSurfaceColors(onBackground = false) {
                SearchSheet(
                    month = selectedMonth,
                    envelopes = envelopes,
                    monthOperations = monthOperations,
                    searchAll = viewModel::searchOperations,
                    onOpen = { hit ->
                        searchOpen = false
                        when (hit) {
                            is SearchHit.Category -> {
                                controller.tab = Tab.OPERATIONS
                                spendingView = SpendingTabView.ANALYSIS
                                // La catégorie touchée s'ouvre dépliée, ses catégories mères aussi.
                                expandCategory = hit.name
                            }
                            is SearchHit.EnvelopeHit -> controller.dialog = OpenDialog.EnvelopeDetail(hit.envelope.id)
                            is SearchHit.Operation -> controller.dialog = OpenDialog.SortOperation(hit.row)
                        }
                    },
                    onDismiss = { searchOpen = false },
                )
            }
        }
        if (yearOpen) {
            val yearSpending by viewModel.yearSpending.collectAsStateWithLifecycle()
            GlassSurfaceColors(onBackground = false) {
                YearSheet(
                    year = selectedYear,
                    spending = yearSpending,
                    canNextYear = selectedYear < LocalDate.now().year,
                    onPreviousYear = viewModel::previousYear,
                    onNextYear = viewModel::nextYear,
                    onSelectMonth = {
                        viewModel.selectMonth(it)
                        yearOpen = false
                        if (controller.tab == Tab.SAVINGS) controller.tab = Tab.OPERATIONS
                    },
                    onSort = {
                        yearOpen = false
                        controller.tab = Tab.OPERATIONS
                        spendingView = SpendingTabView.LIST
                    },
                    recurring = recurringExpenses,
                    onDismiss = { yearOpen = false },
                )
            }
        }
    }
}

/** Message de confirmation, sur un fond plein : il se lit sur n'importe quel écran. */
@Composable
private fun GlassSnackbar(data: SnackbarData) {
    // Une capsule pleine, sans verre : le verre est réservé à la barre d'onglets et au bouton Claude.
    Row(
        Modifier
            .fillMaxWidth()
            .background(Glass.BaseTop, androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
            .border(1.dp, Color.White.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
