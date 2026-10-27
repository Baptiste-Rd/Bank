package fr.baptiste.bank.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.R
import fr.baptiste.bank.ui.theme.BankTheme

/*
 * Petits composants communs aux deux styles, issus de la refonte (docs/refonte-ux.md) :
 * puce de statut, bouton d'action dans une ligne, filet de séparation, bouton ⓘ et sa feuille.
 * Ils prennent leurs couleurs dans le thème : ils marchent en verre comme en classique.
 */

/** Statut d'un élément : la couleur ne porte jamais seule le sens, un mot l'accompagne toujours. */
enum class StatusKind { GOOD, WARNING, OVER }

@Composable
private fun StatusKind.color(): Color = when (this) {
    StatusKind.GOOD -> BankTheme.status.good
    StatusKind.WARNING -> BankTheme.status.warning
    StatusKind.OVER -> BankTheme.status.over
}

/** Puce de statut : un point et un texte court dans la couleur du statut, sur un fond très atténué. */
@Composable
fun StatusChip(text: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val color = kind.color()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Bouton d'action placé dans une ligne (« Couvrir ») : niveau tonal de Material 3, entre le
 * bouton principal de l'écran et un simple lien. 40 dp de haut au moins, pour le pouce.
 */
@Composable
fun RowActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Filet entre deux lignes d'une même carte, décalé après la pastille ([inset]) comme sur iOS. */
@Composable
fun RowDivider(inset: Dp = 48.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = inset),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
}

/** Bouton ⓘ : ouvre l'explication d'un chiffre. Zone tactile de 40 dp, icône de 18. */
@Composable
fun InfoButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Une ligne du calcul dans une feuille ⓘ : libellé à gauche, montant à droite. */
data class InfoLine(val label: String, val value: String? = null)

/**
 * Feuille d'explication : un titre, quelques lignes de calcul, une phrase au plus. C'est là que
 * vit le texte retiré des cartes (divulgation progressive).
 */
@Composable
fun InfoSheet(title: String, lines: List<InfoLine>, note: String? = null, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            BlurBehindWindow()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (line.value != null) Text(line.value, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Compris") } },
    )
}
