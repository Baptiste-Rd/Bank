package fr.baptiste.bank.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.baptiste.bank.BuildConfig
import fr.baptiste.bank.R
import fr.baptiste.bank.ui.theme.BankTheme

/**
 * Fenêtre des mises à jour, ouverte depuis le menu ⋮. La vérification elle-même a lieu
 * au démarrage de l'app : le menu signale déjà une nouvelle version avant l'ouverture.
 */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Au retour des paramètres, on relance l'installation si l'autorisation vient d'être accordée.
    LifecycleResumeEffect(state) {
        (state as? UpdateUiState.NeedsPermission)?.let { viewModel.install(it.update) }
        onPauseOrDispose { }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mises à jour") },
        text = {
            UpdateCard(
                state = state,
                onCheck = viewModel::check,
                onInstall = viewModel::install,
                onOpenSettings = { context.startActivity(viewModel.permissionSettings()) },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
fun UpdateCard(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onInstall: (AvailableUpdate) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    run {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.update_current_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                UpdateUiState.Checking -> Text(stringResource(R.string.update_checking))
                UpdateUiState.UpToDate -> StatusRow(
                    message = stringResource(R.string.update_up_to_date),
                    action = stringResource(R.string.update_check_again),
                    onAction = onCheck,
                )
                is UpdateUiState.Available -> {
                    Text(
                        text = stringResource(R.string.update_available, state.update.versionCode),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.update.notes.isNotBlank()) {
                        Text(state.update.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onInstall(state.update) }) {
                        Text(stringResource(R.string.update_install))
                    }
                }
                is UpdateUiState.NeedsPermission -> {
                    Text(stringResource(R.string.update_needs_permission))
                    Button(onClick = onOpenSettings) {
                        Text(stringResource(R.string.update_open_settings))
                    }
                }
                is UpdateUiState.Downloading -> {
                    Text(stringResource(R.string.update_downloading))
                    val progress = state.progress
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
                UpdateUiState.WaitingForSystem -> Text(stringResource(R.string.update_waiting))
                is UpdateUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    val update = state.update
                    StatusRow(
                        message = null,
                        action = stringResource(R.string.update_retry),
                        onAction = { if (update != null) onInstall(update) else onCheck() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(message: String?, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(message.orEmpty(), modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Preview
@Composable
private fun UpdateCardAvailablePreview() {
    BankTheme {
        UpdateCard(
            state = UpdateUiState.Available(
                AvailableUpdate(
                    versionCode = 12,
                    tag = "build-12",
                    apkUrl = "",
                    apkSizeBytes = 0,
                    notes = "Import CSV du CIC",
                ),
            ),
            onCheck = {},
            onInstall = {},
            onOpenSettings = {},
        )
    }
}
