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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.bank.envelopeNameKey
import fr.baptiste.bank.domain.model.DEFAULT_CATEGORY
import fr.baptiste.bank.domain.model.ancestorsOf
import fr.baptiste.bank.domain.model.defaultCategories
import fr.baptiste.bank.ui.theme.categoryColor

/** Ce que l'on déplace : une enveloppe (vers une catégorie) ou une catégorie (dans une autre). */
sealed interface MoveSubject {
    val name: String
    data class EnvelopeSubject(val envelopeId: Long, override val name: String, val current: String) : MoveSubject
    data class CategorySubject(override val name: String, val currentParent: String?) : MoveSubject
}

/**
 * « Déplacer » : l'arbre des catégories, celles de départ jamais utilisées comprises, et une
 * nouvelle catégorie. Toucher une ligne range tout de suite (un message permet d'annuler). Une
 * catégorie ne peut pas aller dans elle-même ni dans ses sous-catégories : ces lignes n'y sont pas.
 *
 * @param known noms de catégories connus (enregistrés et portés par les enveloppes).
 * @param onPick la destination : un nom de catégorie, « Autres » pour une enveloppe, null pour
 *   remettre une catégorie en catégorie principale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(
    subject: MoveSubject,
    known: Collection<String>,
    parents: Map<String, String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    val isCategory = subject is MoveSubject.CategorySubject
    val rows = remember(known, parents, subject) {
        val all = (known + defaultCategories)
            .map { it.trim() }
            .filter { it.isNotEmpty() && envelopeNameKey(it) != envelopeNameKey(DEFAULT_CATEGORY) }
            .distinctBy(::envelopeNameKey)
        val selfKey = envelopeNameKey(subject.name)
        // Pour une catégorie : ni elle-même, ni ses sous-catégories.
        val allowed = if (!isCategory) all else all.filter { name ->
            envelopeNameKey(name) != selfKey && ancestorsOf(name, parents).none { envelopeNameKey(it) == selfKey }
        }
        fun parentOf(name: String) = ancestorsOf(name, parents).firstOrNull()?.let(::envelopeNameKey)
        val allowedKeys = allowed.map(::envelopeNameKey).toSet()
        fun walk(parent: String?, depth: Int): List<Pair<String, Int>> =
            allowed.filter { name -> (parentOf(name)?.takeIf { it in allowedKeys }) == parent }
                .sortedBy { it.lowercase() }
                .flatMap { listOf(it to depth) + walk(envelopeNameKey(it), depth + 1) }
        walk(null, 0)
    }
    val current = when (subject) {
        is MoveSubject.EnvelopeSubject -> subject.current
        is MoveSubject.CategorySubject -> subject.currentParent
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Fermer") }
            Text("Déplacer ${subject.name}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(72.dp))
        }
        LazyColumn(Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            if (isCategory) {
                item(key = "root") { DestinationRow("Aucune (catégorie principale)", null, 0, current == null) { onPick(null) } }
            }
            items(rows, key = { it.first }) { (name, depth) ->
                DestinationRow(name, categoryColor(name), depth, current?.let(::envelopeNameKey) == envelopeNameKey(name)) { onPick(name) }
            }
            if (!isCategory) {
                item(key = "others") {
                    DestinationRow(DEFAULT_CATEGORY, categoryColor(DEFAULT_CATEGORY), 0, envelopeNameKey(current.orEmpty()) == envelopeNameKey(DEFAULT_CATEGORY)) { onPick(DEFAULT_CATEGORY) }
                }
            }
            item(key = "new") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nouvelle catégorie") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = newName.isNotBlank(), onClick = { onPick(newName.trim()) }) { Text("Créer") }
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
