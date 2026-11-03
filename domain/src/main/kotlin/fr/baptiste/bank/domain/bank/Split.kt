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
