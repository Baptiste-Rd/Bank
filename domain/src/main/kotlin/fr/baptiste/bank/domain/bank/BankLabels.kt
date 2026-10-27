package fr.baptiste.bank.domain.bank

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer

/*
 * Libellés et montants tels que la banque les transmet.
 *
 * Un libellé bancaire brut ressemble à « PAIEMENT CB 2410 PARIS CARREFOUR CITY CARTE 4970XXXXXXXX1234 ».
 * Le nettoyage retire ce qui ne sert pas à reconnaître la dépense (type d'opération, date courte,
 * numéro de carte masqué) et rend le reste lisible : « Paris Carrefour City ».
 */

/** Préfixes techniques des libellés, retirés au nettoyage. L'ordre compte : du plus précis au plus court. */
private val technicalPrefixes = listOf(
    Regex("""^(PAIEMENT|PAIEMT|PAIE)\s+(PAR\s+)?(CB|PSC|CARTE)\s+(\d{4,6}\s+)?"""),
    Regex("""^(PRLV|PRELEVEMENT|PRELEVMT)\s+(SEPA\s+)?(EUROPEEN\s+)?"""),
    Regex("""^(VIR|VIREMENT)\s+(SEPA\s+)?(INST(ANTANE)?\s+)?(RECU\s+|EMIS\s+)?(DE\s+|A\s+)?"""),
    Regex("""^(CB|PSC)\s+(\d{4,6}\s+)?"""),
)

private val technicalLeftovers = setOf("SEPA", "CB", "PSC", "INST", "EUROPEEN")

/** Numéro de carte masqué (« CARTE 4970XXXXXXXX1234 », « 4970XXXXXXXX1234 ») et références longues. */
private val noise = listOf(
    Regex("""\bCARTE\s+\d{4,}X+\d+\b"""),
    Regex("""\b\d{4,}X{3,}\d{2,}\b"""),
    Regex("""\bX{3,}\d{2,}\b"""),
)

/**
 * Libellé lisible à partir du libellé brut : préfixes techniques et numéro de carte retirés,
 * espaces normalisés, chaque mot en minuscules avec sa première lettre en capitale.
 * Les retraits gardent leur nature (« Retrait Paris ») ; un libellé vide après nettoyage
 * retombe sur le brut.
 */
fun cleanBankLabel(raw: String): String {
    var text = raw.uppercase().replace(Regex("""\s+"""), " ").trim()
    noise.forEach { text = it.replace(text, " ") }
    text = text.replace(Regex("""\s+"""), " ").trim()
    val withdrawal = Regex("""^RETRAIT\s+(DAB\s+)?(\d{4,6}\s+)?""")
    text = when {
        withdrawal.containsMatchIn(text) -> "RETRAIT " + withdrawal.replace(text, "")
        else -> technicalPrefixes.fold(text) { current, prefix -> prefix.replace(current, "") }
    }
    text = text.trim()
    // Un reste purement technique (« SEPA » seul) ne décrit rien : on garde alors le brut.
    if (text.isEmpty() || text in technicalLeftovers) text = raw.trim()
    return text.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.titlecase() }
    }
}

/**
 * Forme de comparaison d'un libellé : majuscules, sans accents, espaces simples.
 * « Café  de la Gare » et « CAFE DE LA GARE » deviennent identiques.
 */
fun normalizeLabel(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
        .uppercase()
        .replace(Regex("""\s+"""), " ")
        .trim()

/**
 * Mot-clé proposé pour une règle de tri : les trois premiers mots du libellé nettoyé, sans
 * chiffres isolés. « Paris Carrefour City 75011 » donne « Paris Carrefour City ».
 */
fun suggestedRuleKeyword(cleanLabel: String): String =
    cleanLabel.split(' ')
        .filter { word -> word.isNotBlank() && !word.all { it.isDigit() } }
        .take(3)
        .joinToString(" ")

/**
 * Montant décimal transmis par la banque (« 12.3 », « 1234.56 ») en centimes, arrondi au
 * centime le plus proche. Null si la chaîne n'est pas un nombre.
 */
fun decimalToCents(amount: String): Long? = runCatching {
    BigDecimal(amount.trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()
