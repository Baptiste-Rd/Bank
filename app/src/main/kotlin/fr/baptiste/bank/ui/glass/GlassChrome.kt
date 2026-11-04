package fr.baptiste.bank.ui.glass

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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "Réglages",
            onClick = onSettings,
            badge = settingsBadge,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        GlassIconButton(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = "Ajouter",
            onClick = onAdd,
            accent = true,
        )
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
    val name = month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRANCE).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    return if (year == YearMonth.now().year) name else "$name ${year % 100}"
}

/** Barre des mois : une capsule par mois, le mois choisi en clair, puis « Année ». */
@Composable
fun MonthStripBar(modifier: Modifier = Modifier) {
    val navigation = LocalMonthNavigation.current ?: return
    val state = rememberLazyListState()
    val index = navigation.months.indexOf(navigation.selected).coerceAtLeast(0)
    LaunchedEffect(navigation.selected, navigation.months.size) {
        // Le mois choisi au milieu de la barre (à peu près : deux capsules à sa gauche).
        state.scrollToItem((index - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(navigation.months, key = { it.toString() }) { month ->
            val selected = month == navigation.selected
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
                    .clickable(role = Role.Tab) { navigation.onSelect(month) }
                    .semantics { this.selected = selected }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    month.shortName(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) Color(0xFF0A1228) else Color.White.copy(alpha = 0.82f),
                )
            }
        }
        item(key = "year") {
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(role = Role.Button, onClick = navigation.onYear)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Année", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
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
private fun SheetRow(title: String, detail: String? = null, badge: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    ENVELOPE("Enveloppe", "Un nouveau budget"),
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
                    SheetRow(entry.title, entry.detail, entry.badge, entry.onClick)
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
