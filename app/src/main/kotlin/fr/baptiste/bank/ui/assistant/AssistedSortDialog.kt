package fr.baptiste.bank.ui.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import fr.baptiste.bank.data.AssistedSortChoice
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.parseAssistantReply
import fr.baptiste.bank.domain.bank.AssistantEnvelope
import fr.baptiste.bank.domain.bank.AssistantOperation
import fr.baptiste.bank.domain.bank.AssistantProposal
import fr.baptiste.bank.domain.bank.ProposalTarget
import fr.baptiste.bank.domain.bank.anonymizeLabel
import fr.baptiste.bank.domain.bank.sortingPrompt
import fr.baptiste.bank.domain.bank.target
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.ui.components.RowDivider
import fr.baptiste.bank.ui.envelopes.EnvelopeFamily
import fr.baptiste.bank.ui.envelopes.family
import fr.baptiste.bank.ui.format.formatted

/*
 * Tri assisté par Claude, en trois temps sur un même écran :
 * 1. Envoyer : ce qui partira est affiché tel quel (libellés anonymisés, montants, dates, noms
 *    des enveloppes) ; on peut retirer une opération avant l'envoi.
 * 2. Coller : la réponse de Claude, copiée depuis son app.
 * 3. Valider : une ligne par opération, avec l'enveloppe proposée (modifiable), le nom lisible,
 *    la confiance et la raison. Les propositions de confiance basse sont décochées d'office.
 * Rien n'est rangé avant « Ranger ».
 */

/** Au-delà, le message devient long à relire et la réponse moins fiable : on trie par lots. */
private const val MAX_BATCH = 40

private enum class Step { SEND, PASTE, REVIEW }

private fun EnvelopeFamily.assistantName(): String = when (this) {
    EnvelopeFamily.DAILY -> "quotidien"
    EnvelopeFamily.FIXED -> "charges fixes"
    EnvelopeFamily.PROVISIONS -> "provisions"
    EnvelopeFamily.SAVINGS -> "épargne"
}

@Composable
fun AssistedSortDialog(
    toSort: List<OperationRow>,
    envelopes: List<Envelope>,
    onApply: (List<AssistedSortChoice>) -> Unit,
    onDismiss: () -> Unit,
    /** Réponse déjà collée (captures d'écran de la démo) : l'écran s'ouvre sur la validation. */
    initialReply: String? = null,
) {
    val context = LocalContext.current
    val batch = remember(toSort) { toSort.take(MAX_BATCH) }
    val byRef = remember(batch) { batch.mapIndexed { index, operation -> index + 1 to operation }.toMap() }
    val excluded = remember { mutableStateMapOf<Int, Boolean>() }
    var step by rememberSaveable { mutableStateOf(if (initialReply != null) Step.REVIEW else Step.SEND) }
    var reply by rememberSaveable { mutableStateOf(initialReply.orEmpty()) }
    var replyError by remember { mutableStateOf<String?>(null) }

    val sent = byRef.filterKeys { excluded[it] != true }
    val prompt = remember(sent, envelopes) {
        sortingPrompt(
            sent.map { (ref, operation) -> AssistantOperation(ref, operation.date, operation.amountCents, operation.bankLabel) },
            envelopes.map { AssistantEnvelope(it.name, it.type.family.assistantName(), it.group) },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Fond plein : la fenêtre passe devant l'app, son texte doit rester lisible.
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1328)) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Trier avec Claude", style = MaterialTheme.typography.titleLarge)
                        Text(
                            when (step) {
                                Step.SEND -> "1 sur 3 · Ce qui sera envoyé"
                                Step.PASTE -> "2 sur 3 · La réponse de Claude"
                                Step.REVIEW -> "3 sur 3 · Valider"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
                when (step) {
                    Step.SEND -> SendStep(
                        byRef = byRef,
                        excluded = excluded,
                        total = toSort.size,
                        envelopeNames = envelopes.joinToString(", ") { it.name },
                        canSend = sent.isNotEmpty(),
                        onSend = {
                            if (!openClaudeWith(context, prompt)) replyError = "Aucune app pour l'ouvrir : le message est copié."
                            step = Step.PASTE
                        },
                        onCopy = {
                            copyToClipboard(context, prompt)
                            step = Step.PASTE
                        },
                    )
                    Step.PASTE -> PasteStep(
                        reply = reply,
                        error = replyError,
                        onReplyChange = {
                            reply = it
                            replyError = null
                        },
                        onPaste = { readClipboard(context)?.let { reply = it; replyError = null } },
                        onBack = { step = Step.SEND },
                        onRead = {
                            val proposals = parseAssistantReply(reply)?.filter { it.ref in sent.keys }
                            if (proposals.isNullOrEmpty()) {
                                replyError = "Je ne trouve pas le tableau de réponses. Copie toute la réponse de Claude, " +
                                    "bloc json compris."
                            } else {
                                step = Step.REVIEW
                            }
                        },
                    )
                    Step.REVIEW -> {
                        val refs = sent.keys
                        val proposals = remember(reply, refs) { parseAssistantReply(reply).orEmpty().filter { it.ref in refs } }
                        ReviewStep(
                            proposals = proposals,
                            byRef = byRef,
                            envelopes = envelopes,
                            onBack = { step = Step.PASTE },
                            onApply = onApply,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SendStep(
    byRef: Map<Int, OperationRow>,
    excluded: MutableMap<Int, Boolean>,
    total: Int,
    envelopeNames: String,
    canSend: Boolean,
    onSend: () -> Unit,
    onCopy: () -> Unit,
) {
    Text(
        "Claude identifie chaque libellé, propose une enveloppe, puis répond dans un format que l'app lit. " +
            "Seuls partent le libellé nettoyé, le montant, la date et le nom de tes enveloppes : jamais l'IBAN, " +
            "les numéros de carte ou de référence, ni les soldes.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (total > byRef.size) {
        Text(
            "Les ${byRef.size} plus récentes sur $total : les suivantes au prochain lot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
        items(byRef.entries.toList(), key = { it.key }) { (ref, operation) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = excluded[ref] != true, onCheckedChange = { excluded[ref] = !it })
                Column(Modifier.weight(1f)) {
                    Text(anonymizeLabel(operation.bankLabel), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        operation.date.formatted(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money(operation.amountCents).formatted(), style = MaterialTheme.typography.titleSmall)
            }
        }
        item {
            Text(
                "Enveloppes envoyées : $envelopeNames.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        OutlinedButton(onClick = onCopy, enabled = canSend, modifier = Modifier.weight(1f)) { Text("Copier") }
        Button(onClick = onSend, enabled = canSend, modifier = Modifier.weight(1f)) { Text("Ouvrir Claude") }
    }
}

@Composable
private fun ColumnScope.PasteStep(
    reply: String,
    error: String?,
    onReplyChange: (String) -> Unit,
    onPaste: () -> Unit,
    onBack: () -> Unit,
    onRead: () -> Unit,
) {
    Text(
        "Le message est copié. Dans Claude, envoie-le (colle-le s'il n'apparaît pas), puis copie toute sa réponse " +
            "et reviens ici.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    OutlinedTextField(
        value = reply,
        onValueChange = onReplyChange,
        label = { Text("Réponse de Claude") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp).heightIn(min = 160.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TextButton(onClick = onBack) { Text("Retour") }
        Box(Modifier.weight(1f))
        OutlinedButton(onClick = onPaste) { Text("Coller") }
        Button(onClick = onRead, enabled = reply.isNotBlank()) { Text("Lire") }
    }
}

@Composable
private fun ColumnScope.ReviewStep(
    proposals: List<AssistantProposal>,
    byRef: Map<Int, OperationRow>,
    envelopes: List<Envelope>,
    onBack: () -> Unit,
    onApply: (List<AssistedSortChoice>) -> Unit,
) {
    val names = envelopes.map { it.name }
    // Choix modifiables : coché ou non, et la cible (proposée, ou changée à la main).
    val accepted = remember(proposals) {
        mutableStateMapOf<Int, Boolean>().apply {
            proposals.forEach { put(it.ref, !it.confidence.orEmpty().startsWith("bas", ignoreCase = true)) }
        }
    }
    val targets = remember(proposals) {
        mutableStateMapOf<Int, ProposalTarget>().apply { proposals.forEach { put(it.ref, it.target(names)) } }
    }
    var useNames by rememberSaveable { mutableStateOf(true) }
    var createRules by rememberSaveable { mutableStateOf(true) }
    val count = proposals.count { accepted[it.ref] == true }

    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
        items(proposals, key = { it.ref }) { proposal ->
            val operation = byRef.getValue(proposal.ref)
            ProposalRow(
                proposal = proposal,
                operation = operation,
                checked = accepted[proposal.ref] == true,
                onCheckedChange = { accepted[proposal.ref] = it },
                target = targets.getValue(proposal.ref),
                proposedTarget = proposal.target(names),
                envelopeNames = names,
                onTargetChange = { targets[proposal.ref] = it },
            )
            RowDivider(0.dp)
        }
    }
    SwitchLine("Renommer avec le nom proposé", useNames) { useNames = it }
    SwitchLine("Ranger et renommer aussi les prochaines (règles)", createRules) { createRules = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TextButton(onClick = onBack) { Text("Retour") }
        Box(Modifier.weight(1f))
        Button(
            enabled = count > 0,
            onClick = {
                onApply(
                    proposals.filter { accepted[it.ref] == true }.map { proposal ->
                        val operation = byRef.getValue(proposal.ref)
                        val newName = proposal.name?.takeIf { useNames && it != operation.label }
                        AssistedSortChoice(
                            transactionId = operation.id,
                            target = targets.getValue(proposal.ref),
                            alias = newName,
                            keyword = proposal.keyword?.takeIf { createRules },
                        )
                    },
                )
            },
        ) { Text(if (count > 1) "Ranger $count opérations" else "Ranger") }
    }
}

@Composable
private fun ProposalRow(
    proposal: AssistantProposal,
    operation: OperationRow,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    target: ProposalTarget,
    proposedTarget: ProposalTarget,
    envelopeNames: List<String>,
    onTargetChange: (ProposalTarget) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 8.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    proposal.name ?: operation.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(Money(operation.amountCents).formatted(), style = MaterialTheme.typography.titleSmall)
            }
            Text(
                anonymizeLabel(operation.bankLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Box {
                AssistChip(onClick = { picking = true }, label = { Text(target.label()) })
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    if (proposedTarget is ProposalTarget.New) {
                        DropdownMenuItem(
                            text = { Text(proposedTarget.label()) },
                            onClick = { onTargetChange(proposedTarget); picking = false },
                        )
                    }
                    envelopeNames.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { onTargetChange(ProposalTarget.Existing(name)); picking = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Hors budget") },
                        onClick = { onTargetChange(ProposalTarget.OutOfBudget); picking = false },
                    )
                }
            }
            val detail = listOfNotNull(proposal.confidence?.let { "Confiance $it" }, proposal.reason).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun ProposalTarget.label(): String = when (this) {
    is ProposalTarget.Existing -> name
    is ProposalTarget.New -> "Nouvelle : $name ($family)"
    ProposalTarget.OutOfBudget -> "Hors budget"
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
