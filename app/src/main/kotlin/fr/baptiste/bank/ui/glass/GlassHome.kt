package fr.baptiste.bank.ui.glass

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
 * Accueil : le tableau de bord du mois, au plus simple mais complet. Cinq blocs, une question
 * chacun (voir domain/budget/Home.kt) :
 * 1. Sur ton compte : le solde réel, le dépensé du mois sur le budget, les charges encore à prélever.
 * 2. À trier, seulement s'il y a quelque chose.
 * 3. Autres comptes : livrets et placements (≈ net), le versement du mois à faire.
 * 4. Rythme du mois : le dépensé cumulé face à la ligne du budget.
 * 5. Enveloppes : six lignes au plus (charges en une ligne), le reste dans l'onglet Budget.
 * Une seule action : le + en haut à droite (ajouter une dépense).
 */

private val todayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)
private val hour = DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRANCE)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

internal fun Money.signed(): String = if (isNegative) "−${(-this).formatted()}" else "+${formatted()}"

/** Comptes montrés sur l'accueil ; les autres sont dans l'onglet Épargne. */
private const val HOME_ACCOUNTS = 4

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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberTabListState(),
        contentPadding = glassContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        largeTitle("Aujourd'hui", LocalDate.now().format(todayTitle).replaceFirstChar { it.titlecase(Locale.FRANCE) })
        if (dashboard == null) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        val today = dashboard.today

        // 1. Sur ton compte.
        item(key = "account") {
            AccountHero(
                balance = accountBalance,
                checkedAt = balanceCheckedAt,
                bankConnected = bankConnected,
                pace = pace,
                toCome = chargesToCome(dashboard.envelopes, today),
                onRefresh = if (bankConnected) onSync else onSetBalance,
            )
        }

        // 2. À trier : le seul rappel qui garde les chiffres justes ; absent quand tout est rangé.
        if (unsortedCount > 0) {
            item(key = "to-sort") {
                GlassCard(contentPadding = 16.dp, onClick = { onOpenTab(Tab.OPERATIONS) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (unsortedCount > 1) "$unsortedCount opérations à trier" else "1 opération à trier",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f),
                        )
                        Chevron()
                    }
                }
            }
        }

        // 3. Autres comptes.
        if (holdings != null) {
            sectionHeader("accounts-label", "Autres comptes", action = "Tout voir", onAction = { onOpenTab(Tab.SAVINGS) })
            item(key = "accounts") { AccountsCard(holdings, today, onOpenAccount) }
        }

        // 4. Rythme du mois.
        if (pace != null && pace.budget.isPositive) {
            sectionHeader("pace-label", "Rythme du mois")
            item(key = "pace") { PaceCard(pace) }
        }

        // 5. Enveloppes.
        val home = homeBudget(dashboard.envelopes)
        if (home.daily.isNotEmpty() || home.charges.isNotEmpty() || home.rare.isNotEmpty()) {
            sectionHeader("envelopes-label", "Enveloppes", action = "Tout voir", onAction = { onOpenTab(Tab.BUDGET) })
            item(key = "envelopes") {
                GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
                    var first = true
                    if (home.daily.isNotEmpty()) {
                        FamilyCaption("Au quotidien", "${dashboard.dailyAllowance.allowance.formatted()} par jour jusqu'au ${today.lengthOfMonth()}")
                        home.daily.forEachIndexed { index, line ->
                            if (index > 0) RowDivider(0.dp)
                            EnvelopeRow(line, onClick = { onOpenEnvelope(line) }, onCover = { onCover(line) })
                        }
                        first = false
                    }
                    if (home.charges.isNotEmpty()) {
                        if (!first) RowDivider(0.dp)
                        ChargesRow(home.charges, onOpenEnvelope)
                        first = false
                    }
                    if (home.rare.isNotEmpty()) {
                        if (!first) RowDivider(0.dp)
                        FamilyCaption("Rares et ponctuels", null)
                        home.rare.forEachIndexed { index, line ->
                            if (index > 0) RowDivider(0.dp)
                            EnvelopeRow(line, onClick = { onOpenEnvelope(line) }, onCover = { onCover(line) })
                        }
                    }
                    if (home.hidden > 0) {
                        RowDivider(0.dp)
                        TextButton(onClick = { onOpenTab(Tab.BUDGET) }) {
                            Text(if (home.hidden > 1) "${home.hidden} autres enveloppes" else "1 autre enveloppe")
                        }
                    }
                }
            }
        }
    }
}

/** Le solde réel en grand, sa fraîcheur, puis le mois en une ou deux lignes. */
@Composable
private fun AccountHero(
    balance: Money?,
    checkedAt: Instant?,
    bankConnected: Boolean,
    pace: MonthPace?,
    toCome: Money,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text("Sur ton compte", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            balance?.summary() ?: "—",
            style = MaterialTheme.typography.displayLarge,
            color = if (balance?.isNegative == true) BankTheme.status.over else MaterialTheme.colorScheme.onSurface,
        )
        // Un solde ancien ne doit jamais avoir l'air à jour : sa date, en ambre au-delà d'un jour (banque)
        // ou d'une semaine (saisie). Toucher la ligne l'actualise.
        val stale = checkedAt == null ||
            Duration.between(checkedAt, Instant.now()) > Duration.ofDays(if (bankConnected) 1 else 7)
        val read = checkedAt?.atZone(ZoneId.systemDefault())
        // Lu à la banque : l'heure ; saisi à la main : le jour seulement (la saisie n'a pas d'heure).
        val readText = when {
            read == null -> if (bankConnected) "Synchroniser la banque" else "Saisir le solde"
            !bankConnected && read.toLocalDate() == LocalDate.now() -> "saisi aujourd'hui"
            !bankConnected -> "saisi le ${read.format(dayMonth)}" + if (stale) " · à mettre à jour" else ""
            read.toLocalDate() == LocalDate.now() -> "lu à ${read.format(hour)}"
            else -> "lu le ${read.format(dayMonth)}" + if (stale) " · actualiser" else ""
        }
        Text(
            readText,
            style = MaterialTheme.typography.bodySmall,
            color = if (stale) BankTheme.status.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onRefresh).padding(vertical = 2.dp),
        )
        if (pace != null) {
            Text(
                "Dépensé ce mois : ${pace.spent.formatted()} sur ${pace.budget.formatted()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (toCome.isPositive) {
            Text(
                "dont ${toCome.formatted()} de charges encore à prélever",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Livrets et placements : le total, le net estimé, quatre comptes au plus, le versement du mois. */
@Composable
private fun AccountsCard(holdings: Holdings, today: LocalDate, onOpenAccount: (HoldingLine?) -> Unit) {
    GlassCard(contentPadding = 16.dp, verticalPadding = 12.dp) {
        if (holdings.lines.isEmpty()) {
            Row(Modifier.fillMaxWidth().clickable { onOpenAccount(null) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ajouter un livret ou un placement", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Chevron()
            }
            return@GlassCard
        }
        Text(holdings.total.summary(), style = MaterialTheme.typography.titleLarge)
        if (holdings.netTotal != holdings.total) {
            Text(
                "≈ ${holdings.netTotal.formatted()} net après impôt estimé",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        holdings.lines.take(HOME_ACCOUNTS).forEach { line ->
            RowDivider(0.dp)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenAccount(line) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(line.account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val stale = line.isStale(today)
                    Text(
                        accountCaption(line, today),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (stale) BankTheme.status.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(line.balance?.formatted() ?: "—", style = MaterialTheme.typography.titleMedium)
            }
        }
        // Le versement du mois : à faire (ambre), puis fait (vert) quelques jours.
        holdings.lines.forEach { line ->
            when (val reminder = line.reminder(today)) {
                is SavingReminder.Due -> StatusChip(
                    "${reminder.amount.formatted()} à verser sur ${line.account.name}",
                    StatusKind.WARNING,
                    Modifier.padding(top = 8.dp),
                )
                is SavingReminder.Done -> StatusChip(
                    "Versé sur ${line.account.name} le ${reminder.on.format(dayMonth)}",
                    StatusKind.GOOD,
                    Modifier.padding(top = 8.dp),
                )
                null -> Unit
            }
        }
    }
}

/** « relié · lu le 2 nov. », « saisi le 12 oct. · à mettre à jour », « ≈ 4 343 € net · +500 € ». */
private fun accountCaption(line: HoldingLine, today: LocalDate): String {
    val estimate = line.estimate
    if (estimate != null) return "≈ ${estimate.net.formatted()} net · ${estimate.gain.signed()}"
    val asOf = line.asOf
    val origin = when {
        line.account.linked -> "relié" + (asOf?.let { " · lu le ${it.format(dayMonth)}" } ?: "")
        asOf != null -> "saisi le ${asOf.format(dayMonth)}"
        else -> "solde à saisir"
    }
    return origin + if (line.isStale(today)) " · à mettre à jour" else ""
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

/** Petit titre de famille dans la carte des enveloppes, avec un fait en regard. */
@Composable
private fun FamilyCaption(title: String, detail: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Les charges fixes en une ligne (« 4 sur 6 prélevées »), dépliable. */
@Composable
private fun ChargesRow(charges: List<EnvelopeLine>, onOpenEnvelope: (EnvelopeLine) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val paid = charges.count { it.spent.isPositive }
    val left = charges.filter { !it.spent.isPositive }.fold(Money.ZERO) { sum, it -> sum + it.remaining }
    Row(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Charges fixes", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(
                "$paid sur ${charges.size} prélevées",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(left.formatted(), style = MaterialTheme.typography.titleMedium)
            Text("à venir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        charges.forEach { line ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenEnvelope(line) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line.envelope.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (line.spent.isPositive) "prélevée · ${line.spent.formatted()}" else "à venir · ${line.allocated.formatted()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
