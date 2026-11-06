package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.MonthSpending
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.Tab
import fr.baptiste.bank.ui.format.summary
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Résumé d'un mois terminé (choisi dans la barre des mois) : ce qu'il a coûté, son bilan
 * d'épargne, et le chemin vers le détail. Le Résumé du mois en cours reste [GlassHomeContent].
 */
@Composable
fun GlassPastMonthContent(
    month: YearMonth,
    report: MonthSpending?,
    dashboard: Dashboard?,
    transfers: Money?,
    trackingStart: YearMonth?,
    onEdit: (Envelope) -> Unit,
    onOpenTab: (Tab) -> Unit,
) {
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberTabListState(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "month-strip") { MonthStripBar() }
        if (trackingStart != null && month < trackingStart) {
            item(key = "not-tracked") { NotTrackedCard(month, trackingStart) {} }
            return@LazyColumn
        }
        item(key = "spent") {
            GlassCard(strong = true) {
                Text("Dépensé en $name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(report?.total?.summary() ?: "—", style = MaterialTheme.typography.displaySmall)
                if (report != null && report.budget.isPositive) {
                    Text("pour un budget de ${report.budget.summary()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (dashboard != null && dashboard.savings.isFinal) {
            item(key = "review") { MonthReview(dashboard, onEdit, transfers) }
        }
        item(key = "details") {
            GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
                DetailRow("Dépenses du mois") { onOpenTab(Tab.OPERATIONS) }
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Chevron()
    }
}
