package fr.baptiste.bank.ui.operations

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.category
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.ui.glass.SortProposal
import fr.baptiste.bank.ui.theme.categoryColor
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val cardDay = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

/**
 * Le tri carte par carte, comme dans le prototype testé : une opération à la fois, sa proposition
 * en grand bouton (glisser à droite l'accepte), « Plus tard » (glisser à gauche), et dessous
 * d'autres enveloppes, « Répartir » ou « Autre » (le choix complet).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SortCardsSheet(
    operations: List<OperationRow>,
    proposal: (OperationRow) -> SortProposal?,
    /** Autres enveloppes proposées en raccourci (la proposition exclue). */
    alternatives: (OperationRow, Envelope?) -> List<Envelope>,
    onAccept: (OperationRow, SortProposal) -> Unit,
    onChoose: (OperationRow, Envelope) -> Unit,
    onSplit: (OperationRow) -> Unit,
    onOther: (OperationRow) -> Unit,
    onDismiss: () -> Unit,
) {
    // Les opérations passées (« Plus tard ») reviennent à la fin du tour.
    var skipped by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (operations.isEmpty()) {
                Text("À trier", style = MaterialTheme.typography.titleMedium)
                Text("Tout est trié", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                return@Column
            }
            val index = skipped % operations.size
            val operation = operations[index]
            val suggested = proposal(operation)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("À trier · ${index + 1} sur ${operations.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
            SwipeCard(
                key = operation.id,
                onRight = { if (suggested != null) onAccept(operation, suggested) },
                onLeft = { skipped++ },
            ) {
                Text(operation.date.format(cardDay), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(operation.label, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(Money(operation.amountCents).formatted(), style = MaterialTheme.typography.headlineMedium)
                if (suggested != null) {
                    Text(
                        "Suggéré d'après tes tris précédents",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { skipped++ }, modifier = Modifier.weight(1f)) { Text("Plus tard") }
                    if (suggested != null) {
                        Button(onClick = { onAccept(operation, suggested) }, modifier = Modifier.weight(2f)) { Text(suggested.label, maxLines = 1) }
                    }
                }
            }
            Text(if (suggested != null) "Ou" else "Ranger dans", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                alternatives(operation, suggested?.envelope).forEach { envelope ->
                    AssistChip(
                        onClick = { onChoose(operation, envelope) },
                        label = { Text(envelope.name) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(envelope.category))) },
                    )
                }
                AssistChip(onClick = { onSplit(operation) }, label = { Text("Répartir") })
                AssistChip(onClick = { onOther(operation) }, label = { Text("Autre") })
            }
        }
    }
}

/** Carte qui se glisse : à droite au-delà d'un seuil, [onRight] ; à gauche, [onLeft] ; sinon elle revient. */
@Composable
private fun SwipeCard(key: Long, onRight: () -> Unit, onLeft: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / 60f
                alpha = 1f - (abs(offset.value) / (threshold * 4)).coerceIn(0f, 0.5f)
            }
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offset.value > threshold -> onRight()
                                offset.value < -threshold -> onLeft()
                            }
                            offset.animateTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + amount) }
                }
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}
