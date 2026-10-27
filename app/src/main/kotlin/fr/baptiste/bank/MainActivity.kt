package fr.baptiste.bank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.baptiste.bank.data.UiStyle
import fr.baptiste.bank.ui.BudgetViewModel
import fr.baptiste.bank.ui.ClassicMainScreen
import fr.baptiste.bank.ui.glass.GlassMainScreen
import fr.baptiste.bank.ui.glass.GlassTheme
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.update.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as BankApplication).container.settingsRepository
        setContent {
            val style by settings.uiStyle.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            val budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory)
            val updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)
            val onStyleChange: (UiStyle) -> Unit = { scope.launch { settings.setUiStyle(it) } }
            when (style) {
                // Premier instant, avant la lecture du réglage : rien à afficher.
                null -> Unit
                UiStyle.GLASS -> GlassTheme {
                    GlassMainScreen(budgetViewModel, updateViewModel, onStyleChange)
                }
                UiStyle.CLASSIC -> BankTheme {
                    ClassicMainScreen(budgetViewModel, updateViewModel, onStyleChange)
                }
            }
        }
    }
}
