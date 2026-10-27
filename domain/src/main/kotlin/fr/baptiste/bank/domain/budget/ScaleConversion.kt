package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Scale

/**
 * Convertit un montant d'une échelle à une autre en passant par son équivalent annuel.
 *
 * Exemples : 180 € par an = 15 € par mois ; 300 € par mois = 69,24 € par semaine.
 * L'arrondi se fait au centime supérieur : une provision légèrement surestimée
 * est préférable à une provision qui manque quelques centimes en fin d'année.
 */
fun Money.convert(from: Scale, to: Scale): Money {
    if (from == to) return this
    val perYear = this * from.periodsPerYear
    return perYear.divideCeil(to.periodsPerYear)
}
