package fr.baptiste.bank.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.bank.CardDebit
import fr.baptiste.bank.domain.bank.DateCheckLine
import fr.baptiste.bank.domain.bank.DateCorrection
import fr.baptiste.bank.domain.bank.DateReport
import fr.baptiste.bank.domain.bank.DateSource
import fr.baptiste.bank.domain.bank.cleanBankLabel
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.components.StatusChip
import fr.baptiste.bank.ui.components.StatusKind
import fr.baptiste.bank.ui.format.formatted
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
private fun LocalDate.short(): String = format(dayMonth)

/** Opérations listées pour la comparaison au relevé de la banque. */
private const val RECENT_LINES = 20

/**
 * « Vérifier les dates » : comment la banque date tes opérations, et les jours à corriger.
 * Rien ne change sans « Appliquer » ; on peut décocher une correction.
 */
@Composable
fun DateCheckDialog(
    report: DateReport?,
    onApply: (List<DateCorrection>) -> Unit,
    onDismiss: () -> Unit,
) {
    var excluded by remember { mutableStateOf(emptySet<Long>()) }
    val selected = report?.corrections.orEmpty().filter { it.operation.id !in excluded }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vérifier les dates") },
        text = {
            BlurBehindWindow()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (report == null) {
                    Hint("Lecture des opérations…")
                    return@Column
                }
                if (report.lines.isEmpty()) {
                    Hint("Aucune opération de la banque pour l'instant. Synchronise d'abord le compte.")
                    return@Column
                }
                Section("Ta carte")
                Text(report.debitSentence(), style = MaterialTheme.typography.bodyMedium)
                Hint(report.sourceSentence())
                if (report.cardStatements.isNotEmpty()) {
                    val first = report.cardStatements.first()
                    StatusChip("À vérifier", StatusKind.WARNING, Modifier.padding(top = 4.dp))
                    Text(
                        "${report.cardStatements.size} ligne(s) ressemblent au prélèvement global de la carte " +
                            "(« ${cleanBankLabel(first.rawLabel)} », ${Money(kotlin.math.abs(first.amountCents)).formatted()}). " +
                            "Si tes achats sont aussi détaillés, classe-la hors budget pour ne pas compter deux fois.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Section("Jours à corriger")
                if (report.corrections.isEmpty()) {
                    Hint("Aucun : chaque opération porte déjà le jour de l'achat.")
                } else {
                    val moving = report.corrections.count { it.changesMonth }
                    Hint(
                        "${report.corrections.size} opération(s)" +
                            (if (moving > 0) ", dont $moving changent de mois" else "") +
                            ". Décoche celles que tu veux garder telles quelles.",
                    )
                    report.corrections.forEachIndexed { index, correction ->
                        if (index > 0) RowDivider(0.dp)
                        CorrectionLine(
                            correction = correction,
                            checked = correction.operation.id !in excluded,
                            onCheckedChange = { checked ->
                                excluded = if (checked) excluded - correction.operation.id else excluded + correction.operation.id
                            },
                        )
                    }
                }

                Section("Dernières opérations")
                Hint("Compare-les à ton relevé : le jour affiché en premier doit être celui de l'achat.")
                report.lines.take(RECENT_LINES).forEachIndexed { index, line ->
                    if (index > 0) RowDivider(0.dp)
                    OperationLine(line)
                }
            }
        },
        confirmButton = {
            if (selected.isNotEmpty()) {
                TextButton(onClick = { onApply(selected) }) { Text("Appliquer (${selected.size})") }
            } else {
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
        dismissButton = if (selected.isNotEmpty()) {
            { TextButton(onClick = onDismiss) { Text("Plus tard") } }
        } else {
            null
        },
    )
}

private fun DateReport.debitSentence(): String = when (debit) {
    CardDebit.DEFERRED ->
        "Débit différé : tes paiements par carte arrivent le plus souvent $medianCardDelay jours après l'achat " +
            "(jusqu'à $maxCardDelay). L'app les date du jour de l'achat."
    CardDebit.IMMEDIATE ->
        "Débit immédiat : tes paiements par carte arrivent le plus souvent ${medianCardDelay ?: 0} jour(s) après l'achat."
    CardDebit.UNKNOWN ->
        if (cardCount == 0) {
            "Pas encore de paiement par carte avec son jour d'inscription. Il se complète à chaque synchronisation."
        } else {
            "Pas encore assez de paiements par carte pour conclure ($cardCount sur 5, " +
                "le plus souvent $medianCardDelay jour(s) après l'achat)."
        }
}

private fun DateReport.sourceSentence(): String {
    val bank = bySource[DateSource.BANK] ?: 0
    val label = bySource[DateSource.LABEL] ?: 0
    val booking = bySource[DateSource.BOOKING] ?: 0
    return "Jour de l'achat : $bank donné par la banque, $label lu dans le libellé, " +
        "$booking faute de mieux au jour d'inscription."
}

private fun DateSource.word(): String = when (this) {
    DateSource.BANK -> "donné par la banque"
    DateSource.LABEL -> "lu dans le libellé"
    DateSource.BOOKING -> "jour d'inscription"
}

@Composable
private fun CorrectionLine(correction: DateCorrection, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                cleanBankLabel(correction.operation.rawLabel),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${correction.oldDate.short()} → ${correction.newDate.short()} · ${Money(kotlin.math.abs(correction.operation.amountCents)).formatted()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (correction.changesMonth) StatusChip("Change de mois", StatusKind.WARNING)
    }
}

@Composable
private fun OperationLine(line: DateCheckLine) {
    val op = line.operation
    val booking = op.bookingDate?.let { " · inscrit le ${it.short()}" }.orEmpty()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cleanBankLabel(op.rawLabel),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(Money(kotlin.math.abs(op.amountCents)).formatted(), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (line.needsCorrection) {
                "Daté du ${op.date.short()}$booking · achat le ${line.detail.date.short()}, à corriger"
            } else {
                "Achat le ${op.date.short()}$booking · ${line.detail.source.word()}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
