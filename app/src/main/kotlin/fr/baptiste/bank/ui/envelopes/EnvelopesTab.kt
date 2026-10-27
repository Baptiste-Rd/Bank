package fr.baptiste.bank.ui.envelopes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.ui.format.label

@Composable
fun EnvelopesTab(
    dashboard: Dashboard?,
    onEdit: (Envelope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = dashboard?.envelopes.orEmpty()
    if (lines.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Aucune enveloppe. Crée la première avec le bouton en bas à droite : " +
                    "nourriture, essence, coiffeur, projets...",
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val grouped = lines.groupBy { it.envelope.type }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    ) {
        grouped.forEach { (type, typeLines) ->
            item(key = "header-$type") {
                Text(
                    type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(typeLines, key = { it.envelope.id }) { line ->
                EnvelopeRow(line, onClick = { onEdit(line.envelope) })
                HorizontalDivider()
            }
        }
    }
}
