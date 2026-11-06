package fr.baptiste.bank.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.EnvelopeUsage
import fr.baptiste.bank.domain.bank.isNameTaken
import fr.baptiste.bank.domain.budget.AmountSuggestion
import fr.baptiste.bank.domain.budget.monthShare
import fr.baptiste.bank.domain.model.DEFAULT_CATEGORY
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.categoryChoices
import fr.baptiste.bank.domain.model.storedCategory
import fr.baptiste.bank.domain.model.suggestCategory
import fr.baptiste.bank.domain.model.IdeaUse
import fr.baptiste.bank.domain.model.searchCatalog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.parseMoney
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.format.summary
import fr.baptiste.bank.ui.format.unitName
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Créer ou modifier un budget en trois questions : combien ? jusqu'à quand ? et ensuite ?
 *
 * On ne choisit plus un « type » : on dit à quoi sert l'argent (le quotidien, une charge, un budget
 * rare ou un projet, de l'épargne). Un budget rare a une remise à zéro et, s'il recommence, un
 * rythme et un montant pour la suite (« 26 € jusqu'au 1er janvier, puis 26 € tous les 3 mois »).
 * Le reste (départ, rythme d'une charge, protection) est rangé dans « Plus d'options ».
 */

private const val NEW_CATEGORY = "Nouveau groupe…"
private val day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)
private fun LocalDate.text(): String = format(day)

/** À quoi sert l'argent : ce qui décide de la famille de l'enveloppe. */
private enum class Use(val label: String) {
    DAILY("Au quotidien"),
    FIXED("Charge fixe"),
    RARE("Rare ou ponctuel"),
    SAVINGS("Épargne"),
}

private fun EnvelopeType.use(): Use = when (this) {
    EnvelopeType.DAILY -> Use.DAILY
    EnvelopeType.FIXED -> Use.FIXED
    EnvelopeType.SAVINGS -> Use.SAVINGS
    EnvelopeType.RECURRING, EnvelopeType.ANNUAL, EnvelopeType.PLANNED -> Use.RARE
}


@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EnvelopeDialog(
    initial: Envelope?,
    onDismiss: () -> Unit,
    onSave: (Envelope) -> Unit,
    /** Arrêter : elle disparaît des mois à venir et reste dans ceux qu'elle a couverts. */
    onArchive: (Envelope) -> Unit,
    /** Catégories que j'ai déjà créées, proposées avec la liste de départ. */
    groups: List<String> = emptyList(),
    /** Catégories imbriquées : la mère de chaque catégorie (voir parentsOf). */
    categoryParents: Map<String, String> = emptyMap(),
    /** Début du suivi : la part par mois d'une provision rattrapée en dépend. */
    trackingStart: YearMonth? = null,
    /** Noms des autres enveloppes (actives et archivées) : un nom ne sert qu'une fois. */
    takenNames: List<String> = emptyList(),
    /** Montant proposé d'après les mois passés (l'enveloppe elle-même, sinon sa catégorie). */
    suggest: suspend (category: String?, frequency: Frequency) -> AmountSuggestion? = { _, _ -> null },
    /** Ce qui est rangé dans l'enveloppe, pour annoncer une suppression définitive. */
    usage: suspend () -> EnvelopeUsage? = { null },
    onDelete: (Envelope) -> Unit = {},
) {
    val today = LocalDate.now()
    // Un modèle (id 0) pré-remplit le formulaire mais reste une création.
    val isExisting = initial != null && initial.id != 0L
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var category by rememberSaveable {
        mutableStateOf(initial?.group ?: DEFAULT_CATEGORY.takeIf { initial?.parentId != null } ?: suggestCategory(initial?.name.orEmpty()) ?: DEFAULT_CATEGORY)
    }
    var categoryChosen by rememberSaveable { mutableStateOf(initial?.group != null || isExisting) }
    var newCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var use by rememberSaveable { mutableStateOf(initial?.type?.use() ?: Use.DAILY) }

    // Combien ? (le premier budget pour une création, le montant par période sinon)
    var amountText by rememberSaveable {
        mutableStateOf((if (isExisting) initial?.amount else initial?.firstAmount ?: initial?.amount)?.toFieldText().orEmpty())
    }
    // Budget rare : remise à zéro, recommencer, rythme, montant ensuite, garder le reste.
    val currentPeriod = initial?.takeIf { isExisting }?.periodContaining(today)
    var reset by remember {
        mutableStateOf(initial?.firstReset?.takeIf { it.isAfter(today) } ?: currentPeriod?.endInclusive?.plusDays(1) ?: LocalDate.of(today.year + 1, 1, 1))
    }
    // Une enveloppe existante ne change de remise à zéro que si on y touche.
    var resetTouched by rememberSaveable { mutableStateOf(!isExisting) }
    var repeats by rememberSaveable { mutableStateOf(initial?.repeats ?: true) }
    var rareEveryText by rememberSaveable {
        mutableStateOf(initial?.takeIf { it.type.use() == Use.RARE && it.frequency.scale in listOf(Scale.MONTH, Scale.YEAR) }?.frequency?.every?.toString() ?: "3")
    }
    var rareScale by rememberSaveable {
        mutableStateOf(initial?.frequency?.scale?.takeIf { it == Scale.YEAR } ?: Scale.MONTH)
    }
    var thenText by rememberSaveable { mutableStateOf(initial?.amount?.toFieldText().orEmpty()) }
    var thenEdited by rememberSaveable { mutableStateOf(isExisting) }
    var keep by rememberSaveable { mutableStateOf(initial?.carryOver ?: true) }

    // Plus d'options : départ, rythme d'une charge ou du quotidien, protection.
    var more by rememberSaveable { mutableStateOf(false) }
    var start by remember { mutableStateOf(initial?.start ?: if (isExisting) null else today.withDayOfMonth(1)) }
    var scale by rememberSaveable { mutableStateOf(initial?.frequency?.scale?.takeIf { initial.type.use() != Use.RARE } ?: Scale.MONTH) }
    var everyText by rememberSaveable { mutableStateOf(initial?.frequency?.every?.takeIf { initial.type.use() != Use.RARE }?.toString() ?: "1") }
    var anchor by remember { mutableStateOf(initial?.anchor ?: today) }
    var protected by rememberSaveable {
        mutableStateOf(initial?.status?.let { it == EnvelopeStatus.PROTECTED } ?: false)
    }

    val amount = if (amountText.isBlank()) Money.ZERO else parseMoney(amountText)
    val thenAmount = if (thenEdited) parseMoney(thenText) else amount
    val rareEvery = rareEveryText.toIntOrNull()?.takeIf { it in 1..99 }
    val every = everyText.toIntOrNull()?.takeIf { it in 1..999 }
    val frequency: Frequency? = when (use) {
        Use.RARE -> when {
            !repeats -> Frequency(monthsBetween(start ?: today, reset), Scale.MONTH)
            rareEvery != null -> Frequency(rareEvery, rareScale)
            else -> null
        }
        else -> every?.let { Frequency(it, scale) }
    }
    val nameTaken = isNameTaken(name, takenNames)
    val resetValid = use != Use.RARE || reset.isAfter(start ?: today.minusDays(1))
    val canSave = name.isNotBlank() && !nameTaken && amount != null && !amount.isNegative && frequency != null &&
        resetValid && (use != Use.RARE || !repeats || (thenAmount != null && !thenAmount.isNegative))

    // Montant proposé : la moyenne des mois passés de l'enveloppe, recalculée quand le rythme change.
    var suggestion by remember { mutableStateOf<AmountSuggestion?>(null) }
    val categoryForSuggestion = newCategory?.takeIf { it.isNotBlank() } ?: category
    LaunchedEffect(categoryForSuggestion, frequency) {
        suggestion = frequency?.let { suggest(categoryForSuggestion, it) }
    }

    val draft: Envelope? = if (canSave && amount != null && frequency != null) {
        buildEnvelope(
            initial = initial, isExisting = isExisting, today = today, name = name.trim(),
            category = storedCategory(newCategory?.takeIf { it.isNotBlank() } ?: category),
            use = use, amount = amount, thenAmount = thenAmount ?: amount, frequency = frequency,
            start = start, reset = reset.takeIf { resetTouched }, repeats = repeats, keep = keep, anchor = anchor, protected = protected,
        )
    } else {
        null
    }

    var confirmArchive by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<EnvelopeUsage?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    if (askDelete) {
        LaunchedEffect(Unit) {
            confirmDelete = usage() ?: EnvelopeUsage(0, Money.ZERO, 0, Money.ZERO)
            askDelete = false
        }
    }

    if (confirmArchive && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Arrêter ${initial.name} ?") },
            text = {
                BlurBehindWindow()
                Text(
                    "Elle disparaît des mois à venir et reste dans les mois qu'elle a couverts, avec ses dépenses. " +
                        "Tu pourras la reprendre depuis le bas de l'onglet Budget.",
                )
            },
            confirmButton = { TextButton(onClick = { onArchive(initial) }) { Text("Arrêter") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Annuler") } },
        )
        return
    }
    confirmDelete?.let { found ->
        if (initial == null) return@let
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Supprimer ${initial.name} définitivement ?") },
            text = {
                BlurBehindWindow()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elle disparaît de tous les mois, comme si elle n'avait jamais existé. Pour une erreur de saisie.")
                    if (found.bankCount > 0) {
                        Text("${found.bankCount} dépense(s) de la banque (${found.bankAmount.formatted()}) repartent dans « À trier ».")
                    }
                    if (found.manualCount > 0) {
                        Text("${found.manualCount} dépense(s) saisie(s) à la main (${found.manualAmount.formatted()}) sont effacées.")
                    }
                    Text("Ses règles de tri et ses ajouts et retraits sont effacés. Impossible à annuler.", color = BankThemeColors.over())
                }
            },
            confirmButton = {
                TextButton(onClick = { onDelete(initial) }) { Text("Supprimer", color = BankThemeColors.over()) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annuler") } },
        )
        return
    }

    // Une sheet, comme toute saisie : Annuler et Enregistrer en haut (lot 5 de la refonte).
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    var proposedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
            SheetHeader(
                title = if (isExisting) "Modifier l'enveloppe" else "Nouvelle enveloppe",
                onCancel = onDismiss,
                action = "Enregistrer",
                enabled = draft != null,
                onAction = { draft?.let(onSave) },
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (!categoryChosen) category = suggestCategory(it) ?: DEFAULT_CATEGORY
                    },
                    label = { Text("Nom") },
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text("Une enveloppe porte déjà ce nom.") }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Noms tout faits : le nom, et pour une création sa catégorie et son usage.
                TextButton(onClick = { catalogOpen = !catalogOpen }) {
                    Text(if (catalogOpen) "Fermer la liste des noms" else "Choisir un nom tout fait")
                }
                if (catalogOpen) {
                    OutlinedTextField(
                        value = catalogQuery,
                        onValueChange = { catalogQuery = it },
                        label = { Text("Chercher un nom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val ideas = remember(catalogQuery) { searchCatalog(catalogQuery) }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(280.dp)) {
                        ideas.groupBy { it.theme }.forEach { (theme, list) ->
                            item(key = "theme-$theme") {
                                Text(theme, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                            }
                            items(list.size, key = { "idea-" + list[it].name }) { index ->
                                val idea = list[index]
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            name = idea.name
                                            catalogOpen = false
                                            if (!isExisting) {
                                                category = idea.category
                                                newCategory = null
                                                categoryChosen = true
                                                use = idea.use.toUse()
                                            } else if (idea.category != category) {
                                                // Une enveloppe existante ne change que de nom, sauf si on accepte la catégorie.
                                                proposedCategory = idea.category
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(idea.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Text("${idea.category} · ${idea.use.toUse().label.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                proposedCategory?.let { proposed ->
                    AssistChip(
                        onClick = {
                            category = proposed
                            newCategory = null
                            proposedCategory = null
                        },
                        label = { Text("Ranger aussi dans $proposed") },
                    )
                }
                Text("C'est pour", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Use.entries.forEach { option ->
                        FilterChip(selected = use == option, onClick = { use = option }, label = { Text(option.label) })
                    }
                }

                // 1. Combien ?
                MoneyField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = when {
                        use == Use.RARE && !isExisting -> "Combien"
                        use == Use.SAVINGS -> "Combien mettre de côté ?"
                        else -> "Combien par période ?"
                    },
                )
                suggestion?.let { proposed ->
                    AssistChip(
                        onClick = { amountText = proposed.amount.toFieldText() },
                        label = {
                            // D'où vient le chiffre : l'historique de cette enveloppe (une nouvelle n'a pas de proposition).
                            val span = if (proposed.months > 1) "sur ${proposed.months} mois" else "sur le dernier mois"
                            Text("Proposé : ${proposed.amount.summary()} (moyenne $span)")
                        },
                    )
                }

                if (use == Use.RARE) {
                    // 2. Jusqu'à quand ?
                    DateField(label = "Remise à zéro le", date = reset, onDateChange = {
                        reset = it
                        resetTouched = true
                    })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { reset = today.withDayOfMonth(1).plusMonths(1); resetTouched = true }, label = { Text("1er du mois prochain") })
                        AssistChip(onClick = { reset = LocalDate.of(today.year + 1, 1, 1); resetTouched = true }, label = { Text("1er janvier") })
                        AssistChip(onClick = { reset = today.withDayOfMonth(1).plusMonths(3); resetTouched = true }, label = { Text("Dans 3 mois") })
                    }
                    if (!resetValid) {
                        Text("La remise à zéro doit venir après le départ.", style = MaterialTheme.typography.bodySmall, color = BankThemeColors.over())
                    }
                }

                // Catégorie : pour les statistiques (Courses et Bar dans Nourriture).
                // Le même arbre que « Déplacer » : catégories de départ, enregistrées (même vides) et sous-catégories.
                val treeRows = remember(groups, categoryParents) {
                    fr.baptiste.bank.domain.model.categoryTreeRows(groups, categoryParents)
                }
                val depthOf = remember(treeRows) { treeRows.associate { (name, depth) -> fr.baptiste.bank.domain.bank.envelopeNameKey(name) to depth } }
                val categoryOptions = remember(treeRows) { treeRows.map { it.first } + fr.baptiste.bank.domain.model.DEFAULT_CATEGORY + NEW_CATEGORY }
                ChoiceField(
                    label = "Ranger dans",
                    selected = if (newCategory != null) NEW_CATEGORY else category,
                    options = categoryOptions,
                    // Une sous-catégorie est décalée sous sa mère, comme dans « Déplacer ».
                    optionLabel = { name -> "\u2003".repeat(depthOf[fr.baptiste.bank.domain.bank.envelopeNameKey(name)] ?: 0) + name },
                    onSelect = {
                        categoryChosen = true
                        if (it == NEW_CATEGORY) newCategory = "" else {
                            newCategory = null
                            category = it
                        }
                    },
                )
                newCategory?.let { typed ->
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { newCategory = it },
                        label = { Text("Nom de la catégorie") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                draft?.let { Text(summary(it, today, trackingStart), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                TextButton(onClick = { more = !more }) { Text(if (more) "Moins d'options" else "Plus d'options") }
                if (more) {
                    val chosenStart = start
                    if (chosenStart == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Compte toutes les dépenses rangées ici, même anciennes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { start = today.withDayOfMonth(1) }) { Text("Choisir un départ") }
                        }
                    } else {
                        DateField(label = "Départ", date = chosenStart, onDateChange = { start = it })
                        Text(
                            "Une dépense antérieure peut y être rangée, mais ne compte pas dans son reste.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (use != Use.RARE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (scale == Scale.WEEK) "Toutes les" else "Tous les", style = MaterialTheme.typography.bodyLarge)
                            OutlinedTextField(
                                value = everyText,
                                onValueChange = { value -> everyText = value.filter(Char::isDigit).take(3) },
                                singleLine = true,
                                isError = every == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(72.dp),
                            )
                            ChoiceField(
                                label = "",
                                selected = scale,
                                options = Scale.entries,
                                optionLabel = { it.unitName(every ?: 1) },
                                onSelect = { scale = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (frequency != null && !frequency.isCalendar) {
                            DateField(label = "Début d'une période", date = anchor, onDateChange = { anchor = it })
                        }
                    }
                    if (use == Use.RARE) {
                        // 3. Et ensuite ?
                        Text("Et ensuite ?", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = repeats, onClick = { repeats = true; resetTouched = true }, label = { Text("Recommencer") })
                            FilterChip(selected = !repeats, onClick = { repeats = false; resetTouched = true }, label = { Text("Une seule fois") })
                        }
                        if (repeats) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Tous les", style = MaterialTheme.typography.bodyLarge)
                                OutlinedTextField(
                                    value = rareEveryText,
                                    onValueChange = { value -> rareEveryText = value.filter(Char::isDigit).take(2) },
                                    singleLine = true,
                                    isError = rareEvery == null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(72.dp),
                                )
                                ChoiceField(
                                    label = "",
                                    selected = rareScale,
                                    options = listOf(Scale.MONTH, Scale.YEAR),
                                    optionLabel = { it.unitName(rareEvery ?: 1) },
                                    onSelect = { rareScale = it },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (!isExisting) {
                                MoneyField(
                                    value = if (thenEdited) thenText else amountText,
                                    onValueChange = {
                                        thenText = it
                                        thenEdited = true
                                    },
                                    label = "Puis, à chaque fois",
                                )
                            }
                            SwitchRow("Garder ce qui reste pour la fois suivante", "Sinon, le reste va à l'épargne à la remise à zéro.", keep) { keep = it }
                        }
                    }
                    SwitchRow("Protégée", "Jamais utilisée pour ajouter de l'argent à une autre enveloppe.", protected) { protected = it }
                }

                if (isExisting && initial != null) {
                    Row {
                        TextButton(onClick = { confirmArchive = true }) { Text("Arrêter") }
                        TextButton(onClick = { askDelete = true }) { Text("Supprimer définitivement", color = BankThemeColors.over()) }
                    }
                }
            }
    }
}

private fun IdeaUse.toUse(): Use = when (this) {
    IdeaUse.DAILY -> Use.DAILY
    IdeaUse.FIXED -> Use.FIXED
    IdeaUse.RARE -> Use.RARE
    IdeaUse.SAVINGS -> Use.SAVINGS
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Mois couverts du départ à la veille de la remise à zéro, au moins 1. */
private fun monthsBetween(start: LocalDate, reset: LocalDate): Int =
    (ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(reset.minusDays(1))) + 1).coerceAtLeast(1).toInt()

/** L'enveloppe que décrit le formulaire. */
private fun buildEnvelope(
    initial: Envelope?,
    isExisting: Boolean,
    today: LocalDate,
    name: String,
    category: String?,
    use: Use,
    amount: Money,
    thenAmount: Money,
    frequency: Frequency,
    start: LocalDate?,
    /** Remise à zéro choisie (null : celle de l'enveloppe, inchangée). */
    reset: LocalDate?,
    repeats: Boolean,
    keep: Boolean,
    anchor: LocalDate,
    protected: Boolean,
): Envelope {
    val type = when (use) {
        Use.DAILY -> EnvelopeType.DAILY
        Use.FIXED -> EnvelopeType.FIXED
        Use.SAVINGS -> EnvelopeType.SAVINGS
        Use.RARE -> when {
            // Une ancienne provision garde son type ; un nouveau budget sans suite est un achat prévu.
            isExisting && initial != null && initial.type.use() == Use.RARE && (repeats || initial.type != EnvelopeType.PLANNED) ->
                if (repeats && initial.type == EnvelopeType.PLANNED) EnvelopeType.RECURRING else initial.type
            !repeats -> EnvelopeType.PLANNED
            frequency == Frequency.YEARLY -> EnvelopeType.ANNUAL
            else -> EnvelopeType.RECURRING
        }
    }
    val base = initial ?: Envelope(id = 0, name = "", type = type, frequency = frequency, amount = Money.ZERO, status = type.defaultStatus)
    val rare = use == Use.RARE
    // Une ancienne enveloppe sans départ en reçoit un à l'enregistrement si besoin (voir saveEnvelope).
    val origin = if (rare && start == null && !isExisting) today.withDayOfMonth(1) else start
    return base.copy(
        name = name,
        type = type,
        frequency = frequency,
        amount = if (rare && repeats && !isExisting) thenAmount else amount,
        firstAmount = if (rare && repeats && !isExisting && thenAmount != amount) amount else base.firstAmount.takeIf { isExisting },
        firstReset = if (rare) reset ?: base.firstReset else null,
        repeats = if (rare) repeats else true,
        carryOver = rare && repeats && keep,
        start = origin,
        // Un nouveau budget rare met de côté à partir de ce mois ; un ponctuel suit le calcul d'un achat prévu.
        fundingStart = if (rare && !isExisting) YearMonth.from(today) else base.fundingStart,
        anchor = when {
            rare && !repeats -> origin
            rare -> null
            frequency.isCalendar -> null
            else -> anchor
        },
        group = category,
        status = if (protected || type == EnvelopeType.PLANNED && !isExisting) EnvelopeStatus.PROTECTED else EnvelopeStatus.FLEXIBLE,
    )
}

/** La phrase qui confirme le réglage : « 26 € jusqu'au 31 déc., puis 26 € tous les 3 mois ». */
private fun summary(envelope: Envelope, today: LocalDate, trackingStart: YearMonth?): String {
    val period = envelope.periodContaining(maxOf(today, envelope.start ?: today))
    val unit = envelope.frequency.scale.unitName(envelope.frequency.every)
    val rhythm = if (envelope.frequency.every == 1) "chaque ${unit}" else "tous les ${envelope.frequency.every} $unit"
    return when (envelope.type) {
        EnvelopeType.DAILY, EnvelopeType.FIXED -> "${envelope.amount.summary()} $rhythm"
        EnvelopeType.SAVINGS -> "${envelope.amount.summary()} mis de côté $rhythm"
        else -> {
            val first = envelope.amountIn(period)
            // Le même calcul que la fiche et Budget (rattrapage compris : il dépend du début du suivi).
            val share = envelope.monthShare(YearMonth.from(today), period, first, trackingStart)
            // La même date que la remise à zéro choisie : « d'ici le 1er janv. ».
            val reset = period.endInclusive.plusDays(1)
            val day = if (reset.dayOfMonth == 1) "1er" else "${reset.dayOfMonth}"
            "${first.summary()} d'ici le $day ${reset.format(java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.FRANCE))} · ${share.summary()} par mois"
        }
    }
}

/** Couleurs de statut lisibles hors d'un composable de thème (rouge du dépassement). */
private object BankThemeColors {
    @Composable
    fun over() = fr.baptiste.bank.ui.theme.BankTheme.status.over
}
