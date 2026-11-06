package fr.baptiste.bank.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.baptiste.bank.domain.budget.BudgetRow
import fr.baptiste.bank.domain.budget.EnvelopeLine
import fr.baptiste.bank.domain.budget.GroupNode
import fr.baptiste.bank.domain.budget.SpendFilter
import fr.baptiste.bank.domain.budget.SpendingRow
import fr.baptiste.bank.domain.budget.budgetRows
import fr.baptiste.bank.domain.budget.spendingRows
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.TreeDrop
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.components.SwipeAction
import fr.baptiste.bank.ui.components.SwipeColors
import fr.baptiste.bank.ui.components.SwipeRow
import fr.baptiste.bank.ui.envelopes.EnvelopeRow
import fr.baptiste.bank.ui.envelopes.family
import fr.baptiste.bank.ui.format.summary
import fr.baptiste.bank.ui.format.unitName
import fr.baptiste.bank.ui.theme.categoryColor

/*
 * L'arbre des enveloppes : le même dans les deux onglets. Budget montre ce qui est prévu et se
 * réorganise (rester appuyé, glisser, poser) ; Dépenses montre le dépensé du mois de chaque branche.
 */

private enum class DropZone { BEFORE, INTO, AFTER }

private data class DropTarget(val id: Long, val zone: DropZone)

/** Le point de la ligne où l'on pose : les bords pour se ranger à côté, le milieu pour entrer dedans. */
private fun zoneAt(bounds: Rect, y: Float): DropZone {
    val position = (y - bounds.top) / bounds.height.coerceAtLeast(1f)
    return when {
        position < 0.25f -> DropZone.BEFORE
        position > 0.75f -> DropZone.AFTER
        else -> DropZone.INTO
    }
}

/** Le montant d'une enveloppe, comme on l'a réglé : « 1 800 € par an », « 30 € tous les 3 mois ». */
private fun rhythmOf(envelope: Envelope): String {
    val frequency = envelope.frequency
    val unit = frequency.scale.unitName(frequency.every)
    return if (frequency.every == 1) "par $unit" else "tous les ${frequency.every} $unit"
}

@Composable
private fun Chevron(open: Boolean) {
    Icon(
        painterResource(fr.baptiste.bank.R.drawable.ic_chevron_right),
        contentDescription = if (open) "Replier" else "Déplier",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (open) 90f else 0f },
    )
}

/** L'emplacement du chevron : vide sur une ligne sans enfant, pour garder les noms alignés. */
@Composable
private fun ChevronSlot(hasChildren: Boolean, open: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .then(if (hasChildren) Modifier.clickable(onClick = onToggle, role = Role.Button) else Modifier),
        Alignment.Center,
    ) {
        if (hasChildren) Chevron(open)
    }
}

@Composable
private fun Dot(rootName: String) {
    Box(Modifier.padding(end = 10.dp).size(10.dp).clip(CircleShape).background(categoryColor(rootName)))
}

/**
 * Budget : l'arbre et ses montants, sans aucune dépense. Toucher une enveloppe ouvre ses réglages,
 * toucher un groupe le plie. Rester appuyé soulève la ligne, qui suit le doigt.
 */
@Composable
internal fun BudgetTree(
    envelopes: List<Envelope>,
    groups: List<GroupNode>,
    listState: LazyListState,
    onEdit: (Envelope) -> Unit,
    onMoveEnvelope: (Envelope) -> Unit,
    onRenameGroup: (String) -> Unit,
    onMoveGroup: (String) -> Unit,
    onDrop: (Long, TreeDrop) -> Unit,
) {
    var closed by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val rows = remember(envelopes, groups, closed) {
        budgetRows(envelopes, groups, (envelopes.map { it.id } + groups.map { it.id }).toSet() - closed.toSet())
    }
    // Le geste dure plus longtemps que la composition qui l'a lancé : il lit toujours les dernières valeurs.
    val currentRows by rememberUpdatedState(rows)
    val currentDrop by rememberUpdatedState(onDrop)
    val bounds = remember { HashMap<Long, Rect>() }
    var dragged by remember { mutableStateOf<Long?>(null) }
    var startY by remember { mutableFloatStateOf(0f) }
    var finger by remember { mutableFloatStateOf(0f) }
    var scrolled by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableStateOf<DropTarget?>(null) }
    val haptics = LocalHapticFeedback.current

    fun findTarget() {
        val moving = dragged ?: return
        val y = startY + finger
        val hit = currentRows.firstOrNull { it.id != moving && bounds[it.id]?.let { box -> y >= box.top && y <= box.bottom } == true }
        target = hit?.let { row -> DropTarget(row.id, zoneAt(bounds.getValue(row.id), y)) }
    }

    // Près d'un bord, la liste défile sous la ligne soulevée.
    val height = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val topEdge = with(LocalDensity.current) { 160.dp.toPx() }
    val bottomEdge = with(LocalDensity.current) { 180.dp.toPx() }
    LaunchedEffect(dragged) {
        if (dragged == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val y = startY + finger
            val speed = when {
                y < topEdge -> -14f
                y > height - bottomEdge -> 14f
                else -> 0f
            }
            if (speed != 0f) {
                scrolled += listState.scrollBy(speed)
                findTarget()
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            key(row.id) {
                val lifted = dragged == row.id
                val hovered = target?.takeIf { it.id == row.id }?.zone
                val accent = MaterialTheme.colorScheme.primary
                Box(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (lifted) 1f else 0f)
                        .onGloballyPositioned { bounds[row.id] = it.boundsInRoot() }
                        .graphicsLayer {
                            if (lifted) {
                                translationY = finger + scrolled
                                scaleX = 1.03f
                                scaleY = 1.03f
                                alpha = 0.92f
                                shadowElevation = 16.dp.toPx()
                            }
                        }
                        .drawBehind {
                            val stroke = 2.dp.toPx()
                            when (hovered) {
                                DropZone.BEFORE -> drawLine(accent, Offset(0f, 0f), Offset(size.width, 0f), stroke)
                                DropZone.AFTER -> drawLine(accent, Offset(0f, size.height), Offset(size.width, size.height), stroke)
                                DropZone.INTO -> drawRect(accent.copy(alpha = 0.14f))
                                null -> Unit
                            }
                        }
                        .pointerInput(row.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    startY = (bounds[row.id]?.top ?: 0f) + offset.y
                                    finger = 0f
                                    scrolled = 0f
                                    target = null
                                    dragged = row.id
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    finger += amount.y
                                    findTarget()
                                },
                                onDragEnd = {
                                    val drop = target
                                    dragged = null
                                    target = null
                                    if (drop != null) {
                                        if (drop.zone == DropZone.INTO) closed = closed - drop.id
                                        currentDrop(
                                            row.id,
                                            when (drop.zone) {
                                                DropZone.BEFORE -> TreeDrop.Before(drop.id)
                                                DropZone.AFTER -> TreeDrop.After(drop.id)
                                                DropZone.INTO -> TreeDrop.Into(drop.id)
                                            },
                                        )
                                    }
                                },
                                onDragCancel = {
                                    dragged = null
                                    target = null
                                },
                            )
                        },
                ) {
                    val envelope = row.envelope
                    SwipeRow(
                        key = "t-${row.id}",
                        actions = if (envelope != null) {
                            listOf(
                                SwipeAction("Modifier", SwipeColors.Neutral) { onEdit(envelope) },
                                SwipeAction("Déplacer", SwipeColors.Accent) { onMoveEnvelope(envelope) },
                            )
                        } else {
                            listOf(
                                SwipeAction("Renommer", SwipeColors.Neutral) { onRenameGroup(row.name) },
                                SwipeAction("Déplacer", SwipeColors.Accent) { onMoveGroup(row.name) },
                            )
                        },
                    ) {
                        BudgetTreeRow(
                            row = row,
                            open = row.id !in closed,
                            onToggle = { closed = if (row.id in closed) closed - row.id else closed + row.id },
                            onClick = {
                                if (envelope != null) {
                                    onEdit(envelope)
                                } else {
                                    closed = if (row.id in closed) closed - row.id else closed + row.id
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetTreeRow(row: BudgetRow, open: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    val envelope = row.envelope
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp * row.depth, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronSlot(row.hasChildren, open, onToggle)
        Dot(row.rootName)
        Column(Modifier.weight(1f)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (envelope == null) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (envelope != null) {
                Text(
                    envelope.type.family.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Un budget d'un autre rythme garde son montant, avec sa part du mois en dessous.
        val monthly = envelope == null || envelope.frequency == Frequency.MONTHLY || row.hasChildren
        Column(horizontalAlignment = Alignment.End) {
            Text(if (monthly) row.covered.summary() else envelope!!.amount.summary(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
            Text(
                if (monthly) "par mois" else "${rhythmOf(envelope!!)} · ${row.covered.summary()}/mois",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Dépenses : le même arbre, le dépensé du mois à chaque branche, le reste et la barre sur les
 * enveloppes seules. Additionner des restes de périodes différentes donnerait un chiffre trompeur.
 */
@Composable
internal fun SpendingTree(
    lines: List<EnvelopeLine>,
    monthSpent: Map<Long, Money>,
    groups: List<GroupNode>,
    onOpen: (EnvelopeLine) -> Unit,
) {
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    var closed by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val filter = SpendFilter.entries[filterIndex]
    val rows = remember(lines, monthSpent, groups, filter, closed) {
        spendingRows(lines, monthSpent, groups, filter, (lines.map { it.envelope.id } + groups.map { it.id }).toSet() - closed.toSet())
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedToggle(SpendFilter.entries.map { it.label }, filterIndex) { filterIndex = it }
        GlassCard(contentPadding = 16.dp, verticalPadding = 4.dp) {
            if (rows.isEmpty()) {
                Text("Aucune enveloppe de ce type.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
            }
            rows.forEachIndexed { index, row ->
                key(row.id) {
                    if (index > 0) RowDivider(0.dp)
                    val toggle = { closed = if (row.id in closed) closed - row.id else closed + row.id }
                    val line = row.line
                    if (line != null && !row.hasChildren && line.envelope.type != EnvelopeType.SAVINGS) {
                        Box(Modifier.padding(start = 16.dp * row.depth)) {
                            EnvelopeRow(line, onClick = { onOpen(line) }, accent = categoryColor(row.rootName))
                        }
                    } else {
                        SpendingBranchRow(
                            row = row,
                            open = row.id !in closed,
                            onToggle = toggle,
                            onClick = if (line != null) ({ onOpen(line) }) else toggle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingBranchRow(row: SpendingRow, open: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp * row.depth, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronSlot(row.hasChildren, open, onToggle)
        Dot(row.rootName)
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val share = row.line?.takeIf { it.envelope.type == EnvelopeType.SAVINGS }?.monthlyShare
        Column(horizontalAlignment = Alignment.End) {
            Text((share ?: row.spent).summary(), style = MaterialTheme.typography.titleMedium)
            Text(
                if (share != null) "mis de côté" else "dépensé",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}
