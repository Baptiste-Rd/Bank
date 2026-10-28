package fr.baptiste.bank.domain.bank

/*
 * Quel solde montrer parmi ceux que la banque fournit (norme ISO 20022, reprise par la DSP2) :
 * - XPCD (attendu) : comptabilisé + opérations en attente connues. C'est ce que montrent la
 *   plupart des applis bancaires : on le prend quand il existe ;
 * - ITBD / CLBD (comptabilisé, en cours de journée / à la clôture) : sans les paiements par carte
 *   pas encore débités. On lui retire alors les opérations en attente, si la banque les donne ;
 * - ITAV / CLAV (disponible) : peut inclure le découvert autorisé, donc plus haut que l'argent
 *   réellement sur le compte. En dernier recours seulement.
 */

/** Solde retenu et comment il a été obtenu. */
data class ChosenBalance(
    val cents: Long,
    /** Type de solde de la banque sur lequel il repose. */
    val type: String,
    /** Opérations en attente retirées du solde comptabilisé (0 si aucune ou déjà comprises). */
    val pendingApplied: Long,
)

/**
 * Choisit le solde à montrer. [balances] : montant par type de solde ; [pendingNet] : somme
 * signée des opérations en attente (négative pour des paiements à venir). Null sans solde.
 */
fun chooseBalance(balances: Map<String, Long>, pendingNet: Long = 0): ChosenBalance? {
    balances["XPCD"]?.let { return ChosenBalance(it, "XPCD", 0) }
    listOf("ITBD", "CLBD").firstNotNullOfOrNull { type -> balances[type]?.let { type to it } }
        ?.let { (type, cents) -> return ChosenBalance(cents + pendingNet, type, pendingNet) }
    listOf("ITAV", "CLAV").firstNotNullOfOrNull { type -> balances[type]?.let { type to it } }
        ?.let { (type, cents) -> return ChosenBalance(cents, type, 0) }
    return balances.entries.firstOrNull()?.let { ChosenBalance(it.value, it.key, 0) }
}

/** Nom lisible d'un type de solde. */
fun balanceTypeLabel(type: String): String = when (type) {
    "XPCD" -> "à venir"
    "ITBD", "CLBD", "OPBD", "PRCD" -> "comptable"
    "ITAV", "CLAV", "FWAV" -> "disponible"
    else -> type
}
