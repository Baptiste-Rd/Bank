package fr.baptiste.bank.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.format.summary

/**
 * Terminer une somme mise à part. Ce qui reste va sur le compte (rien à faire) ou dans un objectif ;
 * un dépassement est couvert par le mois en cours ou par un objectif. [goals] : identifiant, nom.
 */
@Composable
fun FinishLumpDialog(
    name: String,
    left: Money,
    goals: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onFinish: (goalId: Long?) -> Unit,
) {
    // null : le compte (un reste) ou le mois en cours (un dépassement).
    var choice by rememberSaveable { mutableStateOf<Long?>(null) }
    val over = left.isNegative
    val amount = if (over) Money(-left.cents) else left
    AlertDialog(
        onDismissRequest = onDismiss,
        // Le chiffre d'abord ; le nom de la somme est sur la fiche d'où l'on vient.
        title = {
            Text(
                when {
                    over -> "${amount.summary()} en trop"
                    left.isPositive -> "Il reste ${amount.summary()}"
                    else -> "Tout est dépensé"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        over -> "Qui les couvre ?"
                        left.isPositive -> "Où vont-ils ?"
                        else -> "$name sera terminée."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (left != Money.ZERO) {
                    Choice(if (over) "À répartir ce mois-ci" else "Disponible sur le compte", choice == null) { choice = null }
                    goals.forEach { (id, goal) -> Choice(if (over) "Pris sur $goal" else "Dans $goal", choice == id) { choice = id } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onFinish(choice) }) { Text("Terminer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
