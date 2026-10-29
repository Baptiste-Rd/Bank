package fr.baptiste.bank.domain.bank

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Tri assisté par Claude, sans clé d'API : l'app prépare un message, l'utilisateur l'envoie dans
 * l'app Claude, puis colle la réponse. Rien ne part sans qu'il l'ait vu.
 *
 * Ce qui part : le libellé nettoyé et anonymisé, le montant, la date, et le nom des enveloppes.
 * Ce qui ne part jamais : IBAN, numéros de carte ou de référence, nom du compte, soldes.
 *
 * Le message demande trois phases, dans l'ordre :
 * 1. analyse : identifier l'organisme derrière chaque libellé, avec une confiance ;
 * 2. proposition : une enveloppe existante, ou une nouvelle si aucune ne convient ;
 * 3. réponse : un bloc JSON que l'app sait lire. L'utilisateur valide ensuite ligne par ligne.
 */

/** Une opération telle qu'envoyée : un numéro (pas l'identifiant interne), date, montant, libellé. */
data class AssistantOperation(val ref: Int, val date: LocalDate, val amountCents: Long, val label: String)

/** Une enveloppe telle qu'envoyée : son nom, sa famille (« quotidien »...), sa catégorie éventuelle. */
data class AssistantEnvelope(val name: String, val family: String, val group: String? = null)

/** Enveloppe spéciale : l'opération ne compte dans aucune enveloppe (salaire, virement interne). */
const val OUT_OF_BUDGET = "HORS BUDGET"

/** Préfixe d'un virement vers un de mes comptes : « COMPTE: Livret A ». */
const val ACCOUNT_PREFIX = "COMPTE: "

/** Familles qu'une nouvelle enveloppe peut prendre, dans le vocabulaire de l'app. */
val assistantFamilies = listOf("quotidien", "charges fixes", "provisions", "épargne")

private val ibanLike = Regex("""\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){3,8}(?:\s?[A-Z0-9]{1,3})?\b""", RegexOption.IGNORE_CASE)
private val email = Regex("""\b[\w.+-]+@[\w-]+\.[\w.]+\b""")
private val maskedCard = Regex("""\b\d*[X*]{3,}\d*\b""", RegexOption.IGNORE_CASE)
private val longNumber = Regex("""\b[A-Z]{0,3}\d{5,}[A-Z0-9]*\b""", RegexOption.IGNORE_CASE)
private val reference = Regex("""\b(?:REF|RUM|ICS|ID|NO|N°)\s*[:.]?\s*\S+""", RegexOption.IGNORE_CASE)

/**
 * Libellé sans ce qui identifie le titulaire ou le moyen de paiement : IBAN, adresse e-mail,
 * numéro de carte masqué, références de mandat, numéros de 5 chiffres ou plus. Le nom du
 * commerçant, la ville et les mots utiles restent.
 */
fun anonymizeLabel(label: String): String =
    label
        .replace(ibanLike, " ")
        .replace(email, " ")
        .replace(reference, " ")
        .replace(maskedCard, " ")
        .replace(longNumber, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifEmpty { "(libellé masqué)" }

private val shortDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

/** Montant lisible pour le message : « −19,99 € ». */
fun assistantAmount(cents: Long): String {
    val sign = if (cents < 0) "−" else "+"
    val absolute = kotlin.math.abs(cents)
    return "$sign${absolute / 100},${(absolute % 100).toString().padStart(2, '0')} €"
}

/** Question courte sur un libellé obscur : qui est derrière, quoi, où. */
fun labelQuestion(label: String, amountCents: Long, date: LocalDate): String =
    "Libellé d'une opération sur mon compte bancaire en France : « ${anonymizeLabel(label)} » " +
        "(${assistantAmount(amountCents)}, le ${date.format(shortDate)}). " +
        "Qui est probablement derrière (organisme ou commerçant), quelle est la nature de la dépense, " +
        "et dans quelle ville si le libellé l'indique ? Réponds en trois lignes courtes : " +
        "Nom lisible / Nature / Lieu. Si tu n'es pas sûr, dis-le."

/** Message complet du tri assisté : les trois phases, mes enveloppes, les opérations, le format attendu. */
fun sortingPrompt(
    operations: List<AssistantOperation>,
    envelopes: List<AssistantEnvelope>,
    /** Mes livrets et investissements : un virement vers l'un d'eux n'est pas une dépense. */
    accounts: List<String> = emptyList(),
): String = buildString {
    appendLine("Tu m'aides à trier les opérations de mon budget personnel (compte en France). Procède en trois phases.")
    appendLine()
    appendLine(
        "Phase 1 — Analyse. Pour chaque opération, identifie l'organisme ou le commerçant derrière le libellé " +
            "bancaire (abréviations, préfixes CB, PRLV, VIR), la nature de la dépense et ta confiance " +
            "(haute, moyenne, basse). Si un libellé est ambigu, dis-le plutôt que de deviner.",
    )
    appendLine()
    appendLine(
        "Phase 2 — Proposition. Range chaque opération dans une de mes enveloppes, en reprenant son nom exact. " +
            "Si aucune ne convient vraiment, propose une nouvelle enveloppe : nom court, une famille parmi " +
            assistantFamilies.joinToString(", ") + ", et une catégorie (reprends une des miennes si elle convient, " +
            "sinon parmi " + fr.baptiste.bank.domain.model.defaultCategories.joinToString(", ") + "). Une rentrée d'argent (salaire, remboursement) ou un virement " +
            "entre mes propres comptes va dans \"$OUT_OF_BUDGET\"." +
            if (accounts.isEmpty()) "" else " Un virement vers (ou depuis) un de mes comptes d'épargne va dans \"$ACCOUNT_PREFIX<nom du compte>\".",
    )
    appendLine()
    appendLine(
        "Phase 3 — Réponse. Termine par un unique bloc ```json contenant un tableau, un objet par opération, " +
            "dans ce format :",
    )
    appendLine(
        """{"n": 1, "nom": "nom lisible", "enveloppe": "nom exact", "nouvelle": false, "famille": "quotidien", """ +
            """"categorie": "Nourriture", "motcle": "mot du libellé qui reconnaîtra les prochaines", "confiance": "haute", "raison": "une phrase"}""",
    )
    appendLine()
    appendLine("Mes enveloppes :")
    envelopes.forEach { envelope ->
        append("- ${envelope.name} (${envelope.family}")
        if (envelope.group != null) append(", catégorie ${envelope.group}")
        appendLine(")")
    }
    if (accounts.isNotEmpty()) {
        appendLine()
        appendLine("Mes comptes d'épargne : ${accounts.joinToString(", ")}.")
    }
    appendLine()
    appendLine("Opérations (n | date | montant | libellé) :")
    operations.forEach { operation ->
        appendLine(
            "${operation.ref} | ${operation.date.format(shortDate)} | ${assistantAmount(operation.amountCents)} | " +
                anonymizeLabel(operation.label),
        )
    }
}.trimEnd()

/**
 * Le tableau JSON d'une réponse de Claude, ou null : le bloc ```json s'il y en a un, sinon le texte
 * entre le premier « [ » et le dernier « ] ».
 */
fun extractJsonArray(reply: String): String? {
    val fenced = Regex("""```(?:json)?\s*(\[[\s\S]*?])\s*```""", RegexOption.IGNORE_CASE).find(reply)
    if (fenced != null) return fenced.groupValues[1].trim()
    val start = reply.indexOf('[')
    val end = reply.lastIndexOf(']')
    return if (start >= 0 && end > start) reply.substring(start, end + 1).trim() else null
}

/** Ce que Claude propose pour une opération, tel que lu dans sa réponse. */
data class AssistantProposal(
    val ref: Int,
    val name: String?,
    val envelope: String,
    val isNew: Boolean,
    val family: String?,
    val keyword: String?,
    val confidence: String?,
    val reason: String?,
    /** Catégorie proposée pour une nouvelle enveloppe. */
    val category: String? = null,
)

/** Ce que l'app fera d'une proposition, une fois rapprochée de mes enveloppes. */
sealed interface ProposalTarget {
    /** Une enveloppe existante (son nom exact chez moi). */
    data class Existing(val name: String) : ProposalTarget

    /** Une enveloppe à créer, avec sa catégorie (null : « Autres »). */
    data class New(val name: String, val family: String, val category: String? = null) : ProposalTarget

    /** Hors budget. */
    data object OutOfBudget : ProposalTarget

    /** Virement vers (ou depuis) un de mes comptes d'épargne (son nom exact chez moi). */
    data class Account(val name: String) : ProposalTarget
}

/**
 * Rapproche la proposition de mes enveloppes. Un nom qui existe déjà (à la casse et aux accents près)
 * désigne l'enveloppe existante, même si Claude la croyait nouvelle ; un nom inconnu devient une
 * nouvelle enveloppe, de famille « quotidien » si la famille proposée n'est pas reconnue.
 */
fun AssistantProposal.target(existing: Collection<String>, accounts: Collection<String> = emptyList()): ProposalTarget {
    val key = envelopeNameKey(envelope)
    if (key.isEmpty() || key == envelopeNameKey(OUT_OF_BUDGET)) return ProposalTarget.OutOfBudget
    val prefix = envelopeNameKey(ACCOUNT_PREFIX.trim())
    if (key.startsWith(prefix)) {
        val accountKey = key.removePrefix(prefix).trim()
        return accounts.firstOrNull { envelopeNameKey(it) == accountKey }?.let { ProposalTarget.Account(it) }
            ?: ProposalTarget.OutOfBudget
    }
    existing.firstOrNull { envelopeNameKey(it) == key }?.let { return ProposalTarget.Existing(it) }
    val knownFamily = assistantFamilies.firstOrNull { envelopeNameKey(it) == envelopeNameKey(family.orEmpty()) }
    val proposedCategory = category?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { fr.baptiste.bank.domain.model.storedCategory(it) }
        ?: fr.baptiste.bank.domain.model.suggestCategory(envelope)
    return ProposalTarget.New(envelope.trim(), knownFamily ?: assistantFamilies.first(), proposedCategory)
}
