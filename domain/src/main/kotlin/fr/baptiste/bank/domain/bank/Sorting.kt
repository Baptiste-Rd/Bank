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
    /** Jour de l'opération (du paiement, du retrait), celui que l'utilisateur reconnaît. */
    val date: LocalDate,
    /** Négatif pour une sortie d'argent. */
    val amountCents: Long,
    val rawLabel: String,
    /**
     * Jour de comptabilisation par la banque, souvent 1 à 3 jours après [date]. Il sert à la clé de
     * dédoublonnage, qui doit rester celle des imports déjà faits.
     */
    val bookingDate: LocalDate = date,
)

private val labelDayMonth = listOf(
    // « CB 2510 », « PAIEMENT CB 2510 », « RETRAIT DAB 2510 »
    Regex("""\b(?:CB|PSC|CARTE|DAB|RETRAIT)\s+(\d{2})(\d{2})\b""", RegexOption.IGNORE_CASE),
    // « RETRAIT DAB 25/10 », « CB 25/10 », « DU 25/10 »
    Regex("""\b(\d{2})/(\d{2})(?:/\d{2,4})?\b"""),
)

/**
 * Jour de l'opération : celui donné par la banque ([transactionDate]), sinon celui écrit dans le
 * libellé (« CB 2510 » = le 25 octobre), à condition qu'il tombe dans les 10 jours avant la
 * comptabilisation ; sinon la date de comptabilisation.
 */
fun operationDate(rawLabel: String, bookingDate: LocalDate, transactionDate: LocalDate? = null): LocalDate {
    if (transactionDate != null && !transactionDate.isAfter(bookingDate)) return transactionDate
    for (pattern in labelDayMonth) {
        val match = pattern.find(rawLabel) ?: continue
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        if (month !in 1..12 || day !in 1..31) continue
        val candidate = listOf(bookingDate.year, bookingDate.year - 1)
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .firstOrNull { !it.isAfter(bookingDate) } ?: continue
        if (ChronoUnit.DAYS.between(candidate, bookingDate) <= 10) return candidate
    }
    return bookingDate
}

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
            val base = "eb:$accountUid:${operation.bookingDate}|${operation.amountCents}|${normalizeLabel(operation.rawLabel)}"
            val index = seen.merge(base, 1, Int::plus)!!
            if (index == 1) base else "$base#$index"
        }
    }
}
