package fr.baptiste.bank.domain.bank

/*
 * Ce qui aide à reconnaître une opération sans tout ranger à la main.
 *
 * 1. Les alias : « PRLV SEPA FREE MOBILE » s'affiche « Forfait mobile ». Un alias porte un motif
 *    (texte cherché dans le libellé, sans casse ni accents) ; les opérations suivantes qui le
 *    contiennent prennent le même nom. Le libellé de la banque est conservé à côté.
 * 2. La proposition d'après l'historique : une opération à trier dont le libellé ressemble à une
 *    opération déjà rangée se voit proposer la même enveloppe. Rien n'est rangé sans validation.
 * 3. Les noms d'enveloppe : deux enveloppes ne peuvent pas porter le même nom, à la casse, aux
 *    accents et aux espaces près (« Bar » et « bar » sont le même nom).
 */

/** Alias d'un libellé bancaire : le nom affiché à la place, pour ce motif et les suivants. */
data class LabelAlias(val id: Long, val pattern: String, val alias: String)

/** Alias qui s'applique à [label], ou null. Entre plusieurs motifs, le plus long (le plus précis). */
fun List<LabelAlias>.aliasFor(label: String): LabelAlias? {
    val text = normalizeLabel(label)
    return filter { normalizeLabel(it.pattern).let { pattern -> pattern.isNotEmpty() && text.contains(pattern) } }
        .maxByOrNull { normalizeLabel(it.pattern).length }
}

/** Une opération déjà rangée : son libellé bancaire et l'enveloppe choisie. */
data class SortedLabel<K>(val label: String, val envelope: K)

/**
 * Enveloppe proposée pour [label] d'après les opérations déjà rangées, ou null.
 *
 * Deux libellés se ressemblent quand ils ont le même mot-clé ([suggestedRuleKeyword] : les trois
 * premiers mots, sans les numéros). L'enveloppe la plus souvent choisie pour ce mot-clé l'emporte ;
 * à égalité, la plus récente (l'historique est donné du plus récent au plus ancien).
 */
fun <K> suggestEnvelope(label: String, history: List<SortedLabel<K>>): K? {
    val key = normalizeLabel(suggestedRuleKeyword(label))
    if (key.isEmpty()) return null
    val matches = history.filter { normalizeLabel(suggestedRuleKeyword(it.label)) == key }
    if (matches.isEmpty()) return null
    val counts = matches.groupingBy { it.envelope }.eachCount()
    val best = counts.values.max()
    return matches.first { counts[it.envelope] == best }.envelope
}

/** Forme de comparaison d'un nom d'enveloppe. */
fun envelopeNameKey(name: String): String = normalizeLabel(name)

/** Vrai si [name] est déjà pris parmi [taken] (à la casse et aux accents près). */
fun isNameTaken(name: String, taken: Collection<String>): Boolean {
    val key = envelopeNameKey(name)
    return key.isNotEmpty() && taken.any { envelopeNameKey(it) == key }
}
