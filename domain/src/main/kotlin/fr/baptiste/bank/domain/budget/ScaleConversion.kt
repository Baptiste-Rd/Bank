package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale

/**
 * Convertit un montant d'une fréquence à une autre en passant par son équivalent annuel.
 *
 * Exemples : 180 € par an = 15 € par mois ; 300 € par mois = 69,24 € par semaine ;
 * 30 € toutes les 6 semaines = 21,67 € par mois (52 / 6 coupes par an, réparties sur 12 mois).
 * L'arrondi se fait au centime supérieur : une provision légèrement surestimée
 * est préférable à une provision qui manque quelques centimes en fin d'année.
 */
fun Money.convert(from: Frequency, to: Frequency): Money {
    if (from == to) return this
    val numerator = this * from.perYearNumeratorValue() * to.perYearDenominatorValue()
    return numerator.divideCeil(from.perYearDenominatorValue() * to.perYearNumeratorValue())
}

fun Money.convert(from: Scale, to: Scale): Money = convert(Frequency(1, from), Frequency(1, to))

private fun Frequency.perYearNumeratorValue(): Long = scale.periodsPerYear
private fun Frequency.perYearDenominatorValue(): Long = every.toLong()
