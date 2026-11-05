package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.R
import fr.baptiste.bank.domain.budget.YearSpending
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.theme.BankTheme
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/*
 * L'ossature de l'interface (lot 3 de la refonte) : la barre du haut (Réglages, titre, Ajouter),
 * la barre des mois partagée par Résumé, Budget et Dépenses, le bouton Claude à côté de la barre
 * d'onglets, et les sheets Ajouter, Réglages et Année.
 */

/** Barre du haut : la roue des Réglages à gauche, le titre de l'onglet, le + à droite. */
@Composable
fun GlassTopBar(
    title: String,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    settingsBadge: Boolean = false,
    /** La loupe : chercher une catégorie, une enveloppe, une dépense (comme la recherche d'iOS). */
    onSearch: (() -> Unit)? = null,
) {
    // Le titre reste centré sur l'écran, quel que soit le nombre de boutons de chaque côté.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        // Barre du haut plate, comme iOS : des icônes simples, le + en bleu plein. Le verre reste
        // réservé à la barre d'onglets et au bouton Claude, en bas.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlatIconButton(painterResource(R.drawable.ic_settings), "Réglages", onSettings, badge = settingsBadge)
            Spacer(Modifier.weight(1f))
            if (onSearch != null) FlatIconButton(painterResource(R.drawable.ic_search), "Rechercher", onSearch)
            FlatIconButton(painterResource(R.drawable.ic_add), "Ajouter", onAdd, filled = true, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Bouton d'icône plat (48 dp de cible) : sans fond, ou en bleu plein pour l'action principale. */
@Composable
private fun FlatIconButton(painter: androidx.compose.ui.graphics.painter.Painter, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, badge: Boolean = false) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (filled) Glass.Accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        if (badge) {
            // Pastille d'accent (le rouge est réservé à « dépassé ») ; le détail est dans les Réglages.
            Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).size(8.dp).clip(CircleShape).background(Color(0xFF7DB4FF)))
        }
    }
}

/** Le mois choisi et la façon d'en changer : partagés par Résumé, Budget et Dépenses. */
data class MonthNavigation(
    val selected: YearMonth,
    /** Les mois proposés, du plus ancien au mois en cours. */
    val months: List<YearMonth>,
    val onSelect: (YearMonth) -> Unit,
    val onYear: () -> Unit,
)

val LocalMonthNavigation = compositionLocalOf<MonthNavigation?> { null }

private fun YearMonth.shortName(): String {
    // Noms courts (« Sept. », « Oct. ») : trois mois et « Année » tiennent dans la largeur.
    val name = month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.FRANCE).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    return if (year == YearMonth.now().year) name else "$name ${year % 100}"
}

/** Barre des mois : une capsule par mois, le mois choisi en clair, puis « Année ». */
@Composable
fun MonthStripBar(modifier: Modifier = Modifier) {
    val navigation = LocalMonthNavigation.current ?: return
    // Un contrôle segmenté plat, à largeur égale : quatre mois et « Année », rien de tronqué.
    // Les mois plus anciens : glisser le contenu, ou « Année ».
    val months = navigation.months
    val index = months.indexOf(navigation.selected).coerceAtLeast(0)
    val first = if (index >= months.size - 4) (months.size - 4).coerceAtLeast(0) else (index - 1).coerceAtLeast(0)
    val window = months.subList(first, minOf(first + 4, months.size))
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SegmentTrack)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        window.forEach { month ->
            val selected = month == navigation.selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) SegmentSelected else Color.Transparent)
                    .clickable(role = Role.Tab) { navigation.onSelect(month) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    month.shortName(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .clickable(role = Role.Button, onClick = navigation.onYear),
            contentAlignment = Alignment.Center,
        ) {
            Text("Année", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

/** Le bouton Claude : rond, en verre teinté, à droite de la barre d'onglets. */
@Composable
fun ClaudeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassIconButton(
        painter = painterResource(R.drawable.ic_sparkle),
        contentDescription = "Claude",
        onClick = onClick,
        accent = true,
        modifier = modifier,
        size = 64.dp,
    )
}

/** Une ligne de sheet : un titre, une précision, un chevron. */
@Composable
private fun SheetRow(title: String, detail: String? = null, badge: Boolean = false, dot: Color? = null, indent: androidx.compose.ui.unit.Dp = 0.dp, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 24.dp + indent, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.padding(end = 12.dp).size(10.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (badge) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(BankTheme.status.over))
        Chevron()
    }
}

/** Ce que le + peut ajouter. */
enum class AddChoice(val title: String, val detail: String) {
    EXPENSE("Dépense", "Un achat, un paiement"),
    PLANNED("Achat prévu", "Un prix et une date"),
    ENVELOPE("Enveloppe", "Un montant à suivre"),
    ASIDE("Mise de côté", "Vers l'épargne"),
}

/** Sheet « Ajouter » : la dépense en premier. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onPick: (AddChoice) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Ajouter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            AddChoice.entries.forEachIndexed { index, choice ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 24.dp), color = Color.White.copy(alpha = 0.08f))
                SheetRow(choice.title, choice.detail) { onPick(choice) }
            }
        }
    }
}

/** Une entrée des Réglages. */
data class SettingsEntry(val title: String, val detail: String? = null, val badge: Boolean = false, val onClick: () -> Unit)

/**
 * Sheet « Réglages », à la place du menu ⋮. Les réglages pour le développeur ([developer]) ne
 * s'affichent qu'après sept touches sur le numéro de version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    entries: List<SettingsEntry>,
    developer: List<SettingsEntry>,
    version: String,
    onDismiss: () -> Unit,
) {
    var taps by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.navigationBarsPadding()) {
            item { Text("Réglages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)) }
            entries.forEachIndexed { index, entry ->
                item(key = entry.title) {
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 24.dp), color = Color.White.copy(alpha = 0.08f))
                    SheetRow(entry.title, entry.detail, entry.badge, onClick = entry.onClick)
                }
            }
            if (taps >= 7) {
                item { Text("Développeur", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
                developer.forEach { entry -> item(key = "dev-" + entry.title) { SheetRow(entry.title, entry.detail, onClick = entry.onClick) } }
            }
            item {
                Text(
                    if (taps in 4..6) "$version · encore ${7 - taps}" else version,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { taps++ }
                        .padding(vertical = 20.dp),
                )
            }
        }
    }
}

/** Sheet « Année » : l'histogramme et les catégories de l'année ; toucher un mois y mène. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearSheet(
    year: Int,
    spending: YearSpending?,
    canNextYear: Boolean,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSort: () -> Unit,
    recurring: List<fr.baptiste.bank.domain.budget.RecurringExpense>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item(key = "year-switch") {
                PeriodSwitcher(
                    label = year.toString(),
                    canPrevious = true,
                    canNext = canNextYear,
                    onPrevious = onPreviousYear,
                    onNext = onNextYear,
                    note = if (year == YearMonth.now().year) "de janvier à aujourd'hui" else "année terminée",
                )
            }
            if (spending != null && spending.year == year) {
                yearAnalysis(spending, onSort, recurring, onSelectMonth)
            }
        }
    }
}

/** Ce que la recherche peut ouvrir. */
sealed interface SearchHit {
    data class Category(val name: String) : SearchHit
    data class EnvelopeHit(val envelope: fr.baptiste.bank.domain.model.Envelope) : SearchHit
    data class Operation(val row: fr.baptiste.bank.data.local.OperationRow) : SearchHit
}

/**
 * Recherche de l'onglet Dépenses. Elle suit le mois choisi (« Tous les mois » l'élargit) ; vide,
 * elle liste toutes les catégories et leurs enveloppes, pour parcourir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    month: YearMonth,
    envelopes: List<fr.baptiste.bank.domain.model.Envelope>,
    monthOperations: List<fr.baptiste.bank.data.local.OperationRow>,
    searchAll: (String) -> kotlinx.coroutines.flow.Flow<List<fr.baptiste.bank.data.local.OperationRow>>,
    onOpen: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var allMonths by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val key = fr.baptiste.bank.domain.bank.envelopeNameKey(query)
    val allRows by androidx.compose.runtime.produceState(emptyList<fr.baptiste.bank.data.local.OperationRow>(), query, allMonths) {
        if (allMonths && query.isNotBlank()) searchAll(query).collect { value = it } else value = emptyList()
    }
    val monthName = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Fermer") }
            Text("Rechercher", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(72.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            androidx.compose.material3.TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Catégorie, enveloppe, dépense") },
                leadingIcon = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(vertical = 8.dp)) {
                SegmentedToggle(listOf(monthName.replaceFirstChar { it.titlecase(Locale.FRANCE) }, "Tous les mois"), if (allMonths) 1 else 0) { allMonths = it == 1 }
            }
        }
        val categories = envelopes.map { it.group?.trim()?.takeIf { g -> g.isNotEmpty() } ?: fr.baptiste.bank.domain.model.DEFAULT_CATEGORY }.distinct().sorted()
        fun matches(text: String) = key.isNotBlank() && fr.baptiste.bank.domain.bank.envelopeNameKey(text).contains(key)
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (key.isBlank()) {
                // Recherche vide : tout parcourir.
                categories.forEach { category ->
                    item(key = "c-$category") { SheetRow(category, dot = fr.baptiste.bank.ui.theme.categoryColor(category)) { onOpen(SearchHit.Category(category)) } }
                    envelopes.filter { (it.group?.trim()?.takeIf { g -> g.isNotEmpty() } ?: fr.baptiste.bank.domain.model.DEFAULT_CATEGORY) == category }
                        .forEach { envelope -> item(key = "e-${envelope.id}") { SheetRow(envelope.name, indent = 22.dp) { onOpen(SearchHit.EnvelopeHit(envelope)) } } }
                }
                return@LazyColumn
            }
            val foundCategories = categories.filter(::matches)
            val foundEnvelopes = envelopes.filter { matches(it.name) }
            val rows = if (allMonths) allRows else monthOperations.filter { matches(it.label) || matches(it.bankLabel) }
            if (foundCategories.isNotEmpty()) item { SearchTitle("Catégories") }
            foundCategories.forEach { item(key = "fc-$it") { SheetRow(it, dot = fr.baptiste.bank.ui.theme.categoryColor(it)) { onOpen(SearchHit.Category(it)) } } }
            if (foundEnvelopes.isNotEmpty()) item { SearchTitle("Enveloppes") }
            foundEnvelopes.forEach { e -> item(key = "fe-${e.id}") { SheetRow(e.name, e.group) { onOpen(SearchHit.EnvelopeHit(e)) } } }
            if (rows.isNotEmpty()) item { SearchTitle(if (allMonths) "Opérations, tous les mois" else "Opérations de $monthName") }
            rows.forEach { row ->
                item(key = "o-${row.id}") {
                    val amount = fr.baptiste.bank.domain.money.Money(row.amountCents)
                    SheetRow(
                        row.label,
                        "${row.date.dayOfMonth} ${row.date.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE)} · " +
                            (if (amount.isPositive) "+" else "") + amount.formatted(),
                    ) { onOpen(SearchHit.Operation(row)) }
                }
            }
            if (foundCategories.isEmpty() && foundEnvelopes.isEmpty() && rows.isEmpty()) {
                item {
                    Text(
                        if (allMonths) "Aucun résultat." else "Rien en $monthName. Essaie « Tous les mois ».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
}
