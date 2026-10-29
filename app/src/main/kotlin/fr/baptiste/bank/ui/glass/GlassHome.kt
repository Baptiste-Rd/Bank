package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.R
import fr.baptiste.bank.data.MonthSavings
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.Tab
import fr.baptiste.bank.ui.components.RowActionButton
import fr.baptiste.bank.ui.components.StatusChip
import fr.baptiste.bank.ui.components.StatusKind
import fr.baptiste.bank.ui.envelopes.CategoryAvatar
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.home.MonthStrip
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.ui.theme.envelopeTypeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Onglet Aujourd'hui : une seule question, « puis-je dépenser aujourd'hui ? ».
 * 1. Le reste du jour, en très grand, et l'action la plus fréquente.
 * 2. Le mois : dépensé sur budget, et la bande du rythme jour par jour.
 * 3. À faire : ce qui demande une décision (couvrir, trier), seulement s'il y en a.
 * 4. L'épargne prévue en une ligne, qui ouvre l'onglet Épargne.
 * Le détail des enveloppes vit dans l'onglet Budget.
 */

private val todayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

internal fun Money.signed(): String = if (isNegative) "−${(-this).formatted()}" else "+${formatted()}"

@Composable
fun GlassHomeContent(
    dashboard: Dashboard?,
    savingsHistory: List<MonthSavings>,
    unsortedCount: Int,
    onAddExpense: () -> Unit,
    onEditIncome: () -> Unit,
    onCover: (EnvelopeLine) -> Unit,
    onOpenEnvelope: (EnvelopeLine) -> Unit,
    onOpenTab: (Tab) -> Unit,
    /** Ouvre le bilan du mois dernier (onglet Budget, mois précédent). */
    onOpenReview: () -> Unit = {},
    /** Reste du jour plutôt que reste du mois (le mois par défaut). */
    showsDay: Boolean = false,
    onShowsDayChange: (Boolean) -> Unit = {},
    /** Solde du compte courant lu à la banque, s'il est connu. */
    accountBalance: Money? = null,
) {
    // Lu ici : le contenu d'une LazyColumn se construit hors composition.
    val warning = BankTheme.status.warning
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
            item(key = "no-income") {
                GlassCard(strong = true) {
                    Text("Indique ton salaire", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Il fixe ton budget du quotidien, donc ton reste du jour.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GlassPillButton("Indiquer mon salaire", onClick = onEditIncome, modifier = Modifier.padding(top = 12.dp))
                }
            }
        } else {
            // 1. Le chiffre, posé sur le fond : la seule chose en très grand. Par défaut ce qui reste
            // à dépenser d'ici la fin du mois ; « Jour » le ramène à aujourd'hui.
            item(key = "hero") {
                val monthLeft = dashboard.dailyBudget - dashboard.dailySpent
                val shown = if (showsDay) allowance.remainingToday else monthLeft
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    // À gauche, contre le libellé : à droite, la bascule passait sous les boutons
                    // flottants (+ et ⋮) quand l'accueil défile.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (showsDay) "Reste du jour" else "Reste du mois",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PeriodPills(showsDay, onShowsDayChange)
                    }
                    Text(
                        shown.formatted(),
                        style = MaterialTheme.typography.displayLarge,
                        color = if (shown.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (showsDay) {
                            "sur ${allowance.allowance.formatted()} aujourd'hui · ${daysLabel(dashboard.daysRemaining)}"
                        } else {
                            val perDay = if (dashboard.daysRemaining > 0 && monthLeft.isPositive) {
                                " · ${monthLeft.divideFloor(dashboard.daysRemaining.toLong()).formatted()} par jour"
                            } else {
                                ""
                            }
                            "sur ${dashboard.dailyBudget.formatted()} · ${daysLabel(dashboard.daysRemaining)}$perDay"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (accountBalance != null) {
                        Text(
                            "Compte courant : ${accountBalance.formatted()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    GlassPillButton(
                        text = "Ajouter une dépense",
                        onClick = onAddExpense,
                        icon = painterResource(R.drawable.ic_add),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            // 2. Le mois.
            sectionHeader("month-label", "Ce mois-ci", action = "Budget", onAction = { onOpenTab(Tab.BUDGET) })
            item(key = "month") {
                GlassCard(contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(dashboard.dailySpent.formatted(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "  dépensés sur ${dashboard.dailyBudget.formatted()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    MonthStrip(
                        days = dashboard.monthDays,
                        averageShare = dashboard.averageDailyShare,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // 3. À faire : n'apparaît que s'il y a une décision à prendre.
        val watched = dashboard.alerts
        // La première semaine du mois, le bilan du mois dernier attend d'être lu.
        val lastMonth = savingsHistory.dropLast(1).lastOrNull()?.takeIf { dashboard.today.dayOfMonth <= 7 }
        val todoCount = watched.size + (if (unsortedCount > 0) 1 else 0) + (if (lastMonth != null) 1 else 0)
        if (todoCount > 0) {
            sectionHeader("todo-label", "À faire", trailing = "$todoCount", dot = warning)
            item(key = "todo") {
                GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
                    watched.forEachIndexed { index, line ->
                        if (index > 0) fr.baptiste.bank.ui.components.RowDivider()
                        TodoEnvelopeRow(line, onOpen = { onOpenEnvelope(line) }, onCover = { onCover(line) })
                    }
                    if (lastMonth != null) {
                        if (watched.isNotEmpty()) fr.baptiste.bank.ui.components.RowDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenReview).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryAvatar("B", envelopeTypeColor(fr.baptiste.bank.domain.model.EnvelopeType.SAVINGS))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    "Bilan de ${lastMonth.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    "${lastMonth.outlook.forecast.formatted()} épargnés",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Chevron()
                        }
                    }
                    if (unsortedCount > 0) {
                        if (watched.isNotEmpty() || lastMonth != null) fr.baptiste.bank.ui.components.RowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenTab(Tab.OPERATIONS) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryAvatar("?", BankTheme.status.warning)
                            Text(
                                if (unsortedCount > 1) "$unsortedCount opérations à trier" else "1 opération à trier",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            )
                            Chevron()
                        }
                    }
                }
            }
        }

        // 4. L'épargne en une ligne.
        val savings = dashboard.savings
        val previous = savingsHistory.dropLast(1).lastOrNull()
        sectionHeader("savings-label", "Épargne", action = "Détail", onAction = { onOpenTab(Tab.SAVINGS) })
        item(key = "savings") {
            GlassCard(contentPadding = 16.dp, onClick = { onOpenTab(Tab.SAVINGS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar("€", envelopeTypeColor(fr.baptiste.bank.domain.model.EnvelopeType.SAVINGS))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            if (savings.isFinal) "Épargné ce mois-ci" else "Épargne prévue",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        if (previous != null) {
                            val delta = savings.forecast - previous.outlook.forecast
                            StatusChip(
                                "${delta.signed()} vs ${previous.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.FRANCE)}",
                                if (delta.isNegative) StatusKind.WARNING else StatusKind.GOOD,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    // Comparée à l'objectif : vert au-dessus, blanc à 5 % près, rouge en dessous.
                    val margin = savings.target.divideFloor(20)
                    Text(
                        savings.forecast.formatted(),
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            savings.forecast > savings.target + margin -> BankTheme.status.gain
                            savings.forecast < savings.target - margin -> BankTheme.status.over
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                    Chevron()
                }
            }
        }
    }
}

private fun daysLabel(days: Int): String = if (days > 1) "$days jours restants" else "dernier jour du mois"

/** Une enveloppe à couvrir, en version courte : pastille, nom, statut, bouton Couvrir. */
@Composable
private fun TodoEnvelopeRow(line: EnvelopeLine, onOpen: () -> Unit, onCover: () -> Unit) {
    val overspent = line.remaining.isNegative
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(line.envelope.name, envelopeTypeColor(line.envelope.type))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(line.envelope.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (overspent) {
                StatusChip("Dépassé ${(-line.remaining).formatted()}", StatusKind.OVER, Modifier.padding(top = 4.dp))
            } else {
                StatusChip("Prévu +${line.projection.overrun.formatted()}", StatusKind.WARNING, Modifier.padding(top = 4.dp))
            }
        }
        RowActionButton("Couvrir", onClick = onCover)
    }
}

/** « Mois | Jour » en petit, à côté du libellé du chiffre. */
@Composable
private fun PeriodPills(showsDay: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(2.dp),
    ) {
        listOf(false to "Mois", true to "Jour").forEach { (day, label) ->
            val selected = day == showsDay
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)) else Modifier)
                    .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onChange(day) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
