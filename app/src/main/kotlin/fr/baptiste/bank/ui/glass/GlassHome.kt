package fr.baptiste.bank.ui.glass

import fr.baptiste.bank.domain.model.category
import fr.baptiste.bank.ui.theme.categoryColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.HoldingLine
import fr.baptiste.bank.domain.budget.Holdings
import fr.baptiste.bank.domain.budget.MonthPace
import fr.baptiste.bank.domain.budget.MonthIncome
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.domain.budget.SavingReminder
import fr.baptiste.bank.domain.budget.chargesToCome
import fr.baptiste.bank.domain.budget.homeBudget
import fr.baptiste.bank.domain.budget.reminder
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.Tab
import fr.baptiste.bank.ui.accounts.isStale
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.components.StatusChip
import fr.baptiste.bank.ui.components.StatusKind
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.summary
import fr.baptiste.bank.ui.theme.BankTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Résumé : où j'en suis ce mois ? (lot 4 de la refonte, d'après le prototype testé)
 * 1. Deux grands chiffres : le reste du mois et le montant par jour, avec l'écart à la même date
 *    du mois précédent.
 * 2. À trier, seulement s'il y a quelque chose.
 * 3. À surveiller : trois enveloppes au plus, seulement celles qui dépassent ou vont dépasser.
 * 4. Les comptes en deux lignes : le compte courant (et le salaire du mois), les autres comptes.
 * 5. Les trois dernières dépenses.
 * 6. Le rythme du mois.
 * Un bloc qui n'a rien à dire n'apparaît pas.
 */

private val hour = DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRANCE)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

internal fun Money.signed(): String = if (isNegative) "−${(-this).formatted()}" else "+${formatted()}"

/** Dépenses récentes montrées sur le Résumé ; les autres sont dans Dépenses › Opérations. */
private const val RECENT_OPERATIONS = 3

/** Enveloppes à surveiller montrées sur le Résumé. */
private const val WATCHED = 3

@Composable
fun GlassHomeContent(
    dashboard: Dashboard?,
    unsortedCount: Int,
    onOpenEnvelope: (EnvelopeLine) -> Unit,
    onCover: (EnvelopeLine) -> Unit,
    onOpenTab: (Tab) -> Unit,
    /** Solde du compte courant (banque ou saisie), s'il est connu, et sa dernière lecture. */
    accountBalance: Money? = null,
    balanceCheckedAt: Instant? = null,
    bankConnected: Boolean = false,
    onSync: () -> Unit = {},
    onSetBalance: () -> Unit = {},
    pace: MonthPace? = null,
    holdings: Holdings? = null,
    onOpenAccount: (HoldingLine?) -> Unit = {},
    /** Salaire du mois : reçu ou attendu (null s'il n'est pas confirmé). */
    income: MonthIncome? = null,
    /** Opérations les plus récentes (les trois premières sont montrées). */
    recent: List<OperationRow> = emptyList(),
    onOpenOperation: (OperationRow) -> Unit = {},
    /** Salaire retenu pour le mois (celui du tableau de bord), pour le détail du reste. */
    salary: Money? = null,
) {
    var showRest by rememberSaveable { mutableStateOf(false) }
    if (showRest && dashboard != null) RestDetailSheet(dashboard, salary) { showRest = false }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberTabListState(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "month-strip") { MonthStripBar() }
        if (dashboard == null) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        val today = dashboard.today

        // 1. Le reste du mois et par jour.
        item(key = "hero") { MonthHero(dashboard, pace, onInfo = { showRest = true }) }

        // 2. À trier.
        if (unsortedCount > 0) {
            item(key = "to-sort") {
                GlassCard(contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$unsortedCount à trier",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        GlassPillButton("Trier", onClick = { onOpenTab(Tab.OPERATIONS) })
                    }
                }
            }
        }

        // 3. À surveiller.
        val watched = dashboard.alerts.take(WATCHED)
        if (watched.isNotEmpty()) {
            sectionHeader("watch-label", "À surveiller")
            item(key = "watch") {
                GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
                    watched.forEachIndexed { index, line ->
                        if (index > 0) RowDivider(0.dp)
                        EnvelopeRow(line, onClick = { onOpenEnvelope(line) }, accent = categoryColor(line.envelope.category))
                    }
                }
            }
        }

        // 4. Les comptes.
        item(key = "accounts") {
            GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
                CurrentAccountRow(accountBalance, balanceCheckedAt, bankConnected, income, onRefresh = if (bankConnected) onSync else onSetBalance)
                if (holdings != null && holdings.lines.isNotEmpty()) {
                    RowDivider(0.dp)
                    OtherAccountsRow(holdings, today) { onOpenTab(Tab.SAVINGS) }
                } else {
                    RowDivider(0.dp)
                    SummaryRow("Ajouter un livret ou un placement", null, null) { onOpenAccount(null) }
                }
            }
        }

        // 5. Les dernières dépenses.
        // Des dépenses : ni un virement vers un de mes comptes, ni une rentrée d'argent.
        val latest = recent.filter { it.amountCents < 0 && it.transferAccount == null }.take(RECENT_OPERATIONS)
        if (latest.isNotEmpty()) {
            sectionHeader("recent-label", "Dernières dépenses", action = "Tout voir", onAction = { onOpenTab(Tab.OPERATIONS) })
            item(key = "recent") {
                GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
                    latest.forEachIndexed { index, operation ->
                        if (index > 0) RowDivider(0.dp)
                        RecentRow(operation, today) { onOpenOperation(operation) }
                    }
                }
            }
        }

        // 6. Rythme du mois.
        if (pace != null && pace.budget.isPositive) {
            sectionHeader("pace-label", "Rythme du mois")
            item(key = "pace") { PaceCard(pace) }
        }
    }
}

/** Deux grands chiffres côte à côte, puis une seule pastille de comparaison. */
@Composable
private fun MonthHero(dashboard: Dashboard, pace: MonthPace?, onInfo: () -> Unit) {
    val rest = dashboard.dailyBudget - dashboard.dailySpent
    // Toucher les chiffres en montre le calcul (séance : « d'où vient ce reste ? »).
    GlassCard(strong = true, contentPadding = 20.dp, onClick = onInfo) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Reste du mois", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    rest.summary(),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (rest.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Par jour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    dashboard.dailyAllowance.allowance.summary(),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (dashboard.dailyAllowance.allowance.isNegative) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        val previous = pace?.previousSameDay
        if (pace != null && previous != null && pace.cumulative.isNotEmpty()) {
            val gap = pace.spent - previous
            val before = pace.month.minusMonths(1)
            val day = minOf(pace.cumulative.size, before.lengthOfMonth())
            val name = before.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.FRANCE)
            StatusChip(
                "${if (gap.isNegative) "−" else "+"}${(if (gap.isNegative) -gap else gap).summary()} vs $day $name",
                if (gap.isPositive) StatusKind.WARNING else StatusKind.GAIN,
                Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Une ligne de résumé : un titre, une précision, une valeur, un chevron. */
@Composable
private fun SummaryRow(title: String, detail: String?, value: String?, detailColor: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = detailColor ?: MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        Chevron()
    }
}

/** Le compte courant : le solde, sa fraîcheur, et le salaire du mois (reçu ou attendu). */
@Composable
private fun CurrentAccountRow(balance: Money?, checkedAt: Instant?, bankConnected: Boolean, income: MonthIncome?, onRefresh: () -> Unit) {
    // Un solde ancien ne doit jamais avoir l'air à jour : en ambre au-delà d'un jour (banque) ou
    // d'une semaine (saisie). Toucher la ligne l'actualise.
    val stale = checkedAt == null ||
        Duration.between(checkedAt, Instant.now()) > Duration.ofDays(if (bankConnected) 1 else 7)
    val read = checkedAt?.atZone(ZoneId.systemDefault())
    val readText = when {
        read == null -> if (bankConnected) "Synchroniser la banque" else "Saisir le solde"
        !bankConnected && read.toLocalDate() == LocalDate.now() -> "saisi aujourd'hui"
        !bankConnected -> "saisi le ${read.format(dayMonth)}"
        read.toLocalDate() == LocalDate.now() -> "lu à ${read.format(hour)}"
        else -> "lu le ${read.format(dayMonth)}"
    }
    val salary = income?.let { if (it.received) "salaire reçu +${it.amount.summary()}" else "salaire attendu" }
    SummaryRow(
        title = "Compte courant",
        detail = listOfNotNull(readText, salary).joinToString(" · "),
        value = balance?.summary() ?: "—",
        detailColor = if (stale) BankTheme.status.warning else null,
        onClick = onRefresh,
    )
}

/** Les autres comptes en une ligne : le total net estimé et le versement du mois à faire. */
@Composable
private fun OtherAccountsRow(holdings: Holdings, today: LocalDate, onClick: () -> Unit) {
    val due = holdings.lines.mapNotNull { line -> (line.reminder(today) as? SavingReminder.Due)?.let { line to it.amount } }.firstOrNull()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Autres comptes", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (due != null) {
                StatusChip("${due.second.summary()} à verser", StatusKind.WARNING, Modifier.padding(top = 4.dp))
            }
        }
        val net = holdings.netTotal
        Text((if (net != holdings.total) "≈ " else "") + net.summary(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        Chevron()
    }
}

/** Une dépense récente : son nom, son enveloppe et son jour, son montant (vert s'il entre). */
@Composable
private fun RecentRow(operation: OperationRow, today: LocalDate, onClick: () -> Unit) {
    val amount = Money(operation.amountCents)
    val day = when (operation.date) {
        today -> "aujourd'hui"
        today.minusDays(1) -> "hier"
        else -> operation.date.format(dayMonth)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(operation.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(operation.envelopeNames ?: operation.transferAccount ?: "à trier", day).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (amount.isPositive) "+" + amount.formatted() else amount.formatted(),
            style = MaterialTheme.typography.titleMedium,
            color = if (amount.isPositive) BankTheme.status.gain else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Le chiffre (l'écart au rythme), puis la courbe du mois et sa légende. */
@Composable
private fun PaceCard(pace: MonthPace) {
    val status = BankTheme.status
    // Un écart de quelques euros n'est pas un signal : « dans le rythme » à 2 % du budget (10 € au moins).
    val tolerance = maxOf(pace.budget.divideFloor(50), Money.euros(10))
    val above = pace.gap > tolerance
    val below = pace.gap < Money.ZERO - tolerance
    GlassCard(contentPadding = 16.dp) {
        Text(
            if (above || below) "${pace.gap.signed()} vs rythme prévu" else "Dans le rythme",
            style = MaterialTheme.typography.titleMedium,
            color = when {
                above -> status.warning
                below -> status.gain
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            "Prévu à ce jour : ${pace.expected.formatted()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val spentColor = if (above) status.warning else status.good
        val paceColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)) {
            val maxValue = maxOf(pace.budget.cents, pace.spent.cents, 1L).toFloat() * 1.08f
            fun x(day: Int) = size.width * (day.toFloat() / pace.days)
            fun y(cents: Long) = size.height - size.height * (cents / maxValue)
            // Rythme prévu : du 0 au budget, en pointillé.
            drawLine(
                color = paceColor,
                start = Offset(0f, y(0)),
                end = Offset(size.width, y(pace.budget.cents)),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
            // Dépensé cumulé, jusqu'à aujourd'hui.
            val path = Path().apply {
                moveTo(0f, y(0))
                pace.cumulative.forEachIndexed { index, value -> lineTo(x(index + 1), y(value.cents)) }
            }
            drawPath(path, spentColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            pace.cumulative.lastOrNull()?.let { drawCircle(spentColor, 4.dp.toPx(), Offset(x(pace.cumulative.size), y(it.cents))) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
            Legend(spentColor, "Dépensé ${pace.spent.formatted()}")
            Legend(paceColor, "Budget ${pace.budget.formatted()}", dashed = true)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { Box(Modifier.size(width = 5.dp, height = 2.dp).background(color)) }
            }
        } else {
            Box(Modifier.size(width = 12.dp, height = 3.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Le calcul du reste du mois, ligne à ligne : le salaire, ce qui est réservé aux autres enveloppes,
 * le budget du quotidien, ce qui est déjà dépensé. Le solde du compte n'y entre pas.
 */
@Composable
private fun RestDetailSheet(dashboard: Dashboard, salary: Money?, onDismiss: () -> Unit) {
    fun share(vararg types: fr.baptiste.bank.domain.model.EnvelopeType) =
        dashboard.envelopes.filter { it.envelope.type in types }.fold(Money.ZERO) { sum, it -> sum + it.monthlyShare }
    val charges = share(fr.baptiste.bank.domain.model.EnvelopeType.FIXED)
    val rare = share(
        fr.baptiste.bank.domain.model.EnvelopeType.RECURRING,
        fr.baptiste.bank.domain.model.EnvelopeType.ANNUAL,
        fr.baptiste.bank.domain.model.EnvelopeType.PLANNED,
    )
    val savings = share(fr.baptiste.bank.domain.model.EnvelopeType.SAVINGS)
    val rest = dashboard.dailyBudget - dashboard.dailySpent
    val lines = buildList {
        if (salary != null) {
            add(fr.baptiste.bank.ui.components.InfoLine("Salaire du mois", salary.formatted()))
            if (charges.isPositive) add(fr.baptiste.bank.ui.components.InfoLine("Charges fixes", "−" + charges.formatted()))
            if (rare.isPositive) add(fr.baptiste.bank.ui.components.InfoLine("Rares et ponctuels (part du mois)", "−" + rare.formatted()))
            if (savings.isPositive) add(fr.baptiste.bank.ui.components.InfoLine("Épargne", "−" + savings.formatted()))
            // Ce qui a été ajouté ou retiré aux enveloppes ce mois-ci (le reste de l'écart).
            val moves = dashboard.dailyBudget - (salary - charges - rare - savings)
            if (moves.cents != 0L) add(fr.baptiste.bank.ui.components.InfoLine("Ajouts et retraits du mois", moves.signed()))
        }
        add(fr.baptiste.bank.ui.components.InfoLine("Budget du quotidien", dashboard.dailyBudget.formatted()))
        add(fr.baptiste.bank.ui.components.InfoLine("   dont enveloppes du quotidien", dashboard.dailyAssigned.formatted()))
        dashboard.unallocated?.takeIf { it.cents != 0L }?.let { add(fr.baptiste.bank.ui.components.InfoLine("   dont à répartir", it.formatted())) }
        add(fr.baptiste.bank.ui.components.InfoLine("Déjà dépensé au quotidien", "−" + dashboard.dailySpent.formatted()))
        add(fr.baptiste.bank.ui.components.InfoLine("Reste du mois", rest.formatted()))
    }
    fr.baptiste.bank.ui.components.InfoSheet(
        title = "D'où vient le reste du mois",
        lines = lines,
        note = (if (salary == null) "Sans salaire confirmé, le budget du quotidien est la somme des enveloppes du quotidien. " else "") +
            "Par jour : ce reste divisé par les ${dashboard.daysRemaining} jours restants, aujourd'hui compris. " +
            "Le solde du compte n'entre pas dans ce calcul : l'argent des mois passés est dans « Disponible sur le compte ».",
        onDismiss = onDismiss,
    )
}
