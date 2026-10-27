package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.ui.components.BlurBehindWindow
import fr.baptiste.bank.ui.envelopes.CategoryAvatar
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.envelopes.TemplateChips
import fr.baptiste.bank.ui.envelopes.envelopeTemplates
import fr.baptiste.bank.ui.envelopes.template
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.label
import fr.baptiste.bank.ui.home.MonthStrip
import fr.baptiste.bank.ui.home.todayExplanation
import fr.baptiste.bank.ui.operations.dayTitle
import fr.baptiste.bank.ui.theme.BankTheme
import fr.baptiste.bank.ui.theme.envelopeTypeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Contenu des trois onglets de l'interface verre.
 *
 * Organisation : chaque groupe a un titre de section (avec la pastille de couleur de son type
 * et un total à droite) puis une seule carte qui réunit ses lignes. Les lignes d'une carte sont
 * séparées par de l'espace, sans trait. Les couleurs suivent deux codes distincts :
 * - le type d'enveloppe (bleu, violet, cyan, ardoise, vert) pour se repérer ;
 * - le statut (bleu, ambre, rouge) pour ce qui demande une action.
 */

/** Le contenu défile sous la barre d'état et sous la barre d'onglets flottante, comme sur iOS. */
@Composable
private fun glassContentPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 112.dp)
}

/** Grand titre en haut de page, qui défile avec le contenu. La place à droite est celle des boutons. */
private fun LazyListScope.largeTitle(title: String, subtitle: String? = null) {
    item(key = "title") {
        Column(modifier = Modifier.padding(start = 4.dp, end = 120.dp, top = 8.dp, bottom = 8.dp)) {
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

/** Titre de section : une pastille de couleur facultative, le libellé, et un total à droite. */
private fun LazyListScope.sectionLabel(key: String, text: String, trailing: String? = null, dot: Color? = null) {
    item(key = key) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) {
                Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(dot))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Espace entre deux lignes d'une même carte : pas de trait, la respiration suffit. */
@Composable
private fun RowGap() {
    Spacer(Modifier.height(4.dp))
}

/** Somme de ce qu'il reste dans un groupe d'enveloppes, pour le titre de section. */
private fun List<EnvelopeLine>.remainingLabel(): String {
    val total = fold(Money.ZERO) { sum, line -> sum + line.remaining }
    return if (total.isNegative) "${(-total).formatted()} de dépassement" else "${total.formatted()} restants"
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
    // Lu ici : le contenu d'une LazyColumn se construit hors composition.
    val status = BankTheme.status
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
        val hasBudget = dashboard.dailyBudget.isPositive
        if (!hasBudget) {
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
            // Le chiffre du jour, posé directement sur le fond : c'est la seule chose en très grand.
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
        }
        // Le mois en un seul bloc : le rythme jour par jour (s'il y a un budget), puis les deux
        // chiffres qui comptent, côte à côte.
        item(key = "month") {
            GlassCard {
                Text("Ce mois-ci", style = MaterialTheme.typography.titleSmall)
                if (hasBudget) {
                    MonthStrip(
                        days = dashboard.monthDays,
                        averageShare = dashboard.averageDailyShare,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatTile(
                        label = "Dépensé",
                        value = dashboard.dailySpent.formatted(),
                        caption = buildString {
                            append("sur ${dashboard.dailyBudget.formatted()}")
                            if (dashboard.catchUpSpent.isPositive) {
                                append(", dont ${dashboard.catchUpSpent.formatted()} en rattrapage")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    val unallocated = dashboard.unallocated
                    StatTile(
                        label = "Portefeuille",
                        value = unallocated?.formatted() ?: "À définir",
                        caption = when {
                            unallocated == null -> "touche pour indiquer ton salaire"
                            unallocated.isNegative -> "enveloppes au-delà du salaire"
                            else -> "hors enveloppes"
                        },
                        valueColor = if (unallocated?.isNegative == true) BankTheme.status.over else null,
                        onClick = onEditIncome,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val watched = dashboard.alerts
        if (watched.isNotEmpty()) {
            val anyOver = watched.any { it.remaining.isNegative }
            sectionLabel(
                key = "watched-label",
                text = "À surveiller",
                trailing = if (watched.size > 1) "${watched.size} enveloppes" else "1 enveloppe",
                dot = if (anyOver) status.over else status.warning,
            )
            item(key = "watched") {
                GlassCard(contentPadding = 16.dp) {
                    watched.forEachIndexed { index, line ->
                        if (index > 0) RowGap()
                        EnvelopeRow(
                            line,
                            onClick = { onOpenEnvelope(line) },
                            onCover = { onCover(line) },
                            accent = envelopeTypeColor(line.envelope.type),
                        )
                    }
                }
            }
        }
        val daily = dashboard.envelopes.filter { it.envelope.type == EnvelopeType.DAILY && it !in watched }
        if (daily.isNotEmpty()) {
            sectionLabel("daily-label", "Quotidien", daily.remainingLabel(), envelopeTypeColor(EnvelopeType.DAILY))
            item(key = "daily") {
                GlassCard(contentPadding = 16.dp) {
                    daily.forEachIndexed { index, line ->
                        if (index > 0) RowGap()
                        EnvelopeRow(line, onClick = { onOpenEnvelope(line) }, accent = envelopeTypeColor(EnvelopeType.DAILY))
                    }
                }
            }
        }
    }
}

/** Chiffre clé dans une carte : libellé, valeur, précision. Un fond légèrement plus clair le détache. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // Toujours le même ordre de types, du plus quotidien au plus lointain.
        lines.groupBy { it.envelope.type }.toSortedMap(compareBy<EnvelopeType> { it.ordinal }).forEach { (type, typeLines) ->
            val color = envelopeTypeColor(type)
            sectionLabel("label-$type", type.label, typeLines.remainingLabel(), color)
            item(key = "group-$type") {
                GlassCard(contentPadding = 16.dp) {
                    typeLines.forEachIndexed { index, line ->
                        if (index > 0) RowGap()
                        EnvelopeRow(line, onClick = { onOpen(line) }, onCover = { onCover(line) }, accent = color)
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
            sectionLabel("archived-label", "Archivées", if (archived.size > 1) "${archived.size} enveloppes" else null)
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
    envelopes: List<Envelope>,
    unsortedCount: Int,
    onDelete: (Long) -> Unit,
    onSort: (OperationRow) -> Unit,
    onOpenBank: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<OperationRow?>(null) }
    val toSortColor = BankTheme.status.warning
    val today = LocalDate.now()
    val typeByName = remember(envelopes) { envelopes.associate { it.name to it.type } }

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
                        "Les dépenses que tu saisis apparaissent ici, ainsi que celles de ta banque une fois connectée.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenBank) { Text("Connecter ma banque") }
                }
            }
        }
        // Ce qui attend une décision passe avant la liste : tant qu'elle n'est pas rangée, une
        // opération de la banque ne compte dans aucun budget.
        if (unsortedCount > 0) {
            item(key = "to-sort") {
                GlassCard(contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 10.dp).size(10.dp).clip(CircleShape).background(toSortColor))
                        Text(
                            if (unsortedCount > 1) "$unsortedCount opérations à trier" else "1 opération à trier",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        "Touche une opération marquée « À trier » pour la ranger dans une enveloppe ou la mettre " +
                            "hors budget. Tant qu'elle n'est pas rangée, elle ne compte dans aucun budget.",
                        style = MaterialTheme.typography.bodySmall,
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
                        val firstEnvelope = operation.envelopeNames?.split(", ")?.firstOrNull()
                        val toSort = operation.envelopeNames == null && !operation.ignored
                        GlassOperationRow(
                            operation = operation,
                            color = firstEnvelope?.let { typeByName[it] }?.let(::envelopeTypeColor)
                                ?: if (toSort) toSortColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            // Saisie à la main : la toucher propose de la supprimer.
                            // Opération de la banque : la toucher permet de la ranger ou de la reclasser.
                            onClick = if (operation.source == TransactionSource.MANUAL) {
                                { pendingDelete = operation }
                            } else {
                                { onSort(operation) }
                            },
                        )
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

/**
 * Une opération : pastille de la catégorie (ambre « ? » si elle est à trier), libellé et enveloppe,
 * montant. La toucher supprime une saisie manuelle (après confirmation) ou range une opération de
 * la banque ; il n'y a donc pas d'icône sur chaque ligne.
 */
@Composable
private fun GlassOperationRow(operation: OperationRow, color: Color, onClick: (() -> Unit)?) {
    val details = buildList {
        add(operation.envelopeNames ?: if (operation.ignored) "Hors budget" else "À trier")
        if (operation.wholePeriod) {
            val period = operation.periodKey?.let { runCatching { Period.parse(it) }.getOrNull() }
            add(if (period != null) "rattrapage ${period.label}" else "rattrapage")
        } else if (operation.source == TransactionSource.MANUAL) {
            add("saisie à la main")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) {
                    val action = if (operation.source == TransactionSource.MANUAL) "Supprimer" else "Ranger"
                    Modifier.clickable(onClickLabel = action, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryAvatar(operation.envelopeNames ?: if (operation.ignored) "–" else "?", color)
        Column(modifier = Modifier.weight(1f)) {
            Text(operation.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                details.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Money(operation.amountCents).formatted(),
            style = MaterialTheme.typography.titleMedium,
            color = if (operation.amountCents > 0) BankTheme.status.good else MaterialTheme.colorScheme.onSurface,
        )
    }
}
