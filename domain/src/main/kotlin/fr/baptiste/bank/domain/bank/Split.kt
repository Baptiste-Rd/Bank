package fr.baptiste.bank.domain.bank

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney

/*
 * Répartir une opération entre plusieurs enveloppes : un ticket de magasin de bricolage (23 € de
 * vélo dans Loisirs, 10 € de ruban dans Maison), des courses avec une partie soirée. La banque ne
 * voit qu'un montant ; chaque part compte dans son enveloppe, et la somme des parts est le total.
 */

/** Une répartition valable : au moins deux parts, toutes positives, dont la somme fait [total]. */
fun isValidSplit(total: Money, parts: List<Money>): Boolean =
    parts.size >= 2 && parts.all { it.isPositive } && parts.sumOfMoney { it } == total

/** La première part prend ce que les autres laissent : [total] moins leur somme (négatif si elles dépassent). */
fun splitRemainder(total: Money, others: List<Money>): Money = total - others.sumOfMoney { it }

/**
 * [count] parts égales de [total] ; les centimes qui ne se partagent pas vont aux dernières parts
 * (33 € en 3 : 11 €, 11 €, 11 € ; 10 € en 3 : 3,33 €, 3,33 €, 3,34 €).
 */
fun equalParts(total: Money, count: Int): List<Money> {
    require(count > 0) { "Il faut au moins une part" }
    val base = total.cents / count
    val left = (total.cents - base * count).toInt()
    return (0 until count).map { index -> Money(base + if (index >= count - left) 1 else 0) }
}

/**
 * Montants de parts saisies en pourcentages (en centièmes de pour cent : 3350 pour 33,5 %). Chaque
 * part est arrondie au centime ; quand les pourcentages font 100 %, la dernière prend les centimes
 * d'arrondi, et la somme fait exactement [total].
 */
fun percentParts(total: Money, basisPoints: List<Long>): List<Money> {
    val amounts = basisPoints.map { Money(Math.round(total.cents * it / 10_000.0)) }
    if (basisPoints.sum() != 10_000L || amounts.isEmpty()) return amounts
    return amounts.dropLast(1) + (total - amounts.dropLast(1).sumOfMoney { it })
}
