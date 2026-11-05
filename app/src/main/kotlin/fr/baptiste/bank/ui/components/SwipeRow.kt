package fr.baptiste.bank.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Un bouton qui apparaît en glissant une ligne vers la gauche. */
data class SwipeAction(val label: String, val color: Color, val onClick: () -> Unit)

/** Couleurs des actions : neutre (Modifier, Renommer) et bleu (Déplacer). */
object SwipeColors {
    val Neutral = Color(0xFF3A4A6B)
    val Accent = Color(0xFF2563EB)
}

/** La ligne ouverte de l'écran : une seule à la fois, comme dans Mail sur iPhone. */
val LocalOpenSwipeRow = compositionLocalOf<MutableState<Any?>> { mutableStateOf(null) }

private val ActionWidth = 84.dp

/**
 * Une ligne qui se glisse vers la gauche pour faire apparaître [actions] (glisser un peu plus loin
 * que la moitié les laisse ouvertes, sinon la ligne revient). Toucher une action la lance et
 * referme la ligne ; ouvrir une autre ligne referme celle-ci. Le simple toucher de la ligne garde
 * son effet. Les mêmes actions sont proposées au lecteur d'écran.
 */
@Composable
fun SwipeRow(key: Any, actions: List<SwipeAction>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (actions.isEmpty()) {
        Box(modifier) { content() }
        return
    }
    val open = LocalOpenSwipeRow.current
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val maxReveal = with(LocalDensity.current) { (ActionWidth * actions.size).toPx() }
    // Une autre ligne s'ouvre : celle-ci se referme.
    LaunchedEffect(open.value) { if (open.value != key && offset.value != 0f) offset.animateTo(0f) }
    fun close() = scope.launch { offset.animateTo(0f); if (open.value == key) open.value = null }

    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .semantics {
                customActions = actions.map { action -> CustomAccessibilityAction(action.label) { action.onClick(); true } }
            },
    ) {
        // Les boutons occupent seulement la place découverte, à droite.
        val revealed = -offset.value
        if (revealed > 0f) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Row(
                Modifier
                    .width(with(LocalDensity.current) { revealed.toDp() })
                    .fillMaxHeight(),
            ) {
                actions.forEach { action ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(action.color)
                            .clickable {
                                close()
                                action.onClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            action.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(key, maxReveal) {
                    detectHorizontalDragGestures(
                        onDragStart = { open.value = key },
                        onDragEnd = {
                            scope.launch {
                                if (-offset.value > maxReveal / 2) offset.animateTo(-maxReveal) else close()
                            }
                        },
                        onDragCancel = { close() },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + amount).coerceIn(-maxReveal, 0f)) }
                    }
                },
        ) { content() }
    }
}
