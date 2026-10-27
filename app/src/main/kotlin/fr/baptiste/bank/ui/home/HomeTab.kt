package fr.baptiste.bank.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.update.UpdateSection

@Composable
fun HomeTab(
    dashboard: Dashboard?,
    onEditIncome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (dashboard == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    // Les enveloppes en alerte d'abord, puis les enveloppes quotidiennes : ce sont celles du jour.
    val highlighted = (dashboard.alerts + dashboard.envelopes.filter { it.envelope.type == EnvelopeType.DAILY })
        .distinctBy { it.envelope.id }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AvailableTodayCard(dashboard) }
        item { WalletCard(dashboard, onEditIncome) }
        if (highlighted.isNotEmpty()) {
            item {
                Text(
                    "À surveiller",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(highlighted, key = { it.envelope.id }) { line -> EnvelopeRow(line) }
        }
        item { UpdateSection() }
    }
}

@Composable
private fun AvailableTodayCard(dashboard: Dashboard) {
    val allowance = dashboard.dailyAllowance
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Disponible aujourd'hui", style = MaterialTheme.typography.titleMedium)
            if (!dashboard.dailyBudget.isPositive) {
                Text(
                    "Crée une enveloppe de type Quotidien (nourriture, essence...) pour voir ce que tu peux dépenser chaque jour.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }
            Text(
                allowance.remainingToday.formatted(),
                style = MaterialTheme.typography.displaySmall,
                color = if (allowance.remainingToday.isNegative) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                "Part du jour ${allowance.allowance.formatted()} · déjà dépensé ${allowance.spentToday.formatted()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Budget quotidien du mois : ${dashboard.dailyBudget.formatted()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WalletCard(dashboard: Dashboard, onEditIncome: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val unallocated = dashboard.unallocated
                if (unallocated == null) {
                    Text("Portefeuille", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Renseigne ton salaire pour voir ce qui reste à répartir.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Portefeuille : ${unallocated.formatted()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (unallocated.isNegative) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        if (unallocated.isNegative) {
                            "Tes enveloppes dépassent ton salaire du mois."
                        } else {
                            "Non attribué ce mois-ci, à répartir dans tes enveloppes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onEditIncome) { Text("Salaire") }
        }
    }
}
