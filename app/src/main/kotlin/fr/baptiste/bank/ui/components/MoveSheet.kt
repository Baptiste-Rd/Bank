package fr.baptiste.bank.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Ce que l'on déplace : une enveloppe ou une catégorie, toutes deux des lignes de l'arbre. */
sealed interface MoveSubject {
    val name: String
    data class EnvelopeSubject(val envelopeId: Long, override val name: String, val current: String) : MoveSubject
    data class CategorySubject(override val name: String, val currentParent: String?) : MoveSubject
}

/** Une ligne de l'arbre où l'on peut ranger ; [color] : celle de sa branche du haut. */
data class MoveDestination(val id: Long, val name: String, val depth: Int, val color: androidx.compose.ui.graphics.Color)

/**
 * « Déplacer » : l'arbre de Budget (catégories et vraies enveloppes), sans ce que contient la ligne
 * déplacée ni elle-même, et une nouvelle enveloppe. Toucher une ligne range tout de suite dedans
 * (un message permet d'annuler), comme un glissement.
 *
 * @param currentParent le parent actuel, coché ; null : en haut.
 * @param onPick la destination ; null pour ranger en haut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(
    subject: MoveSubject,
    destinations: List<MoveDestination>,
    currentParent: Long?,
    onPick: (Long?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Fermer") }
            Text("Déplacer ${subject.name}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(72.dp))
        }
        LazyColumn(Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            item(key = "root") { DestinationRow("En haut", null, 0, currentParent == null) { onPick(null) } }
            items(destinations, key = { it.id }) { row ->
                DestinationRow(row.name, row.color, row.depth, row.id == currentParent) { onPick(row.id) }
            }
            item(key = "new") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nouvelle enveloppe") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = newName.isNotBlank(), onClick = { onCreate(newName.trim()) }) { Text("Créer") }
                }
            }
        }
    }
}

@Composable
private fun DestinationRow(name: String, color: androidx.compose.ui.graphics.Color?, depth: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp + 20.dp * depth, end = 24.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) Box(Modifier.padding(end = 12.dp).size(10.dp).clip(CircleShape).background(color))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // L'emplacement actuel porte une coche, comme une liste de choix d'iOS.
        if (selected) Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Renommer une catégorie : une sheet courte, Annuler et Renommer en haut. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameCategorySheet(name: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(name) { mutableStateOf(name) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetHeader(title = "Renommer", onCancel = onDismiss, action = "Renommer", enabled = text.isNotBlank() && text.trim() != name, onAction = { onRename(text.trim()) })
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Nom de la catégorie") },
            singleLine = true,
            supportingText = { Text("Un nom déjà pris réunit les deux catégories.") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 24.dp),
        )
    }
}
