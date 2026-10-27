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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.theme.BankTheme

@Composable
fun HomeTab(
    dashboard: Dashboard?,
    onEditIncome: () -> Unit,
    onCreateEnvelope: () -> Unit,
    onCover: (EnvelopeLine) -> Unit,
    onOpenEnvelope: (EnvelopeLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (dashboard == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val watched = dashboard.alerts
    val daily = dashboard.envelopes.filter { it.envelope.type == EnvelopeType.DAILY && it !in watched }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
    ) {
        item { TodaySection(dashboard, onCreateEnvelope) }
        item { WalletLine(dashboard.unallocated, onEditIncome) }
        if (watched.isNotEmpty()) {
            item { SectionTitle("À surveiller") }
            items(watched, key = { "watch-${it.envelope.id}" }) { line ->
                EnvelopeRow(line, onClick = { onOpenEnvelope(line) }, onCover = { onCover(line) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (daily.isNotEmpty()) {
            item { SectionTitle("Quotidien") }
            items(daily, key = { "daily-${it.envelope.id}" }) { line ->
                EnvelopeRow(line, onClick = { onOpenEnvelope(line) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * Le chiffre du jour. C'est la seule information mise en avant : ce qui reste à dépenser
 * aujourd'hui, en très grand, puis de quoi il est fait et la bande du mois.
 */
@Composable
private fun TodaySection(dashboard: Dashboard, onCreateEnvelope: () -> Unit) {
    val allowance = dashboard.dailyAllowance
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
        Text(
            "Tu peux dépenser aujourd'hui",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!dashboard.dailyBudget.isPositive) {
            Text(
                "Pas encore de budget quotidien.",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Crée une enveloppe Quotidien (courses, essence, sorties) : son montant du mois " +
                    "est réparti sur les jours restants.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onCreateEnvelope, modifier = Modifier.padding(top = 4.dp)) {
                Text("Créer une enveloppe")
            }
            return@Column
        }
        Text(
            allowance.remainingToday.formatted(),
            style = MaterialTheme.typography.displayLarge,
            color = if (allowance.remainingToday.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            todayExplanation(allowance.allowance, allowance.spentToday, dashboard.daysRemaining),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        MonthStrip(days = dashboard.monthDays, averageShare = dashboard.averageDailyShare)
        Text(
            buildString {
                append("${dashboard.dailySpent.formatted()} dépensés sur ${dashboard.dailyBudget.formatted()} ce mois")
                if (dashboard.catchUpSpent.isPositive) {
                    append(", dont ${dashboard.catchUpSpent.formatted()} en rattrapage")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun todayExplanation(share: Money, spentToday: Money, daysRemaining: Int): String {
    val days = if (daysRemaining > 1) "$daysRemaining jours restants" else "dernier jour du mois"
    return if (spentToday.isPositive) {
        "Part du jour ${share.formatted()}, déjà ${spentToday.formatted()} dépensés. $days."
    } else {
        "Part du jour sur ce qui reste du mois, $days."
    }
}

/** Le portefeuille sur une ligne : ce que le salaire laisse sans enveloppe. */
@Composable
private fun WalletLine(unallocated: Money?, onEditIncome: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Portefeuille", style = MaterialTheme.typography.labelLarge)
                Text(
                    when {
                        unallocated == null -> "Indique ton salaire pour savoir ce qui reste à répartir."
                        unallocated.isNegative -> "Tes enveloppes dépassent ton salaire de ${(-unallocated).formatted()}."
                        else -> "Non réparti ce mois-ci."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (unallocated != null) {
                Text(
                    unallocated.formatted(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unallocated.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                )
            } else {
                TextButton(onClick = onEditIncome) { Text("Indiquer") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
}
