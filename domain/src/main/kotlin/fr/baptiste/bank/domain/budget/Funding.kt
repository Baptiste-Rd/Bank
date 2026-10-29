package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Period
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/*
 * Provisions honnêtes : ce qu'une provision met de côté chaque mois, et ce qu'elle a déjà mis de côté.
 *
 * Sans rattrapage, une provision prend sa part habituelle : 1 200 € par an font 100 € par mois.
 * Créée en cours de période, elle n'aurait pas réuni la somme à temps (Noël à 300 € créé en
 * octobre : 75 € en décembre). Avec un début de mise de côté ([Envelope.fundingStart]), le montant
 * se répartit sur les mois qui restent : 100 € en octobre, novembre et décembre ; rien avant.
 */

/** Types qui mettent de côté sur plusieurs mois. */
val EnvelopeType.isProvision: Boolean
    get() = this == EnvelopeType.ANNUAL || this == EnvelopeType.RECURRING || this == EnvelopeType.PLANNED

private fun Period.firstMonth(): YearMonth = YearMonth.from(start)
private fun Period.lastMonth(): YearMonth = YearMonth.from(endInclusive)

/** Nombre de mois de [from] à [to] compris. */
private fun monthsBetween(from: YearMonth, to: YearMonth): Long = ChronoUnit.MONTHS.between(from, to) + 1

/**
 * Part de [month] pour une enveloppe dont la période en cours est [period] et le montant [allocated].
 * Rattrapage seulement si la mise de côté commence après le premier mois de la période.
 */
fun Envelope.monthShare(month: YearMonth, period: Period, allocated: Money): Money {
    val usual = allocated.convert(frequency, Frequency.MONTHLY)
    val start = fundingStart
    if (start == null || !type.isProvision || start <= period.firstMonth()) return usual
    if (month < start) return Money.ZERO
    val months = monthsBetween(start, period.lastMonth())
    if (months < 1) return usual
    return allocated.divideCeil(months)
}

/**
 * Ce qui est mis de côté sur la période à la fin de [month] (mois compris), sans dépasser
 * [allocated]. Les mois avant [since] (début du suivi) ne comptent pas : rien n'y a été mis de côté.
 */
fun Envelope.fundedThrough(month: YearMonth, period: Period, allocated: Money, since: YearMonth? = null): Money {
    val first = listOfNotNull(period.firstMonth(), since, fundingStart?.takeIf { type.isProvision }).max()
    val last = minOf(month, period.lastMonth())
    if (last < first) return Money.ZERO
    var total = Money.ZERO
    var current = first
    while (current <= last) {
        total += monthShare(current, period, allocated)
        current = current.plusMonths(1)
    }
    return minOf(total, allocated)
}

/** Rattrapage proposé à une provision existante : répartir tout le montant depuis le début du suivi. */
data class CatchUpProposal(
    /** Mois à partir duquel on met de côté (le début du suivi). */
    val from: YearMonth,
    val months: Int,
    /** Nouvelle part mensuelle. */
    val monthlyShare: Money,
    /** Part mensuelle actuelle. */
    val currentShare: Money,
)

/**
 * Proposition de rattrapage pour une provision suivie depuis [since], postérieur au début de sa
 * période : avant le suivi, rien n'a été mis de côté, la part habituelle n'y suffira pas.
 */
fun Envelope.catchUpProposal(period: Period, allocated: Money, month: YearMonth, since: YearMonth?): CatchUpProposal? {
    if (!type.isProvision || type == EnvelopeType.PLANNED || fundingStart != null || since == null) return null
    if (since <= period.firstMonth() || since > month || period.lastMonth() <= period.firstMonth()) return null
    val months = monthsBetween(since, period.lastMonth())
    val share = allocated.divideCeil(months)
    val current = allocated.convert(frequency, Frequency.MONTHLY)
    if (share <= current) return null
    return CatchUpProposal(since, months.toInt(), share, current)
}
