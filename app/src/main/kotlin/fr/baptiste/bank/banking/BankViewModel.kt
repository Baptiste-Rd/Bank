package fr.baptiste.bank.banking

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.baptiste.bank.BankApplication
import fr.baptiste.bank.data.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que l'écran de connexion bancaire affiche en plus de l'état enregistré. */
data class BankUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val banks: List<Aspsp> = emptyList(),
    /** Page d'authentification de la banque à afficher, pendant la connexion. */
    val authUrl: String? = null,
    /** Comptes autorisés, à choisir juste après l'authentification. */
    val accountsToChoose: List<LinkedAccount> = emptyList(),
)

/**
 * Pilote la connexion bancaire. [repository] est null en mode démo : l'écran l'explique et
 * aucune action n'est possible.
 */
class BankViewModel(private val repository: BankSyncRepository?) : ViewModel() {

    val available: Boolean get() = repository != null

    val connection: StateFlow<BankConnection?> = (repository?.connection ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(BankUiState())
    val ui: StateFlow<BankUiState> = _ui.asStateFlow()

    /** Message à afficher en bas de l'écran après une synchronisation, puis effacé. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    fun saveApplication(applicationId: String, redirectUrl: String) = launchAction {
        repository?.saveApplication(applicationId, redirectUrl)
    }

    fun importKey(uri: Uri) = launchAction { repository?.importKey(uri) }

    fun loadBanks() = launchAction {
        val banks = repository?.banks().orEmpty()
        _ui.update { it.copy(banks = banks) }
    }

    fun connect(bank: Aspsp) = launchAction {
        val url = repository?.startAuthorization(bank) ?: return@launchAction
        _ui.update { it.copy(authUrl = url) }
    }

    /** L'adresse de retour a été atteinte (ou collée par l'utilisateur) : on termine. */
    fun onRedirect(url: String) = launchAction {
        _ui.update { it.copy(authUrl = null) }
        val accounts = repository?.completeAuthorization(url).orEmpty()
        if (accounts.size == 1) {
            report(repository?.chooseAccount(accounts.single()))
        } else {
            _ui.update { it.copy(accountsToChoose = accounts) }
        }
    }

    fun cancelAuthorization() = _ui.update { it.copy(authUrl = null) }

    fun chooseAccount(account: LinkedAccount) = launchAction {
        _ui.update { it.copy(accountsToChoose = emptyList()) }
        report(repository?.chooseAccount(account))
    }

    fun syncNow() = launchAction { report(repository?.sync()) }

    /** Récupère l'historique depuis le 1er janvier et dit jusqu'où la banque est remontée. */
    fun fetchHistory() = launchAction {
        val result = repository?.fetchHistory() ?: return@launchAction
        val format = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRANCE)
        _message.value = buildString {
            if (result.refused != null) {
                append("La banque refuse de remonter au ${result.requested.format(format)}. ")
            }
            append(
                result.oldest?.let { "Historique reçu jusqu'au ${it.format(format)}" }
                    ?: "Aucune opération reçue",
            )
            val added = result.import.added
            append(if (added == 0) " ; rien de nouveau." else " : $added opération(s) ajoutée(s), ${result.import.toSort} à trier.")
        }
    }

    /** Synchronisation discrète à l'ouverture de l'app, si la dernière date de plus de 6 heures. */
    fun syncIfDue() {
        val repository = repository ?: return
        viewModelScope.launch {
            runCatching { if (repository.isSyncDue()) repository.sync() else null }
                .onSuccess { result -> if (result != null && result.added > 0) report(result) }
        }
    }

    fun disconnect() = launchAction { repository?.disconnect() }

    fun forgetAll() = launchAction { repository?.forgetAll() }

    private fun report(result: ImportResult?) {
        if (result == null) return
        _message.value = when {
            result.added == 0 -> "Banque à jour : aucune nouvelle opération."
            else -> buildString {
                append(if (result.added > 1) "${result.added} nouvelles opérations" else "1 nouvelle opération")
                val parts = buildList {
                    if (result.reconciled > 0) add("${result.reconciled} rapprochée(s) de tes saisies")
                    if (result.sortedByRule > 0) add("${result.sortedByRule} rangée(s) par tes règles")
                    if (result.toSort > 0) add("${result.toSort} à trier")
                }
                if (parts.isNotEmpty()) append(" : ").append(parts.joinToString(", "))
                append(".")
            }
        }
    }

    /** Lance une action en affichant l'attente, et transforme une erreur en message lisible. */
    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: error.javaClass.simpleName) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as BankApplication).container
                BankViewModel(container.bankSyncRepository)
            }
        }
    }
}
