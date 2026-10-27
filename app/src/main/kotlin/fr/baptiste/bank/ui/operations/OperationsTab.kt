package fr.baptiste.bank.ui.operations

import fr.baptiste.bank.ui.components.BlurBehindWindow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.R
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.theme.BankTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayHeader = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

/** Titre d'un groupe de jour : « Aujourd'hui », « Hier », sinon « lundi 20 octobre ». */
internal fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Aujourd'hui"
    today.minusDays(1) -> "Hier"
    else -> date.format(dayHeader).replaceFirstChar { it.titlecase(Locale.FRANCE) }
}

@Composable
fun OperationsTab(
    operations: List<OperationRow>,
    onDelete: (Long) -> Unit,
    onSort: (OperationRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<OperationRow?>(null) }
    val today = LocalDate.now()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 112.dp),
    ) {
        if (operations.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp, end = 12.dp)) {
                    Text("Aucune opération", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Les dépenses que tu saisis apparaissent ici, puis celles de ton relevé une fois l'import en place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        operations.groupBy { it.date }.forEach { (date, dayOperations) ->
            item(key = "day-$date") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        dayTitle(date, today),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Money(dayOperations.sumOf { it.amountCents }).formatted(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(dayOperations, key = { it.id }) { operation ->
                OperationItem(operation, onDelete = { pendingDelete = operation }, onSort = { onSort(operation) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    pendingDelete?.let { operation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer cette dépense ?") },
            text = {
                BlurBehindWindow()
                Text("${operation.label}, ${Money(-operation.amountCents).formatted()}")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(operation.id)
                    pendingDelete = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
internal fun OperationItem(operation: OperationRow, onDelete: () -> Unit, onSort: () -> Unit = {}) {
    val manual = operation.source == TransactionSource.MANUAL
    val details = buildList {
        add(operation.envelopeNames ?: if (operation.ignored) "Hors budget" else "À trier")
        if (operation.wholePeriod) {
            val period = operation.periodKey?.let { runCatching { Period.parse(it) }.getOrNull() }
            add(if (period != null) "rattrapage ${period.label}" else "rattrapage")
        } else if (manual) {
            add("saisie à la main")
        }
    }
    Row(
        // Une opération de la banque se range (ou se reclasse) en la touchant.
        modifier = Modifier
            .fillMaxWidth()
            .then(if (manual) Modifier else Modifier.clickable(onClickLabel = "Ranger", onClick = onSort))
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(operation.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                details.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Money(operation.amountCents).formatted(),
            style = MaterialTheme.typography.titleMedium,
            color = if (operation.amountCents > 0) BankTheme.status.good else MaterialTheme.colorScheme.onSurface,
        )
        if (manual) {
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
