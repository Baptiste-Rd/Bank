package fr.baptiste.bank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.baptiste.bank.data.DemoData
import fr.baptiste.bank.data.UiStyle
import fr.baptiste.bank.ui.BudgetViewModel
import fr.baptiste.bank.domain.budget.plannedPurchase
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.ClassicMainScreen
import fr.baptiste.bank.ui.OpenDialog
import fr.baptiste.bank.ui.Tab
import fr.baptiste.bank.ui.glass.BudgetView
import fr.baptiste.bank.ui.glass.SpendingTabView
import fr.baptiste.bank.ui.glass.GlassMainScreen
import fr.baptiste.bank.ui.glass.GlassTheme
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.update.UpdateViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Paramètres de lancement facultatifs, utilisés par les captures d'écran automatiques :
 * `demo` (base d'exemple séparée), `style` (GLASS ou CLASSIC, sans enregistrer le choix)
 * `tab` (HOME, BUDGET, SAVINGS ou OPERATIONS), `dialog` (BANK ou PLANNED), `monthsBack`
 * (nombre de mois en arrière), `budgetView` (YEAR pour la vue à l'année, Budget et Analyse) et
 * `spendingView` (LIST pour la liste des opérations de l'onglet Dépenses).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BankApplication).container
        if (intent.getBooleanExtra(EXTRA_DEMO, false)) {
            container.enableDemo()
            lifecycleScope.launch { DemoData.seedIfEmpty(container.database, container.budgetRepository) }
        }
        val forcedStyle = intent.getStringExtra(EXTRA_STYLE)?.let { name -> UiStyle.entries.firstOrNull { it.name == name } }
        val initialTab = intent.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }
            ?: Tab.HOME
        // Fenêtres ouvrables au lancement, pour les captures : la connexion bancaire, un achat prévu.
        val monthsBack = intent.getIntExtra(EXTRA_MONTHS_BACK, 0).coerceIn(0, 36)
        val initialBudgetView = if (intent.getStringExtra(EXTRA_BUDGET_VIEW) == "YEAR") BudgetView.YEAR else BudgetView.MONTH
        val initialSpendingView = if (intent.getStringExtra(EXTRA_SPENDING_VIEW) == "LIST") SpendingTabView.LIST else SpendingTabView.ANALYSIS
        val initialDialog = when (intent.getStringExtra(EXTRA_DIALOG)) {
            "BANK" -> OpenDialog.Bank
            // Formulaire d'un nouvel achat prévu, pré-rempli comme depuis le modèle.
            "PLANNED" -> OpenDialog.EnvelopeEditor(
                plannedPurchase("Vélo", Money.euros(600), YearMonth.now().plusMonths(5), LocalDate.now()),
            )
            // Fiche de la première enveloppe de la démo (Courses), avec ses dépenses.
            "ENVELOPE" -> OpenDialog.EnvelopeDetail(1)
            // Fiche d'une provision annuelle de la démo (Vacances), avec sa proposition de rattrapage.
            "PROVISION" -> OpenDialog.EnvelopeDetail(9)
            // Simulateur, avec le projet de l'exemple : 2 000 € en 3 mois.
            "PLAN" -> OpenDialog.TryPlan("Voyage", Money.euros(2000))
            // Mettre de côté sur l'épargne de la démo : la réserve est proposée en premier.
            "RESERVE" -> OpenDialog.Move(10, incoming = true)
            "UNSORT" -> OpenDialog.Unsort
            "RULES" -> OpenDialog.Rules
            "ASSISTED" -> OpenDialog.AssistedSort()
            "ACCOUNTS" -> OpenDialog.Accounts
            // Validation du tri assisté, avec une réponse type de Claude pour les trois opérations de la démo.
            "ASSISTED_REVIEW" -> OpenDialog.AssistedSort(demoReply = DEMO_ASSISTANT_REPLY)
            // Un nom déjà pris (« courses » existe) : le formulaire le signale.
            "DUPLICATE" -> OpenDialog.EnvelopeEditor(
                fr.baptiste.bank.domain.model.Envelope(
                    id = 0,
                    name = "courses",
                    type = fr.baptiste.bank.domain.model.EnvelopeType.DAILY,
                    frequency = fr.baptiste.bank.domain.model.EnvelopeType.DAILY.defaultFrequency,
                    amount = Money.euros(50),
                    status = fr.baptiste.bank.domain.model.EnvelopeType.DAILY.defaultStatus,
                    anchor = null,
                ),
            )
            else -> null
        }
        val initialTuningOpen = intent.getBooleanExtra(EXTRA_TUNING, false)
        val settings = container.settingsRepository

        setContent {
            val savedStyle by settings.uiStyle.collectAsState(initial = null)
            val style = forcedStyle ?: savedStyle
            val scope = rememberCoroutineScope()
            val budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory)
            val updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)
            val onStyleChange: (UiStyle) -> Unit = { scope.launch { settings.setUiStyle(it) } }
            // Captures : se placer quelques mois en arrière dans Budget et Épargne.
            LaunchedEffect(Unit) { repeat(monthsBack) { budgetViewModel.previousMonth() } }
            // L'interface verre est toujours sombre : icônes de la barre d'état en blanc.
            LaunchedEffect(style) {
                if (style == UiStyle.GLASS) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    )
                } else if (style == UiStyle.CLASSIC) {
                    enableEdgeToEdge()
                }
            }
            when (style) {
                // Premier instant, avant la lecture du réglage : rien à afficher.
                null -> Unit
                UiStyle.GLASS -> GlassTheme {
                    GlassMainScreen(
                        budgetViewModel,
                        updateViewModel,
                        onStyleChange,
                        initialTab,
                        initialDialog,
                        initialBudgetView,
                        initialTuningOpen,
                        initialSpendingView,
                    )
                }
                UiStyle.CLASSIC -> BankTheme {
                    ClassicMainScreen(budgetViewModel, updateViewModel, onStyleChange, initialTab, initialDialog)
                }
            }
        }
    }

    private companion object {
        const val EXTRA_DEMO = "demo"
        const val EXTRA_STYLE = "style"
        const val EXTRA_TAB = "tab"
        const val EXTRA_DIALOG = "dialog"
        const val EXTRA_MONTHS_BACK = "monthsBack"
        const val EXTRA_BUDGET_VIEW = "budgetView"
        const val EXTRA_SPENDING_VIEW = "spendingView"
        const val EXTRA_TUNING = "tuning"

        private val DEMO_ASSISTANT_REPLY = """
            Phase 1 et 2 faites. Voici le tableau :
            ```json
            [
              {"n": 1, "nom": "Free Mobile", "enveloppe": "Téléphone", "nouvelle": true, "famille": "charges fixes", "motcle": "Free Mobile", "confiance": "haute", "raison": "Forfait mobile mensuel prélevé par Free."},
              {"n": 2, "nom": "Carrefour City", "enveloppe": "Courses", "nouvelle": false, "famille": "quotidien", "motcle": "Carrefour City", "confiance": "haute", "raison": "Supérette alimentaire."},
              {"n": 3, "nom": "Salaire", "enveloppe": "HORS BUDGET", "nouvelle": false, "famille": null, "motcle": "Salaire", "confiance": "moyenne", "raison": "Virement reçu de l'employeur."}
            ]
            ```
        """.trimIndent()
    }
}
