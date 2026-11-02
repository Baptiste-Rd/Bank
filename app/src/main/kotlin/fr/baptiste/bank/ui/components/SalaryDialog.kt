package fr.baptiste.bank.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.bank.cleanBankLabel
import fr.baptiste.bank.domain.budget.MonthIncome
import fr.baptiste.bank.domain.budget.SalaryCandidate
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.format.formatted
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Le salaire lu à la banque : on choisit une fois lequel des virements reçus chaque mois est le
 * salaire ; plus rien à taper. Versé après le 20, il finance le mois suivant (réglable).
 */
@Composable
fun SalaryDialog(
    candidates: List<SalaryCandidate>,
    /** Salaire confirmé : clé et « finance le mois suivant » ; null sinon. */
    salary: Pair<String, Boolean>?,
    income: MonthIncome?,
    /** Montant tapé autrefois, utilisé tant que rien n'est confirmé. */
    typed: Money?,
    onConfirm: (SalaryCandidate, nextMonth: Boolean) -> Unit,
    onNextMonthChange: (Boolean) -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.FRANCE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salaire") },
        text = {
            BlurBehindWindow()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (salary != null) {
                    val current = candidates.firstOrNull { it.key == salary.first }
                    Text("Lu sur ton compte à chaque versement : rien à taper.", style = MaterialTheme.typography.bodyMedium)
                    current?.let { Line(it) }
                    Text(
                        income?.let { "Revenu de $month : ${it.amount.formatted()}" + if (it.received) ", reçu." else ", attendu (dernier versement)." }
                            ?: "Aucun versement reconnu ces derniers mois.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Finance le mois suivant", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Versé en fin de mois, il sert au mois d'après.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = salary.second, onCheckedChange = onNextMonthChange)
                    }
                    TextButton(onClick = onForget) { Text("Ce n'est plus mon salaire") }
                } else {
                    Text(
                        "L'app lit ton salaire sur ton compte : choisis une fois lequel de ces virements reçus chaque mois c'est.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (candidates.isEmpty()) {
                        Text(
                            "Aucun virement reçu deux mois de suite pour l'instant. Synchronise ton compte : il apparaîtra après deux versements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    candidates.forEachIndexed { index, candidate ->
                        if (index > 0) RowDivider(0.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Line(candidate) }
                            RowActionButton("C'est lui", onClick = { onConfirm(candidate, candidate.paysNextMonth) })
                        }
                    }
                    if (typed != null) {
                        Text(
                            "En attendant, l'app utilise le montant tapé autrefois (${typed.formatted()}). Il sera effacé.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun Line(candidate: SalaryCandidate) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(cleanBankLabel(candidate.label), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${candidate.lastAmount.formatted()} · vers le ${candidate.typicalDay} · vu ${candidate.months} mois",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
