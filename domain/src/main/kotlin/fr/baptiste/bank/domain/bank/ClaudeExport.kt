package fr.baptiste.bank.domain.bank

import fr.baptiste.bank.domain.budget.salaryKey
import java.security.MessageDigest

/*
 * Export pour le skill Claude « bank-controle-depenses » : ce qui part, et ce qui ne part jamais.
 *
 * Part : le jour, le montant, un libellé nettoyé, le nom choisi (alias), l'enveloppe, la catégorie,
 * le nom des comptes dans l'app. Ne part jamais : IBAN, numéros de carte ou de référence, longues
 * suites de chiffres (codes postaux compris), soldes, identifiants internes. Les virements entre
 * personnes perdent le nom de l'émetteur ou du destinataire : « Virement reçu · émetteur 2 »,
 * numéroté pour que Claude voie qu'il revient, sauf s'il porte un nom choisi dans l'app (« Loyer »).
 *
 * Les identifiants sont opaques : une empreinte de l'identifiant interne et d'un sel tiré au hasard
 * une fois et gardé sur le téléphone. L'app retrouve l'opération au retour du plan ; personne ne
 * peut remonter à l'identifiant interne.
 */

/** Identifiant opaque : [prefix] + 10 caractères d'empreinte de [salt] et [id]. */
fun opaqueId(prefix: String, salt: String, id: Long): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$prefix:$id".toByteArray(Charsets.UTF_8))
    return prefix + "_" + digest.take(5).joinToString("") { "%02x".format(it) }
}

private val transferLabel = Regex("""^\s*(VIR|VIREMENT|VIRT)\b""", RegexOption.IGNORE_CASE)

/** Un virement (reçu ou émis) : son libellé peut porter le nom d'une personne. */
fun isTransferLabel(rawLabel: String): Boolean = transferLabel.containsMatchIn(rawLabel)

/**
 * Mot-clé d'une règle tel qu'il part vers Claude, ou null s'il ne doit pas partir. Un mot-clé est
 * tiré d'un libellé bancaire : un virement peut y laisser le nom d'une personne, il ne part donc
 * pas ; les autres passent par le même nettoyage que les libellés (références, numéros, IBAN).
 */
fun exportRulePattern(pattern: String): String? {
    if (isTransferLabel(pattern)) return null
    val cleaned = anonymizeLabel(pattern)
    return cleaned.takeIf { it != MASKED_LABEL }
}

/**
 * Libellés d'export d'un ensemble d'opérations, dans le même ordre. [alias] : le nom choisi dans
 * l'app, gardé tel quel. Un virement sans nom choisi devient « Virement reçu · émetteur N » (ou
 * « émis · destinataire N »), N stable pour un même émetteur ; [salaryKey] le désigne « Salaire ».
 */
fun exportLabels(
    operations: List<Triple<String, String?, Long>>,
    salaryKeyValue: String? = null,
): List<String> {
    val numbers = mutableMapOf<String, Int>()
    return operations.map { (raw, alias, cents) ->
        val chosen = alias?.trim()?.takeIf { it.isNotEmpty() }
        when {
            chosen != null -> chosen
            isTransferLabel(raw) -> {
                val key = salaryKey(raw)
                if (salaryKeyValue != null && key == salaryKeyValue) {
                    "Salaire"
                } else {
                    val incoming = cents > 0
                    val number = numbers.getOrPut((if (incoming) "in:" else "out:") + key) {
                        numbers.keys.count { it.startsWith(if (incoming) "in:" else "out:") } + 1
                    }
                    if (incoming) "Virement reçu · émetteur $number" else "Virement émis · destinataire $number"
                }
            }
            else -> anonymizeLabel(raw)
        }
    }
}
