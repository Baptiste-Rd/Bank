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
import fr.baptiste.bank.ui.format.label
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
        AccountType.INVESTMENT -> "Placement"
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

/** « Livret · relié à la banque », « Placement · au 3 oct. 2026 · à mettre à jour ». */
fun HoldingLine.detail(today: java.time.LocalDate = java.time.LocalDate.now()): String {
    val origin = if (account.linked) "relié à la banque" else asOf?.let { "au ${it.formatted()}" } ?: "solde à renseigner"
    return "${account.type.label} · $origin" + if (isStale(today)) " · à mettre à jour" else ""
}

/** Un solde saisi à la main il y a plus de 30 jours : à mettre à jour (un compte relié se met à jour seul). */
fun HoldingLine.isStale(today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
    !account.linked && asOf?.isBefore(today.minusDays(30)) == true

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
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(if (option == AccountType.SAVINGS) "Livret (disponible)" else "Placement (PEA, titres)") })
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

/** Saisir le solde du compte courant (sans banque connectée, ou pour corriger la dernière lecture). */
@Composable
fun CurrentBalanceDialog(initial: Money?, onSave: (Money) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.takeIf { !it.isNegative }?.toFieldText().orEmpty()) }
    val amount = if (text.isBlank()) null else parseMoney(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solde du compte courant") },
        text = {
            BlurBehindWindow()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Celui que ta banque affiche aujourd'hui. Avec la banque connectée, il est relu à chaque synchronisation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Solde aujourd'hui") },
                    suffix = { Text("€") },
                    isError = text.isNotBlank() && amount == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { amount?.let(onSave) }, enabled = amount != null) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Objectifs d'épargne : la précaution (sur les livrets), puis le total visé (tous comptes). */
@Composable
fun SavingsGoalsDialog(
    precaution: Money,
    total: Money,
    totalBy: java.time.YearMonth?,
    onSave: (precaution: Money, total: Money, totalBy: java.time.YearMonth?) -> Unit,
    onDismiss: () -> Unit,
) {
    var by by rememberSaveable { mutableStateOf(totalBy?.toString()) }
    var pickingMonth by remember { mutableStateOf(false) }
    var precautionText by rememberSaveable { mutableStateOf(precaution.toFieldText()) }
    var totalText by rememberSaveable { mutableStateOf(total.toFieldText()) }
    val p = parseMoney(precautionText)
    val t = parseMoney(totalText)
    val valid = p != null && t != null && t >= p
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Objectifs d'épargne") },
        text = {
            BlurBehindWindow()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = precautionText,
                    onValueChange = { precautionText = it },
                    label = { Text("Précaution (sur les livrets)") },
                    supportingText = { Text("Repère courant : 2 à 6 mois de revenus, disponibles tout de suite.") },
                    suffix = { Text("€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text("Total visé (tous comptes)") },
                    supportingText = { Text("Pour les projets de vie ; au-delà, c'est pour tes projets personnels.") },
                    suffix = { Text("€") },
                    isError = p != null && t != null && t < p,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Échéance du total : l'app en déduit combien mettre de côté chaque mois.
                Text("Atteindre le total pour", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.OutlinedButton(onClick = { pickingMonth = true }) {
                        Text(by?.let { "fin ${java.time.YearMonth.parse(it).label}" } ?: "Sans échéance")
                    }
                    androidx.compose.material3.DropdownMenu(expanded = pickingMonth, onDismissRequest = { pickingMonth = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Sans échéance") },
                            onClick = { by = null; pickingMonth = false },
                        )
                        val now = java.time.YearMonth.now()
                        (0L..36L).map { now.plusMonths(it) }.forEach { month ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("fin ${month.label}") },
                                onClick = { by = month.toString(); pickingMonth = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (p != null && t != null) onSave(p, t, by?.let(java.time.YearMonth::parse)) },
                enabled = valid,
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
