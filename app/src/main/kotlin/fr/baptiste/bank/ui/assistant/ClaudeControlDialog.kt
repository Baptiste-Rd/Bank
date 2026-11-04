package fr.baptiste.bank.ui.assistant

import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import fr.baptiste.bank.data.ClaudeBridge
import fr.baptiste.bank.domain.bank.ClaudePlan
import fr.baptiste.bank.domain.bank.PlanAction
import fr.baptiste.bank.domain.bank.isOutOfBudget
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.format.formatted
import java.io.File
import java.time.LocalDate

/*
 * Contrôle avec Claude, en deux gestes :
 * 1. Exporter : un fichier anonymisé (aucun IBAN, numéro ni solde) partagé vers l'app Claude, où le
 *    skill « bank-controle-depenses » l'analyse (audit, rangement, enveloppes, vérification d'un
 *    compte, récap, revue des habitudes).
 * 2. Importer : la réponse de Claude collée ici devient une liste d'actions à cocher. Rien n'est
 *    appliqué avant « Appliquer ».
 */

private enum class ControlStep { HOME, PASTE, REVIEW }

@Composable
fun ClaudeControlDialog(
    onExport: (onReady: (String) -> Unit) -> Unit,
    parse: (String) -> ClaudePlan?,
    loadContext: suspend () -> ClaudeBridge.Context,
    onApply: (List<PlanAction>) -> Unit,
    onDismiss: () -> Unit,
    /** Réponse déjà collée (captures de la démo) : l'écran s'ouvre sur la validation. */
    initialReply: String? = null,
    /** Opérations à trier, et le tri avec Claude (null : pas proposé). */
    unsortedCount: Int = 0,
    onAssistedSort: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(if (initialReply != null) ControlStep.REVIEW else ControlStep.HOME) }
    var reply by rememberSaveable { mutableStateOf(initialReply.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }

    // Plein écran jusque sous la barre de gestes : l'écran du dessous ne dépasse plus en bas.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1328), contentColor = Color.White) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                    Text("Claude", style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                    Box(Modifier.width(72.dp))
                }
                when (step) {
                    ControlStep.HOME -> HomeStep(
                        exporting = exporting,
                        onExport = {
                            exporting = true
                            onExport { json ->
                                exporting = false
                                shareExport(context, json)
                            }
                        },
                        onPaste = { step = ControlStep.PASTE },
                        unsortedCount = unsortedCount,
                        onAssistedSort = onAssistedSort,
                    )
                    ControlStep.PASTE -> {
                        Text(
                            "Copie toute la réponse de Claude (bloc json compris), puis colle-la ici. Il faut une fois le skill « bank-controle-depenses » dans Claude.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        OutlinedTextField(
                            value = reply,
                            onValueChange = {
                                reply = it
                                error = null
                            },
                            label = { Text("Réponse de Claude") },
                            isError = error != null,
                            supportingText = error?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp).heightIn(min = 160.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            TextButton(onClick = { step = ControlStep.HOME }) { Text("Retour") }
                            Box(Modifier.weight(1f))
                            OutlinedButton(onClick = { readClipboard(context)?.let { reply = it; error = null } }) { Text("Coller") }
                            Button(enabled = reply.isNotBlank(), onClick = {
                                val plan = parse(reply)
                                if (plan == null || plan.actions.isEmpty()) {
                                    error = "Je ne trouve pas de plan dans cette réponse. Demande à Claude « rends le plan de corrections »."
                                } else {
                                    step = ControlStep.REVIEW
                                }
                            }) { Text("Lire") }
                        }
                    }
                    ControlStep.REVIEW -> {
                        val plan = remember(reply) { parse(reply) }
                        if (plan == null) {
                            LaunchedEffect(Unit) { step = ControlStep.PASTE }
                        } else {
                            ReviewPlan(plan, loadContext, onBack = { step = ControlStep.PASTE }, onApply = onApply)
                        }
                    }
                }
            }
        }
    }
}

/**
 * L'accueil de Claude, un seul écran pour tout ce qu'il sait faire : trier ce qui attend, contrôler
 * les dépenses (export puis plan à valider), reprendre une réponse. Une ligne dit ce qui est partagé.
 */
@Composable
private fun ColumnScope.HomeStep(
    exporting: Boolean,
    onExport: () -> Unit,
    onPaste: () -> Unit,
    unsortedCount: Int,
    onAssistedSort: (() -> Unit)?,
) {
    Column(Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onAssistedSort != null && unsortedCount > 0) {
            ClaudeAction("Trier mes $unsortedCount opérations", "Une enveloppe proposée pour chacune", onAssistedSort)
        }
        ClaudeAction(
            if (exporting) "Préparation…" else "Contrôler mes dépenses",
            "Rangement, enveloppes, habitudes",
            onExport,
        )
        ClaudeAction("Coller une réponse", "Un plan à valider, rien n'est appliqué avant", onPaste)
        Text(
            "Partagé : jours, montants, libellés nettoyés et enveloppes. Jamais d'IBAN, de numéro ni de solde.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClaudeAction(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(fr.baptiste.bank.R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Step(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Les actions du plan, regroupées, à cocher ; « À vérifier » se lit seulement. */
@Composable
private fun ColumnScope.ReviewPlan(
    plan: ClaudePlan,
    loadContext: suspend () -> ClaudeBridge.Context,
    onBack: () -> Unit,
    onApply: (List<PlanAction>) -> Unit,
) {
    var known by remember { mutableStateOf<ClaudeBridge.Context?>(null) }
    LaunchedEffect(plan) { known = loadContext() }
    val checked = remember(plan) { mutableStateMapOf<Int, Boolean>().apply { plan.actions.forEachIndexed { i, a -> put(i, a.checkedByDefault) } } }
    val groups = listOf(
        "Enveloppes à créer" to plan.actions.withIndex().filter { it.value is PlanAction.CreateEnvelope },
        "Rangements" to plan.actions.withIndex().filter { it.value is PlanAction.Move },
        "Répartitions" to plan.actions.withIndex().filter { it.value is PlanAction.Split },
        "Règles de tri" to plan.actions.withIndex().filter { it.value is PlanAction.Rule },
        "Noms" to plan.actions.withIndex().filter { it.value is PlanAction.Rename },
        "À vérifier toi-même" to plan.actions.withIndex().filter { it.value is PlanAction.Flag },
    ).filter { it.second.isNotEmpty() }
    val count = plan.actions.indices.count { checked[it] == true && plan.actions[it] !is PlanAction.Flag }

    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
        groups.forEach { (title, items) ->
            item(key = "h-$title") {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            items(items, key = { it.index }) { (index, action) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (action !is PlanAction.Flag) {
                        Checkbox(checked = checked[index] == true, onCheckedChange = { checked[index] = it })
                    }
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(action.title(known), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val detail = listOfNotNull(action.detail(known), action.reason, action.confidence?.let { "confiance $it" })
                        if (detail.isNotEmpty()) {
                            Text(
                                detail.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                RowDivider(0.dp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TextButton(onClick = onBack) { Text("Retour") }
        Box(Modifier.weight(1f))
        Button(enabled = count > 0, onClick = {
            onApply(plan.actions.filterIndexed { index, action -> checked[index] == true && action !is PlanAction.Flag })
        }) { Text("Appliquer ($count)") }
    }
}

private fun envelopeLabel(ref: String, known: ClaudeBridge.Context?): String =
    if (isOutOfBudget(ref)) "hors budget" else known?.envelopeNames?.get(ref) ?: ref

private fun PlanAction.title(known: ClaudeBridge.Context?): String = when (this) {
    is PlanAction.Move -> known?.operationLabels?.get(operation) ?: "Opération introuvable ($operation)"
    is PlanAction.Split -> known?.operationLabels?.get(operation) ?: "Opération introuvable ($operation)"
    is PlanAction.Rule -> "« $pattern »"
    is PlanAction.Rename -> "« $pattern »"
    is PlanAction.CreateEnvelope -> name
    is PlanAction.Flag -> operation?.let { known?.operationLabels?.get(it) } ?: issue
}

private fun PlanAction.detail(known: ClaudeBridge.Context?): String? = when (this) {
    is PlanAction.Move -> "→ ${envelopeLabel(envelope, known)}" + (name?.let { " · nommée « $it »" } ?: "")
    is PlanAction.Split -> parts.joinToString(" · ") { (ref, amount) -> "${amount.formatted()} dans ${envelopeLabel(ref, known)}" }
    is PlanAction.Rule -> "va désormais dans ${envelopeLabel(envelope, known)}"
    is PlanAction.Rename -> "s'affiche « $name »"
    is PlanAction.CreateEnvelope -> "${amount.formatted()} · $family" + (every?.let { " · tous les $it ${unit ?: "mois"}" } ?: "") +
        (category?.let { " · $it" } ?: "")
    is PlanAction.Flag -> listOfNotNull(issue.takeIf { operation != null }, note).joinToString(" · ").ifEmpty { null }
}

/** Écrit l'export dans le cache et ouvre le partage (l'app Claude en premier choix). */
private fun shareExport(context: Context, json: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "bank-export-${LocalDate.now()}.json").apply { writeText(json) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val share = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "Contrôle mes dépenses avec le skill bank-controle-depenses.")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(share, "Envoyer à Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
