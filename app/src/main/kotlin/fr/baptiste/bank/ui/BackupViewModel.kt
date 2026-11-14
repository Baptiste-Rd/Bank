package fr.baptiste.bank.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.baptiste.bank.BankApplication
import fr.baptiste.bank.data.BackupError
import fr.baptiste.bank.data.BackupManifest
import fr.baptiste.bank.data.BackupRepository
import fr.baptiste.bank.data.LoadedBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer

/** Ce que les fenêtres « Sauvegarder » et « Restaurer » affichent. */
data class BackupUiState(
    val busy: Boolean = false,
    val error: String? = null,
    /** Le fichier lu et vérifié, en attente de confirmation : sa date et ce qu'il contient. */
    val loaded: BackupManifest? = null,
)

/** Pilote la sauvegarde et la restauration. Le mot de passe ne fait que passer : il n'est gardé nulle part. */
class BackupViewModel(private val repository: BackupRepository) : ViewModel() {

    private val _ui = MutableStateFlow(BackupUiState())
    val ui: StateFlow<BackupUiState> = _ui.asStateFlow()

    private var loaded: LoadedBackup? = null

    /** À la fermeture d'une fenêtre : plus d'erreur affichée, plus de fichier en attente. */
    fun reset() {
        loaded = null
        _ui.value = BackupUiState()
    }

    fun save(uri: Uri, password: String, onDone: () -> Unit) = act {
        repository.save(uri, chars(password))
        onDone()
    }

    /** Lit le fichier choisi ; rien ne change tant que [restore] n'est pas demandé. */
    fun load(uri: Uri, password: String) = act {
        val backup = repository.load(uri, chars(password))
        loaded = backup
        _ui.update { it.copy(loaded = backup.manifest) }
    }

    /** Les données actuelles à la place d'un fichier lu : la fenêtre de confirmation des captures. */
    fun loadCurrent() = act {
        val backup = repository.current()
        loaded = backup
        _ui.update { it.copy(loaded = backup.manifest) }
    }

    fun restore(onDone: (BackupManifest) -> Unit) = act {
        val backup = loaded ?: return@act
        repository.restore(backup)
        loaded = null
        onDone(backup.manifest)
    }

    /** [onDone] reçoit null quand tout est revenu, sinon la phrase à montrer : la fenêtre est déjà fermée. */
    fun undoRestore(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val failure = try {
                if (repository.undoRestore()) null else BackupError.Unreadable().message
            } catch (error: BackupError) {
                error.message
            }
            onDone(failure)
        }
    }

    /** Supprime la copie gardée par la dernière restauration : elle ne peut plus servir. */
    fun forgetUndo() {
        viewModelScope.launch { repository.forgetUndo() }
    }

    /** Le même mot de passe donne les mêmes caractères, quel que soit le clavier (accents composés ou non). */
    private fun chars(password: String): CharArray = Normalizer.normalize(password, Normalizer.Form.NFC).toCharArray()

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (error: BackupError) {
                _ui.update { it.copy(error = error.message) }
            } catch (error: Exception) {
                _ui.update { it.copy(error = BackupError.Unreadable().message) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as BankApplication).container
                BackupViewModel(container.backupRepository)
            }
        }
    }
}
