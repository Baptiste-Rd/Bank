package fr.baptiste.bank.domain.bank

import fr.baptiste.bank.domain.money.Money
import java.time.LocalDate

/*
 * Plan de corrections rendu par le skill Claude : des actions que l'app propose une par une, et
 * que l'utilisateur coche avant « Appliquer ». Rien n'est appliqué sans lui. Les opérations et les
 * enveloppes sont désignées par les identifiants opaques de l'export (« op_… », « env_… ») ; une
 * enveloppe à créer, par son nom.
 */

data class ClaudePlan(val exportId: String?, val actions: List<PlanAction>)

sealed interface PlanAction {
    val reason: String?

    /** « haute », « moyenne » ou « basse » ; une confiance basse est décochée d'office. */
    val confidence: String?

    val checkedByDefault: Boolean get() = !confidence.orEmpty().trim().startsWith("bas", ignoreCase = true)

    /** Ranger une opération : dans une enveloppe (identifiant ou nom), hors budget, ou vers un compte. */
    data class Move(
        val operation: String,
        val envelope: String,
        val name: String? = null,
        val keyword: String? = null,
        override val reason: String? = null,
        override val confidence: String? = null,
    ) : PlanAction

    /** Répartir une opération entre plusieurs enveloppes : [parts] (enveloppe, montant positif), somme = total. */
    data class Split(
        val operation: String,
        val parts: List<Pair<String, Money>>,
        override val reason: String? = null,
        override val confidence: String? = null,
    ) : PlanAction

    /** Ranger désormais tout libellé qui contient [pattern] dans [envelope]. */
    data class Rule(
        val pattern: String,
        val envelope: String,
        override val reason: String? = null,
        override val confidence: String? = null,
    ) : PlanAction

    /** Afficher [name] à la place du libellé de la banque pour tout libellé qui contient [pattern]. */
    data class Rename(
        val pattern: String,
        val name: String,
        override val reason: String? = null,
        override val confidence: String? = null,
    ) : PlanAction

    /** Créer un budget. [family] : quotidien, charge, rare, épargne. [every]/[unit] : rythme (null : chaque mois). */
    data class CreateEnvelope(
        val name: String,
        val family: String,
        val amount: Money,
        val category: String? = null,
        val every: Int? = null,
        val unit: String? = null,
        val reset: LocalDate? = null,
        val repeats: Boolean = true,
        override val reason: String? = null,
        override val confidence: String? = null,
    ) : PlanAction

    /** À vérifier soi-même (doublon, date, montant) : rien n'est appliqué. */
    data class Flag(
        val operation: String?,
        val issue: String,
        val note: String? = null,
        override val reason: String? = null,
        override val confidence: String? = null,
    ) : PlanAction {
        override val checkedByDefault: Boolean get() = false
    }
}

/** Le mot qui désigne « hors budget » dans un plan. */
fun isOutOfBudget(target: String): Boolean = envelopeNameKey(target) in setOf("HORS BUDGET", "HORS-BUDGET", "OUT_OF_BUDGET")
