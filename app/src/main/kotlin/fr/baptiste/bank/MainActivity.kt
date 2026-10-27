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
import fr.baptiste.bank.ui.ClassicMainScreen
import fr.baptiste.bank.ui.OpenDialog
import fr.baptiste.bank.ui.Tab
import fr.baptiste.bank.ui.glass.GlassMainScreen
import fr.baptiste.bank.ui.glass.GlassTheme
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.update.UpdateViewModel
import kotlinx.coroutines.launch

/**
 * Paramètres de lancement facultatifs, utilisés par les captures d'écran automatiques :
 * `demo` (base d'exemple séparée), `style` (GLASS ou CLASSIC, sans enregistrer le choix)
 * `tab` (HOME, ENVELOPES ou OPERATIONS) et `dialog` (BANK).
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
        // Seule fenêtre ouvrable au lancement, pour les captures : la connexion bancaire.
        val initialDialog = if (intent.getStringExtra(EXTRA_DIALOG) == "BANK") OpenDialog.Bank else null
        val settings = container.settingsRepository

        setContent {
            val savedStyle by settings.uiStyle.collectAsState(initial = null)
            val style = forcedStyle ?: savedStyle
            val scope = rememberCoroutineScope()
            val budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory)
            val updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)
            val onStyleChange: (UiStyle) -> Unit = { scope.launch { settings.setUiStyle(it) } }
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
                    GlassMainScreen(budgetViewModel, updateViewModel, onStyleChange, initialTab, initialDialog)
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
    }
}
