package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth

/*
 * Ce qui est de côté sur les autres comptes : livrets, compte d'investissement, saisis à la main
 * ou reliés à la banque. Un compte n'a pas d'opérations ici, seulement des relevés de solde datés ;
 * le solde à une date est le dernier relevé connu à cette date.
 */

/**
 * Un compte d'épargne ou d'investissement. [linked] : relié à la banque, mis à jour seul.
 * [invested] et [taxWrapper] (placements) : montant versé et enveloppe fiscale, pour le net estimé.
 */
data class HoldingAccount(
    val id: Long,
    val name: String,
    val type: AccountType,
    val linked: Boolean,
    val invested: Money? = null,
    val taxWrapper: TaxWrapper? = null,
)

/** Relevé de solde d'un compte à une date. */
data class BalanceSnapshot(val accountId: Long, val date: LocalDate, val balance: Money)

/** Un compte et son dernier solde connu (null : jamais renseigné). */
data class HoldingLine(val account: HoldingAccount, val balance: Money?, val asOf: LocalDate?) {
    /** Net estimé si tout était retiré aujourd'hui (placements renseignés), sinon null. */
    val estimate: NetEstimate? get() = balance?.let { netEstimate(account.taxWrapper, it, account.invested) }
}

/** Total de côté à la fin d'un mois. */
data class HoldingsMonth(val month: YearMonth, val total: Money)

data class Holdings(
    val lines: List<HoldingLine>,
    val total: Money,
    /** Fin de chaque mois, du plus ancien au plus récent (le mois en cours : à aujourd'hui). */
    val history: List<HoldingsMonth>,
    /** Versé sur ces comptes ce mois-ci depuis le compte courant (virements reconnus). */
    val transferredThisMonth: Money,
) {
    /** Total après l'impôt estimé des placements renseignés (les autres comptent pour leur solde). */
    val netTotal: Money get() = lines.fold(Money.ZERO) { sum, line -> sum + (line.estimate?.net ?: line.balance ?: Money.ZERO) }

    /** Impôt estimé sur l'ensemble des placements renseignés. */
    val estimatedTax: Money get() = lines.fold(Money.ZERO) { sum, line -> sum + (line.estimate?.tax ?: Money.ZERO) }
}

/** Solde de [accountId] le jour [date] : le dernier relevé connu à cette date, ou null. */
fun List<BalanceSnapshot>.balanceOn(accountId: Long, date: LocalDate): Money? =
    filter { it.accountId == accountId && !it.date.isAfter(date) }.maxByOrNull { it.date }?.balance

/**
 * Situation des comptes d'épargne et d'investissement (les comptes courants sont exclus).
 * @param months nombre de mois d'historique, le mois en cours compris.
 * @param transfersThisMonth montants des virements du compte courant vers ces comptes ce mois-ci
 *   (négatifs côté compte courant).
 */
fun computeHoldings(
    accounts: List<HoldingAccount>,
    snapshots: List<BalanceSnapshot>,
    today: LocalDate,
    months: Int = 6,
    transfersThisMonth: List<Money> = emptyList(),
): Holdings {
    val held = accounts.filter { it.type != AccountType.CURRENT }
    val lines = held.map { account ->
        val last = snapshots.filter { it.accountId == account.id && !it.date.isAfter(today) }.maxByOrNull { it.date }
        HoldingLine(account, last?.balance, last?.date)
    }
    fun totalOn(date: LocalDate) = held.fold(Money.ZERO) { sum, account ->
        sum + (snapshots.balanceOn(account.id, date) ?: Money.ZERO)
    }
    val current = YearMonth.from(today)
    val history = (months - 1 downTo 0).map { back ->
        val month = current.minusMonths(back.toLong())
        val end = if (month == current) today else month.atEndOfMonth()
        HoldingsMonth(month, totalOn(end))
    }
    // On n'affiche pas les mois d'avant le premier relevé : ils ne valent pas zéro, ils sont inconnus.
    val first = snapshots.filter { snapshot -> held.any { it.id == snapshot.accountId } }.minOfOrNull { it.date }
    val known = if (first == null) emptyList() else history.filter { !it.month.isBefore(YearMonth.from(first)) }
    val transferred = transfersThisMonth.fold(Money.ZERO) { sum, amount -> sum + Money(kotlin.math.abs(amount.cents)) }
    return Holdings(lines, totalOn(today), known, transferred)
}
