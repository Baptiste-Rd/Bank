package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney

/**
 * Le plan du mois de l'onglet Budget : comment le salaire se répartit entre les familles.
 *
 * @property charges part du mois des charges fixes.
 * @property provisions part du mois des enveloppes rares et ponctuelles (sommes mises à part comprises).
 * @property savings part du mois des enveloppes d'épargne.
 * @property daily part du salaire laissée au quotidien.
 * @property moves solde des mouvements du mois qui changent le budget du quotidien sans venir du
 *   salaire : positif quand de l'argent y est ajouté d'ailleurs, négatif quand il en sort vers une
 *   autre enveloppe. `daily + moves` est le budget du quotidien.
 */
data class MonthPlan(
    val charges: Money,
    val provisions: Money,
    val savings: Money,
    val daily: Money,
    val moves: Money,
) {
    /** Les lignes du plan, dans l'ordre de l'écran. Avec un salaire, leur somme est ce salaire (I5). */
    val lines: List<Money> get() = listOf(charges, provisions, savings, daily)
}

private val PROVISION_TYPES = setOf(EnvelopeType.RECURRING, EnvelopeType.ANNUAL, EnvelopeType.PLANNED, EnvelopeType.LUMP)

fun Dashboard.monthPlan(): MonthPlan {
    fun share(types: Set<EnvelopeType>) = envelopes.filter { it.envelope.type in types }.sumOfMoney { it.monthlyShare }
    val charges = share(setOf(EnvelopeType.FIXED))
    val provisions = share(PROVISION_TYPES)
    val savings = share(setOf(EnvelopeType.SAVINGS))
    // Avec un salaire, le quotidien a ce que les autres familles laissent (I5) ; le reste de son
    // budget vient de mouvements du mois. Sans salaire, son budget est la somme de ses enveloppes.
    val daily = income?.let { it - charges - provisions - savings } ?: dailyBudget
    return MonthPlan(charges, provisions, savings, daily, moves = dailyBudget - daily)
}
