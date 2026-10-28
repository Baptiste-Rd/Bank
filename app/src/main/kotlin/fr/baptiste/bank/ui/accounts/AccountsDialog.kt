package fr.baptiste.bank.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.HoldingLine
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.ui.components.toFieldText

/*
 * Autres comptes : livrets, compte d'investissement. Deux sortes :
 * - saisi à la main (Trade Republic, un PEA...) : on renseigne le solde quand on veut ;
 * - relié à la banque (livret du CIC autorisé lors de la connexion) : le solde se met à jour seul.
 * Un compte n'a pas d'opérations ici : seulement des soldes datés, pour suivre ce qui est de côté.
 */

/** Nom affiché d'un type de compte. */
val AccountType.label: String
    get() = when (this) {
        AccountType.SAVINGS -> "Livret"
        AccountType.INVESTMENT -> "Investissement"
        AccountType.CURRENT -> "Compte courant"
    }

/** La liste des comptes, et l'ajout d'un compte saisi à la main. */
@Composable
fun AccountsDialog(
    lines: List<HoldingLine>,
    onOpen: (HoldingLine) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mes comptes") },
        text = {
            BlurBehindWindow()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Livrets et investissements, pour voir tout ce que tu as de côté. Un compte autorisé lors " +
                        "de la connexion à ta banque se met à jour seul ; les autres, tu donnes le solde quand tu veux.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                lines.forEachIndexed { index, line ->
                    if (index > 0) RowDivider(0.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(line) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.account.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                line.detail(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(line.balance?.formatted() ?: "–", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text("Ajouter un compte") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** « Livret · relié à la banque », « Investissement · au 3 oct. 2026 ». */
fun HoldingLine.detail(): String {
    val origin = if (account.linked) "relié à la banque" else asOf?.let { "au ${it.formatted()}" } ?: "solde à renseigner"
    return "${account.type.label} · $origin"
}

/**
 * Créer ou modifier un compte. Pour un compte relié, seuls le nom et le type se modifient : le
 * solde vient de la banque.
 */
@Composable
fun AccountEditDialog(
    line: HoldingLine?,
    onSave: (name: String, type: AccountType, balance: Money?) -> Unit,
    onArchive: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(line?.account?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(line?.account?.type ?: AccountType.SAVINGS) }
    var balanceText by rememberSaveable { mutableStateOf(line?.balance?.toFieldText().orEmpty()) }
    var confirmArchive by remember { mutableStateOf(false) }
    val linked = line?.account?.linked == true
    val balance = if (balanceText.isBlank()) null else parseMoney(balanceText)
    val canSave = name.isNotBlank() && (linked || balance != null)

    if (confirmArchive && onArchive != null) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Retirer ${line?.account?.name} ?") },
            text = { Text("Il disparaît de la liste et du total ; ses soldes passés sont gardés.") },
            confirmButton = { TextButton(onClick = onArchive) { Text("Retirer") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Annuler") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (line == null) "Nouveau compte" else line.account.name) },
        text = {
            BlurBehindWindow()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    placeholder = { Text("Ex. Trade Republic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AccountType.SAVINGS, AccountType.INVESTMENT).forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option.label) })
                    }
                }
                if (linked) {
                    Text(
                        "Solde lu à la banque : ${line?.balance?.formatted() ?: "pas encore reçu"}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = { Text("Solde aujourd'hui") },
                        suffix = { Text("€") },
                        isError = balanceText.isNotBlank() && balance == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (line != null) {
                        Text(
                            "Le nouveau solde est daté d'aujourd'hui ; les précédents restent dans l'historique.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onArchive != null) {
                    TextButton(onClick = { confirmArchive = true }) { Text("Retirer ce compte") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, type, if (linked) null else balance) }, enabled = canSave) {
                Text("Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
