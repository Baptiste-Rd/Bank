package fr.baptiste.bank.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.baptiste.bank.BankApplication
import fr.baptiste.bank.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val update: AvailableUpdate) : UpdateUiState
    data class NeedsPermission(val update: AvailableUpdate) : UpdateUiState
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateUiState
    data object WaitingForSystem : UpdateUiState
    data class Error(val message: String, val update: AvailableUpdate? = null) : UpdateUiState
}

class UpdateViewModel(
    private val repository: UpdateRepository,
    private val installer: ApkInstaller,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Checking)
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    init {
        check()
        viewModelScope.launch {
            InstallEvents.failures.collect { message ->
                mutableState.value = UpdateUiState.Error(message, lastUpdate())
            }
        }
    }

    fun check() {
        mutableState.value = UpdateUiState.Checking
        viewModelScope.launch {
            mutableState.value = runCatching { repository.latestRelease() }.fold(
                onSuccess = { update ->
                    if (update != null && update.versionCode > currentVersionCode) {
                        UpdateUiState.Available(update)
                    } else {
                        UpdateUiState.UpToDate
                    }
                },
                onFailure = { error ->
                    UpdateUiState.Error("Vérification impossible : ${error.message ?: "erreur réseau"}")
                },
            )
        }
    }

    fun install(update: AvailableUpdate) {
        if (!installer.canInstall()) {
            mutableState.value = UpdateUiState.NeedsPermission(update)
            return
        }
        mutableState.value = UpdateUiState.Downloading(update, progress = 0f)
        viewModelScope.launch {
            runCatching {
                installer.downloadAndInstall(update) { progress ->
                    mutableState.value = UpdateUiState.Downloading(update, progress)
                }
            }.fold(
                onSuccess = { mutableState.value = UpdateUiState.WaitingForSystem },
                onFailure = { error ->
                    mutableState.value = UpdateUiState.Error(
                        "Téléchargement impossible : ${error.message ?: "erreur réseau"}",
                        update,
                    )
                },
            )
        }
    }

    fun permissionSettings() = installer.installPermissionSettings()

    private fun lastUpdate(): AvailableUpdate? = when (val current = mutableState.value) {
        is UpdateUiState.Available -> current.update
        is UpdateUiState.NeedsPermission -> current.update
        is UpdateUiState.Downloading -> current.update
        is UpdateUiState.Error -> current.update
        else -> null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as BankApplication).container
                UpdateViewModel(container.updateRepository, container.apkInstaller)
            }
        }
    }
}
