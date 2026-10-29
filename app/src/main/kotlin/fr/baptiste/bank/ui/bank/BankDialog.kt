package fr.baptiste.bank.ui.bank

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.baptiste.bank.banking.Aspsp
import fr.baptiste.bank.banking.BankConnection
import fr.baptiste.bank.banking.BankViewModel
import fr.baptiste.bank.banking.DEFAULT_REDIRECT_URL
import fr.baptiste.bank.domain.bank.balanceTypeLabel
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.format.formatted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTime = DateTimeFormatter.ofPattern("d MMMM 'à' HH:mm", Locale.FRANCE)
private val dateOnly = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
private fun Instant.local() = atZone(ZoneId.systemDefault())

/**
 * Écran de la connexion bancaire, en plein écran. Il suit l'état de la connexion :
 * 1. pas encore configurée : la marche à suivre dans Enable Banking, l'identifiant et la clé ;
 * 2. configurée : le choix de la banque, puis son authentification ;
 * 3. connectée : le compte, le solde, la dernière synchronisation et la validité du consentement.
 */
@Composable
fun BankDialog(viewModel: BankViewModel, onDismiss: () -> Unit) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BlurBehindWindow()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Banque", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    if (ui.busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
                ui.error?.let { error ->
                    Notice(error, isError = true)
                }
                val current = connection
                when {
                    !viewModel.available -> Text(
                        "La connexion bancaire n'est pas disponible en mode démo : les données d'exemple " +
                            "ne doivent jamais se mêler aux vraies.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    current == null -> Unit
                    ui.accountsToChoose.isNotEmpty() -> AccountChoice(viewModel)
                    current.isConnected -> ConnectedSection(current, viewModel)
                    current.isConfigured -> BankChoice(current, ui.banks, viewModel)
                    else -> SetupSection(current, viewModel)
                }
            }
        }
    }

    val authUrl = ui.authUrl
    val redirect = connection?.redirectUrl
    if (authUrl != null && redirect != null) {
        BankAuthPage(
            url = authUrl,
            redirectUrl = redirect,
            onRedirect = viewModel::onRedirect,
            onCancel = viewModel::cancelAuthorization,
        )
    }
}

@Composable
private fun Notice(text: String, isError: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

@Composable
private fun Step(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$number", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Étape 1 : créer l'application chez Enable Banking, puis renseigner l'identifiant et la clé ici. */
@Composable
private fun SetupSection(connection: BankConnection, viewModel: BankViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var applicationId by rememberSaveable { mutableStateOf(connection.applicationId.orEmpty()) }
    var redirectUrl by rememberSaveable { mutableStateOf(connection.redirectUrl) }
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importKey(uri)
    }

    Text(
        "L'app lit tes opérations via Enable Banking, un service agréé d'accès aux comptes. En « mode " +
            "restreint », gratuit, il ne donne accès qu'à tes propres comptes, que tu as liés toi-même. " +
            "La configuration se fait une seule fois.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Step(1, "Crée ton accès Enable Banking") {
        Hint("Inscris-toi avec ton e-mail, puis ouvre « API applications ».")
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://enablebanking.com/cp/applications"))) }
        }) { Text("Ouvrir Enable Banking") }
    }
    Step(2, "Ajoute une application") {
        Hint(
            "Environnement « Production », un nom (par exemple « Budget perso »), et cette adresse de " +
                "redirection, à l'identique :",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                DEFAULT_REDIRECT_URL,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(DEFAULT_REDIRECT_URL)) }) { Text("Copier") }
        }
        Hint(
            "Politique de confidentialité et conditions : une adresse quelconque suffit, elles ne sont pas " +
                "vérifiées en mode restreint. En validant, ton navigateur télécharge un fichier .pem : " +
                "c'est la clé privée. Ne l'envoie à personne.",
        )
    }
    Step(3, "Lie ton compte CIC") {
        Hint(
            "Sur l'application créée, « Link accounts » : choisis CIC et connecte-toi. L'application passe " +
                "alors en « Restricted » et « Active ».",
        )
    }
    Step(4, "Renseigne l'application ici") {
        OutlinedTextField(
            value = applicationId,
            onValueChange = { applicationId = it },
            label = { Text("Application ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = redirectUrl,
            onValueChange = { redirectUrl = it },
            label = { Text("Adresse de redirection déclarée") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickKey.launch(arrayOf("*/*")) }) {
                Text(if (connection.hasKey) "Remplacer la clé" else "Importer la clé (.pem)")
            }
            if (connection.hasKey) Hint("Clé importée, chiffrée sur ce téléphone.")
        }
        Hint("Une fois importée, tu peux supprimer le .pem de tes téléchargements ; garde une copie en lieu sûr.")
        Button(
            onClick = { viewModel.saveApplication(applicationId, redirectUrl) },
            enabled = applicationId.isNotBlank(),
        ) { Text("Enregistrer") }
    }
}

/** Étape 2 : choisir la banque (CIC en tête) et lancer l'authentification. */
@Composable
private fun BankChoice(connection: BankConnection, banks: List<Aspsp>, viewModel: BankViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (banks.isEmpty()) viewModel.loadBanks() }

    if (connection.isExpired) {
        Notice(
            "Ton autorisation a expiré le ${connection.validUntil?.local()?.format(dateOnly)}. " +
                "Reconnecte-toi à ta banque pour reprendre la synchronisation.",
        )
    }
    Text(
        "Choisis ta banque. Tu vas t'authentifier sur sa page, comme sur son site, puis valider dans son app. " +
            "L'autorisation dure jusqu'à 180 jours.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Rechercher") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val filtered = banks
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedBy { if (it.name.equals("CIC", ignoreCase = true)) "" else it.name.lowercase() }
        .take(40)
    filtered.forEach { bank ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.connect(bank) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(bank.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("Se connecter", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (banks.isEmpty()) TextButton(onClick = viewModel::loadBanks) { Text("Charger la liste des banques") }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = viewModel::forgetAll) { Text("Effacer l'identifiant et la clé") }
}

/** Après l'authentification : plusieurs comptes autorisés, on choisit celui des dépenses. */
@Composable
private fun AccountChoice(viewModel: BankViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Text(
        "Quel compte porte tes dépenses courantes ? Ses opérations seront importées dans l'app.",
        style = MaterialTheme.typography.bodyMedium,
    )
    ui.accountsToChoose.forEach { account ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.chooseAccount(account) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(account.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("Choisir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Étape 3 : connecté. */
@Composable
private fun ConnectedSection(connection: BankConnection, viewModel: BankViewModel) {
    var confirmDisconnect by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(connection.bankName ?: "Banque", style = MaterialTheme.typography.titleMedium)
            Text(connection.accountLabel ?: "Compte", style = MaterialTheme.typography.bodyMedium)
            connection.balanceCents?.let {
                Text("Solde : ${Money(it).formatted()}", style = MaterialTheme.typography.titleLarge)
            }
            // D'où vient ce solde : la banque en fournit souvent plusieurs, pas toujours celui de son appli.
            if (connection.bankBalances.isNotEmpty()) {
                Hint(
                    buildString {
                        append("Soldes fournis par la banque : ")
                        append(
                            connection.bankBalances.entries.joinToString(", ") { (type, cents) ->
                                "${balanceTypeLabel(type)} ($type) ${Money(cents).formatted()}"
                            },
                        )
                        append(".")
                        if (connection.pendingApplied != 0L) {
                            append(" Opérations en attente retirées : ${Money(connection.pendingApplied).formatted()}.")
                        }
                        connection.balanceType?.let { append(" Solde retenu : ${balanceTypeLabel(it)}.") }
                    },
                )
            }
            Hint(
                connection.lastSync?.let { "Dernière synchronisation le ${it.local().format(dateTime)}." }
                    ?: "Pas encore synchronisé.",
            )
            connection.validUntil?.let {
                Hint("Autorisation valable jusqu'au ${it.local().format(dateOnly)} ; il faudra ensuite te reconnecter.")
            }
        }
    }
    Button(onClick = viewModel::syncNow) { Text("Synchroniser maintenant") }
    Hint(
        "L'app synchronise aussi d'elle-même à l'ouverture, au plus toutes les 6 heures : les banques limitent " +
            "à quatre les consultations automatiques par jour. Les nouvelles opérations sont rapprochées de " +
            "tes saisies, rangées par tes règles, et le reste attend dans l'onglet Opérations.",
    )
    OutlinedButton(onClick = viewModel::fetchHistory) { Text("Récupérer l'historique depuis le 1er janvier") }
    Hint(
        (connection.historyOldest?.let { "Historique reçu jusqu'au ${it.format(dateOnly)}. " } ?: "") +
            "Les banques donnent souvent 90 jours ; au-delà, certaines demandent une connexion récente : " +
            "si elle refuse, reconnecte la banque puis réessaie aussitôt. Les opérations déjà là ne sont pas doublées.",
    )
    OutlinedButton(onClick = { confirmDisconnect = true }) { Text("Déconnecter la banque") }

    if (confirmDisconnect) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Déconnecter la banque ?") },
            text = {
                BlurBehindWindow()
                Text(
                    "L'autorisation est révoquée. Les opérations déjà importées restent dans l'app. " +
                        "Tu pourras te reconnecter à tout moment.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnect()
                }) { Text("Déconnecter") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Annuler") } },
        )
    }
}
