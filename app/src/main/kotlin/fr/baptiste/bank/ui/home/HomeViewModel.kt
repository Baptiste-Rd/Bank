package fr.baptiste.bank.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.baptiste.bank.BankApplication
import fr.baptiste.bank.data.local.EnvelopeDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** État de l'écran d'accueil. Le calcul du disponible du jour arrivera avec l'import. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Ready(val envelopeCount: Int) : HomeUiState
}

class HomeViewModel(envelopeDao: EnvelopeDao) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = envelopeDao.observeActive()
        .map { envelopes ->
            if (envelopes.isEmpty()) HomeUiState.Empty else HomeUiState.Ready(envelopes.size)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as BankApplication
                HomeViewModel(application.container.database.envelopeDao())
            }
        }
    }
}
