package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.JournalLine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hour = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)
private val day = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

/** « Aujourd'hui, 14:05 », « Hier, 9:12 », sinon « 3 novembre, 9:12 ». */
private fun whenLabel(at: Long): String {
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val date = time.toLocalDate()
    val dayText = when (date) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> date.format(day)
    }
    return "$dayText, ${time.format(hour)}"
}

/**
 * Les dix derniers changements faits à la main, comme dans le prototype : chacun s'annule d'un
 * geste, et redevient ce qu'il était avant, sans toucher aux autres.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(entries: List<JournalLine>, onUndo: (JournalLine) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            item { Text("Historique", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)) }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Aucun changement pour l'instant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (entry.undone) TextDecoration.LineThrough else null,
                                color = if (entry.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (entry.undone) "Annulé" else whenLabel(entry.at),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!entry.undone) OutlinedButton(onClick = { onUndo(entry) }) { Text("Annuler") }
                    }
                    HorizontalDivider(Modifier.padding(start = 24.dp), color = Color.White.copy(alpha = 0.08f))
                }
            }
            item {
                Text(
                    "Les 10 derniers changements",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}
