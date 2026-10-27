package fr.baptiste.bank.domain.bank

import fr.baptiste.bank.domain.model.RuleMatchType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/*
 * Tri automatique des opérations importées.
 *
 * Deux mécanismes, appliqués dans cet ordre à chaque nouvelle opération bancaire :
 * 1. le rapprochement : si une dépense saisie à la main correspond (même montant, date proche),
 *    l'opération bancaire la remplace et reprend son enveloppe ; rien n'est compté deux fois ;
 * 2. les règles : « tout ce qui contient CARREFOUR va dans Courses ».
 * Ce qui reste est « à trier » et n'est compté dans aucune enveloppe tant qu'il n'est pas rangé.
 */

/** Règle de tri : un texte cherché dans le libellé nettoyé, et l'enveloppe où ranger l'opération. */
data class LabelRule(
    val id: Long,
    val matchType: RuleMatchType,
    val pattern: String,
    val envelopeId: Long,
    /** La plus petite valeur l'emporte quand plusieurs règles correspondent. */
    val priority: Int = 0,
)

/**
 * Règle qui s'applique à [label], ou null. Comparaison sans casse ni accents. Entre plusieurs
 * règles qui correspondent : la priorité la plus forte, puis le motif le plus long (le plus précis).
 */
fun List<LabelRule>.matching(label: String): LabelRule? {
    val text = normalizeLabel(label)
    return filter { rule ->
        val pattern = normalizeLabel(rule.pattern)
        pattern.isNotEmpty() && when (rule.matchType) {
            RuleMatchType.CONTAINS -> text.contains(pattern)
            RuleMatchType.STARTS_WITH -> text.startsWith(pattern)
        }
    }.minWithOrNull(compareBy<LabelRule> { it.priority }.thenByDescending { it.pattern.length })
}

/** Dépense saisie à la main, candidate au rapprochement. */
data class ManualEntry(val id: Long, val date: LocalDate, val amountCents: Long)

/**
 * Saisie manuelle qui correspond à une opération bancaire : même montant au centime, saisie
 * entre [daysBefore] jours avant et [daysAfter] jours après la date bancaire (un paiement par
 * carte est souvent comptabilisé un à trois jours après l'achat). S'il y en a plusieurs, la plus
 * proche en date, puis la plus ancienne saisie.
 */
fun findManualMatch(
    bankDate: LocalDate,
    bankAmountCents: Long,
    candidates: List<ManualEntry>,
    daysBefore: Long = 7,
    daysAfter: Long = 2,
): ManualEntry? = candidates
    .filter { it.amountCents == bankAmountCents }
    .filter { candidate ->
        val gap = ChronoUnit.DAYS.between(candidate.date, bankDate)
        gap in -daysAfter..daysBefore
    }
    .minWithOrNull(compareBy<ManualEntry> { abs(ChronoUnit.DAYS.between(it.date, bankDate)) }.thenBy { it.id })

/** Opération telle que reçue de la banque, avant enregistrement. */
data class BankOperation(
    /** Référence unique donnée par la banque, quand elle existe. */
    val reference: String?,
    val date: LocalDate,
    /** Négatif pour une sortie d'argent. */
    val amountCents: Long,
    val rawLabel: String,
)

/**
 * Clé de dédoublonnage de chaque opération : elle doit rester la même d'une synchronisation à
 * l'autre, pour qu'une opération déjà importée soit ignorée. On prend la référence de la banque ;
 * à défaut, la date, le montant et le libellé, numérotés quand plusieurs opérations identiques
 * tombent le même jour (deux cafés au même prix).
 */
fun List<BankOperation>.importKeys(accountUid: String): List<String> {
    val seen = mutableMapOf<String, Int>()
    return map { operation ->
        val reference = operation.reference?.takeIf { it.isNotBlank() }
        if (reference != null) {
            "eb:$accountUid:ref:$reference"
        } else {
            val base = "eb:$accountUid:${operation.date}|${operation.amountCents}|${normalizeLabel(operation.rawLabel)}"
            val index = seen.merge(base, 1, Int::plus)!!
            if (index == 1) base else "$base#$index"
        }
    }
}
