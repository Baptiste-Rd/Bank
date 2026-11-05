package fr.baptiste.bank.ui.glass

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
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

private fun dayOf(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()

private fun hourOf(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(hour)

/** « Aujourd'hui », « Hier », sinon « 3 novembre ». */
private fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> date.format(day)
    }
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
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                    Text("Historique", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(72.dp))
                }
            }
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
            // Un en-tête par jour, l'heure seule sur chaque ligne.
            entries.groupBy { dayOf(it.at) }.forEach { (day, list) ->
            item(key = "day-$day") {
                Text(dayTitle(day), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
            }
            items(list, key = { it.id }) { entry ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (entry.undone) TextDecoration.LineThrough else null,
                                color = if (entry.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            // Une entrée plus récente touche les mêmes éléments : on annule d'abord celle-là.
                            Text(
                                when {
                                    entry.undone -> "Annulé"
                                    entry.blocked -> "${hourOf(entry.at)} · modifié depuis"
                                    else -> hourOf(entry.at)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!entry.undone && !entry.blocked) TextButton(onClick = { onUndo(entry) }) { Text("Annuler") }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Color.White.copy(alpha = 0.08f))
                }
            }
            }
            if (entries.isNotEmpty()) item {
                Text(
                    if (entries.any { it.blocked && !it.undone }) "Les 10 derniers changements. « Modifié depuis » : annule d'abord le changement plus récent qui touche la même chose." else "Les 10 derniers changements",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}
