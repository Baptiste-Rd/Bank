package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money

/**
 * Bilan d'un mois terminé : l'épargne du mois et ce qu'elle est devenue. L'épargne du mois est ce
 * que le mois n'a pas dépensé ; elle n'est « virée » que si elle a quitté le compte courant pour un
 * livret ou un placement. Le reste est encore sur le compte courant, à virer.
 *
 * @property transferred viré vers les comptes d'épargne pendant le mois (null si l'app ne le sait pas).
 * @property onAccount part encore sur le compte courant (négative si l'on a viré plus que l'épargne du
 *   mois : la différence a été prise sur l'argent déjà là). Null si [transferred] l'est.
 */
data class MonthClosing(
    val savings: Money,
    val transferred: Money?,
    val onAccount: Money?,
) {
    /** Ce qu'il reste à virer pour mettre l'épargne du mois à l'abri (rien si tout est viré). */
    val toTransfer: Money get() = onAccount?.takeIf { it.isPositive && savings.isPositive }?.let { minOf(it, savings) } ?: Money.ZERO
}

fun monthClosing(savings: Money, transferred: Money?): MonthClosing =
    MonthClosing(savings, transferred, transferred?.let { savings - it })
