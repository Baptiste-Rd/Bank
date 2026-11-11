package fr.baptiste.bank.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.BackupFile
import fr.baptiste.bank.data.BackupManifest
import fr.baptiste.bank.ui.BackupUiState
import fr.baptiste.bank.ui.format.plural
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val savedDay = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/** « 3 novembre 2026 » : le jour où le fichier a été écrit. */
fun BackupManifest.savedDay(): String = Instant.ofEpochMilli(savedAt).atZone(ZoneId.systemDefault()).format(savedDay)

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, isError: Boolean = false, help: String? = null) {
    val supporting: (@Composable () -> Unit)? = if (help != null) {
        { Text(help) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting,
        isError = isError,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Progress(state: BackupUiState) {
    state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

/**
 * « Sauvegarder » : un mot de passe, tapé deux fois (une faute de frappe rendrait le fichier
 * illisible), puis le dossier, choisi dans le sélecteur de fichiers du téléphone.
 */
@Composable
fun SaveBackupDialog(state: BackupUiState, onSave: (Uri, String) -> Unit, onDismiss: () -> Unit) {
    // Jamais dans l'état enregistré de l'écran : le mot de passe ne vit que le temps de la fenêtre.
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null && password.isNotEmpty()) onSave(uri, password)
    }
    val different = again.isNotEmpty() && again != password
    SheetAlert(
        onDismissRequest = onDismiss,
        title = { Text("Sauvegarder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Toutes tes données dans un fichier chiffré, à garder hors du téléphone. La clé de la banque n'y est pas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordField(password, { password = it }, "Mot de passe")
                PasswordField(
                    again,
                    { again = it },
                    "Encore une fois",
                    isError = different,
                    help = if (different) "Les deux mots de passe diffèrent." else "Sans lui, le fichier ne se lit plus.",
                )
                Progress(state)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { create.launch("bank-${LocalDate.now()}.${BackupFile.EXTENSION}") },
                enabled = password.isNotEmpty() && password == again && !state.busy,
            ) { Text("Sauvegarder", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * « Restaurer » : le fichier (choisi dès l'ouverture si [pickAtOpen]), son mot de passe, puis une
 * confirmation qui dit de quand il date et ce qu'il contient. Rien ne change avant elle.
 */
@Composable
fun RestoreBackupDialog(
    state: BackupUiState,
    pickAtOpen: Boolean,
    onLoad: (Uri, String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked: Uri? ->
        if (picked != null) {
            uri = picked
        } else if (uri == null) {
            // Aucun fichier choisi : il n'y a rien à restaurer.
            onDismiss()
        }
    }
    LaunchedEffect(Unit) {
        if (pickAtOpen && !asked) {
            asked = true
            // Une sauvegarde n'a pas de type connu du téléphone : tous les fichiers sont proposés.
            pick.launch(arrayOf("*/*"))
        }
    }

    val found = state.loaded
    if (found != null) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) onDismiss() },
            title = { Text("Remplacer tes données ?") },
            text = {
                BlurBehindWindow()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fichier du ${found.savedDay()} : ${plural(found.envelopes, "enveloppe", "enveloppes")}, ${plural(found.operations, "opération", "opérations")}.")
                    Text("Ce qui est sur ce téléphone est remplacé.")
                    Progress(state)
                }
            },
            confirmButton = { TextButton(onClick = onRestore, enabled = !state.busy) { Text("Restaurer") } },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Annuler") } },
        )
        return
    }
    // Le sélecteur de fichiers est devant : rien à montrer tant qu'aucun fichier n'est choisi.
    if (pickAtOpen && uri == null) return
    SheetAlert(
        onDismissRequest = onDismiss,
        title = { Text("Restaurer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(password, { password = it }, "Mot de passe", help = "Celui choisi au moment de sauvegarder.")
                Progress(state)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { uri?.let { onLoad(it, password) } },
                enabled = uri != null && password.isNotEmpty() && !state.busy,
            ) { Text("Continuer", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
