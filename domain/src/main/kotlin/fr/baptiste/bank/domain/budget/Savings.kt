package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money

/**
 * Épargne d'un mois : ce qui était visé, et ce qui sera réellement mis de côté à la fin du mois.
 *
 * Le calcul suit l'argent :
 * - [target] : l'objectif, la part mensuelle des enveloppes d'épargne ;
 * - [transferred] : ce qui a été déplacé vers l'épargne ce mois-ci (moins ce qui en est sorti) ;
 * - [dailyMargin] : ce qu'il restera du budget du quotidien à la fin du mois, au rythme de dépense
 *   actuel. Positif, il s'ajoute à l'épargne ; négatif (le rythme dépasse le budget), il la
 *   grignote. Pour un mois terminé, c'est le reste réel ;
 * - [overruns] : les dépassements des autres enveloppes (charges, achats prévus...) et les
 *   dépenses imputées sur l'épargne elle-même, qu'il faut bien prendre quelque part ;
 * - [surpluses] : ce qui reste dans les autres enveloppes (charges, provisions, achats prévus)
 *   à la fin de leur période. Il va à l'épargne, le mois où la période se termine : une charge
 *   mensuelle à la fin du mois, une provision annuelle à la fin de l'année. Tant que la période
 *   court, rien n'est compté : le reste peut encore être dépensé.
 *
 * Déplacer de l'argent vers l'épargne augmente [secured] (la part déjà à l'abri) mais pas
 * [forecast] tant que le rythme de dépense ne change pas : l'argent déplacé manque au budget
 * du quotidien. Seul dépenser moins fait monter la prévision.
 */
data class SavingsOutlook(
    val target: Money,
    val transferred: Money,
    val dailyMargin: Money,
    val overruns: Money,
    /** Vrai pour un mois terminé : les chiffres sont réels, plus des prévisions. */
    val isFinal: Boolean,
    val surpluses: Money = Money.ZERO,
) {
    /** Épargne déjà à l'abri : l'objectif et les transferts vers l'épargne. */
    val secured: Money get() = target + transferred

    /** Épargne prévue en fin de mois (ou réelle, pour un mois terminé). */
    val forecast: Money get() = secured + dailyMargin + surpluses - overruns

    /** Écart à l'objectif : négatif si la prévision est en dessous. */
    val gapToTarget: Money get() = forecast - target
}

/**
 * Dépense maximale par jour, d'aujourd'hui à la fin du mois, pour que l'épargne atteigne son
 * objectif. Null si le mois est terminé ; zéro ou négatif si l'objectif n'est plus atteignable.
 *
 * Il faut : à l'abri + budget du quotidien − dépense du quotidien du mois − dépassements ≥ objectif.
 * La dépense encore permise est donc à l'abri − objectif + budget − déjà dépensé − dépassements,
 * répartie sur les jours restants (aujourd'hui compris).
 */
fun Dashboard.dailyCapForTarget(): Money? {
    if (savings.isFinal || daysRemaining <= 0) return null
    val room = savings.secured - savings.target + dailyBudget - dailySpent - savings.overruns
    return room.divideFloor(daysRemaining.toLong())
}

/**
 * Enveloppes dont la période se termine ce jour-là (le dernier jour d'un mois terminé) : leur
 * reste part à l'épargne, leur dépassement y est pris. Les enveloppes du quotidien et d'épargne
 * n'en font pas partie : elles comptent déjà dans le reste du quotidien et dans l'objectif.
 */
fun Dashboard.closings(): List<EnvelopeLine> = envelopes
    .filter { it.envelope.type != EnvelopeType.DAILY && it.envelope.type != EnvelopeType.SAVINGS }
    .filter { it.period.endInclusive == today && it.remaining.cents != 0L }
