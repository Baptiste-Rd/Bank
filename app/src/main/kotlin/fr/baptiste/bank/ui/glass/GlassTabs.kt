package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.baptiste.bank.R
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.envelopes.TemplateChips
import fr.baptiste.bank.ui.envelopes.envelopeTemplates
import fr.baptiste.bank.ui.envelopes.template
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.home.MonthStrip
import fr.baptiste.bank.ui.home.todayExplanation
import fr.baptiste.bank.ui.operations.OperationItem
import fr.baptiste.bank.ui.operations.dayTitle
import fr.baptiste.bank.ui.theme.BankTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Le contenu défile sous la barre d'état et sous la barre d'onglets flottante, comme sur iOS. */
@Composable
private fun glassContentPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 112.dp)
}

/** Grand titre en haut de page, qui défile avec le contenu. La place à droite est celle du bouton ⋮. */
private fun LazyListScope.largeTitle(title: String, subtitle: String? = null) {
    item(key = "title") {
        Column(modifier = Modifier.padding(start = 4.dp, end = 72.dp, top = 8.dp, bottom = 8.dp)) {
            Text(title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LazyListScope.sectionLabel(key: String, text: String, trailing: String? = null) {
    item(key = key) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val todayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

@Composable
fun GlassHomeContent(
    dashboard: Dashboard?,
    onAddExpense: () -> Unit,
    onEditIncome: () -> Unit,
    onOpenEnvelope: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Aujourd'hui", LocalDate.now().format(todayTitle).replaceFirstChar { it.titlecase(Locale.FRANCE) })
        if (dashboard == null) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        val allowance = dashboard.dailyAllowance
        if (!dashboard.dailyBudget.isPositive) {
            item(key = "empty") {
                GlassCard(strong = true) {
                    Text("Pas encore de budget du jour", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Indique ton salaire : ce qui n'est réservé à aucune charge ni épargne est réparti " +
                            "sur les jours restants du mois.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onEditIncome) { Text("Indiquer mon salaire") }
                }
            }
        } else {
            // Le chiffre du jour, posé directement sur le fond liquide : c'est la seule chose en très grand.
            item(key = "hero") {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        "Tu peux dépenser",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        allowance.remainingToday.formatted(),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, lineHeight = 76.sp),
                        color = if (allowance.remainingToday.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        todayExplanation(allowance.allowance, allowance.spentToday, dashboard.daysRemaining),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // L'action la plus fréquente, à portée de pouce juste sous le chiffre du jour.
                    GlassPillButton(
                        text = "Ajouter une dépense",
                        onClick = onAddExpense,
                        icon = painterResource(R.drawable.ic_add),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            item(key = "month") {
                GlassCard {
                    Text("Ce mois-ci", style = MaterialTheme.typography.titleSmall)
                    MonthStrip(
                        days = dashboard.monthDays,
                        averageShare = dashboard.averageDailyShare,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        buildString {
                            append("${dashboard.dailySpent.formatted()} dépensés sur ${dashboard.dailyBudget.formatted()}")
                            if (dashboard.catchUpSpent.isPositive) {
                                append(", dont ${dashboard.catchUpSpent.formatted()} en rattrapage")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        item(key = "wallet") {
            val unallocated = dashboard.unallocated
            GlassCard(onClick = onEditIncome, contentPadding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Portefeuille", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                unallocated == null -> "Touche ici pour indiquer ton salaire."
                                unallocated.isNegative -> "Tes enveloppes dépassent ton salaire."
                                else -> "Dans ton budget du jour, rangé dans aucune enveloppe."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (unallocated != null) {
                        Text(
                            unallocated.formatted(),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (unallocated.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        val watched = dashboard.alerts
        if (watched.isNotEmpty()) {
            sectionLabel("watched-label", "À surveiller")
            items(watched, key = { "watch-${it.envelope.id}" }) { line ->
                GlassCard(contentPadding = 16.dp) {
                    EnvelopeRow(line, onClick = { onOpenEnvelope(line) }, onCover = { onCover(line) })
                }
            }
        }
        val daily = dashboard.envelopes.filter { it.envelope.type == EnvelopeType.DAILY && it !in watched }
        if (daily.isNotEmpty()) {
            sectionLabel("daily-label", "Quotidien")
            item(key = "daily") {
                GlassCard(contentPadding = 16.dp) {
                    daily.forEachIndexed { index, line ->
                        if (index > 0) RowGap()
                        EnvelopeRow(line, onClick = { onOpenEnvelope(line) })
                    }
                }
            }
        }
    }
}

@Composable
fun GlassEnvelopesContent(
    dashboard: Dashboard?,
    archived: List<Envelope>,
    onOpen: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
    onEdit: (Envelope) -> Unit,
    onRestore: (Envelope) -> Unit,
) {
    val lines = dashboard?.envelopes.orEmpty()
    val usedNames = lines.map { it.envelope.name.lowercase() }.toSet()
    val suggestions = envelopeTemplates.filter { (name, _) -> name.lowercase() !in usedNames }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Enveloppes")
        if (lines.isEmpty()) {
            item(key = "empty") {
                GlassCard(strong = true) {
                    Text("Aucune enveloppe", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Une enveloppe réserve un montant à un usage : courses, essence, coiffeur, projets... " +
                            "Les modèles ci-dessous pré-remplissent le formulaire.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        lines.groupBy { it.envelope.type }.forEach { (type, typeLines) ->
            sectionLabel("label-$type", type.label)
            item(key = "group-$type") {
                GlassCard(contentPadding = 16.dp) {
                    typeLines.forEachIndexed { index, line ->
                        if (index > 0) RowGap()
                        EnvelopeRow(line, onClick = { onOpen(line) }, onCover = { onCover(line) })
                    }
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            item(key = "templates") {
                GlassCard(contentPadding = 16.dp) {
                    TemplateChips(suggestions, onPick = { (name, type) -> onEdit(template(name, type)) })
                }
            }
        }
        if (archived.isNotEmpty()) {
            sectionLabel("archived-label", "Archivées")
            item(key = "archived") {
                GlassCard(contentPadding = 16.dp) {
                    archived.forEachIndexed { index, envelope ->
                        if (index > 0) RowGap()
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(envelope.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${envelope.type.label}, ${envelope.amount.formatted()} ${envelope.frequency.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onRestore(envelope) }) { Text("Restaurer") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassOperationsContent(
    operations: List<OperationRow>,
    onDelete: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<OperationRow?>(null) }
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Opérations")
        if (operations.isEmpty()) {
            item(key = "empty") {
                GlassCard(strong = true) {
                    Text("Aucune opération", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Les dépenses que tu saisis apparaissent ici, puis celles de ta banque une fois l'import en place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        operations.groupBy { it.date }.forEach { (date, dayOperations) ->
            sectionLabel("label-$date", dayTitle(date, today), Money(dayOperations.sumOf { it.amountCents }).formatted())
            item(key = "day-$date") {
                GlassCard(contentPadding = 12.dp) {
                    dayOperations.forEachIndexed { index, operation ->
                        if (index > 0) RowGap()
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            OperationItem(operation, onDelete = { pendingDelete = operation })
                        }
                    }
                }
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

/** Espace entre deux lignes d'une même carte : pas de trait, la respiration suffit. */
@Composable
private fun RowGap() {
    Spacer(Modifier.height(10.dp))
}
