package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.BudgetPeriod
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import java.time.LocalDate

/*
 * Le passé figé : chaque période commencée est enregistrée, avec ses bornes et son montant.
 *
 * Sans cela, un mois passé se recalculait avec les réglages du jour : baisser le budget des courses
 * en octobre faisait paraître août dépassé, retirer une enveloppe la faisait disparaître d'août.
 * L'app enregistre donc les périodes qui manquent, du début du suivi jusqu'à aujourd'hui. Une
 * enveloppe qui garde son reste ([Envelope.carryOver], un budget rare comme le coiffeur) ajoute ce
 * qui n'a pas été dépensé sur sa période précédente au montant de la suivante (à partir de la
 * période en cours : les périodes passées enregistrées après coup gardent leur montant d'alors).
 */

/**
 * Périodes de [envelope] à enregistrer entre [from] et [today] (celles qui manquent, dans l'ordre).
 * [spentByKey] : dépensé par clé de période, pour le report d'un budget qui garde son reste.
 */
fun Envelope.missingPeriods(from: LocalDate, today: LocalDate, spentByKey: Map<String, Money> = emptyMap()): List<BudgetPeriod> {
    var date = listOfNotNull(from, start, if (frequency.isCalendar) null else anchor).max()
    if (date.isAfter(today)) return emptyList()
    val added = mutableListOf<BudgetPeriod>()
    var known = this
    while (!date.isAfter(today)) {
        if (!known.existsOn(date)) break
        val period = known.periodContaining(date)
        if (known.periods.none { it.key == period.key }) {
            var amount = known.amountIn(period)
            // Le report vaut pour la période qui commence, pas pour le passé rattrapé d'un coup :
            // un mois terminé garde le budget qu'il avait.
            if (carryOver && !period.endInclusive.isBefore(today)) {
                val previous = known.periods.lastOrNull { it.endInclusive.isBefore(period.start) }
                if (previous != null) {
                    val left = previous.amount - (spentByKey[previous.key] ?: Money.ZERO)
                    if (left.isPositive) amount += left
                }
            }
            val row = BudgetPeriod(period.key, period.start, period.endInclusive, amount)
            added += row
            known = known.copy(periods = (known.periods + row).sortedBy { it.start })
        }
        date = period.endInclusive.plusDays(1)
    }
    return added
}

/**
 * Le montant change : la période en cours prend le nouveau montant, les périodes terminées gardent
 * le leur. Renvoie la période en cours mise à jour (null si elle n'est pas encore enregistrée).
 */
fun Envelope.currentPeriodWithAmount(today: LocalDate, newAmount: Money): BudgetPeriod? {
    val period = periodContaining(today)
    val stored = periods.firstOrNull { it.key == period.key } ?: return null
    // Un report déjà ajouté reste acquis : on change seulement la part du montant.
    val carried = stored.amount - amountWithoutCarry(period)
    return stored.copy(amount = newAmount + (if (carried.isPositive) carried else Money.ZERO))
}

private fun Envelope.amountWithoutCarry(period: fr.baptiste.bank.domain.period.Period): Money =
    if (firstAmount != null && firstReset != null && period.start == start) firstAmount else amount
