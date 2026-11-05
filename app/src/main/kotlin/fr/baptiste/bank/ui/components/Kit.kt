package fr.baptiste.bank.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
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
enum class StatusKind { GOOD, WARNING, OVER, GAIN }

@Composable
private fun StatusKind.color(): Color = when (this) {
    StatusKind.GOOD -> BankTheme.status.good
    StatusKind.WARNING -> BankTheme.status.warning
    StatusKind.OVER -> BankTheme.status.over
    StatusKind.GAIN -> BankTheme.status.gain
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
fun InfoSheet(
    title: String,
    lines: List<InfoLine>,
    note: String? = null,
    onDismiss: () -> Unit,
    /** Un réglage lié à ce qui est expliqué (l'option d'un mois, par exemple). */
    extra: (@Composable () -> Unit)? = null,
) {
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
                extra?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Compris") } },
    )
}

/**
 * En-tête d'une sheet de saisie, comme sur iOS : « Annuler » à gauche, le titre, l'action à droite
 * (désactivée tant que la saisie n'est pas valable).
 */
@Composable
fun SheetHeader(title: String, onCancel: () -> Unit, action: String, enabled: Boolean, onAction: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) { Text("Annuler") }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = androidx.compose.ui.Modifier.weight(1f),
        )
        TextButton(onClick = onAction, enabled = enabled) {
            Text(action, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}

/**
 * Bouton principal d'une sheet : le même bleu plein, texte blanc, que le bouton principal en verre
 * des écrans (un seul style d'action principale dans toute l'app).
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
    ) { Text(text, maxLines = 1) }
}

/**
 * Une fenêtre en sheet qui monte du bas, avec les mêmes emplacements qu'une AlertDialog : la sortie
 * à gauche, le titre au centre, l'action à droite, puis le contenu qui défile. Pour toute saisie ou
 * tout contenu un peu long ; une AlertDialog reste réservée aux confirmations.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SheetAlert(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { dismissButton?.invoke() }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleMedium,
            ) { Box(Modifier.weight(2f), contentAlignment = Alignment.Center) { title() } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { confirmButton() }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
        ) { text() }
    }
}
