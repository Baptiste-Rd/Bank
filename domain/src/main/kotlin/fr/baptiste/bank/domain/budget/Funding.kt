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
 *
 * Provision en retard ([Envelope.catchUpFrom] dans la période) : les mois d'avant gardent leur
 * part ; à partir de ce mois, ce qui manque (le montant moins ce qui a été mis de côté depuis
 * [since], le début du suivi) se répartit sur les mois restants.
 */
fun Envelope.monthShare(month: YearMonth, period: Period, allocated: Money, since: YearMonth?): Money {
    // Une somme mise à part ne prend rien au salaire, sauf si elle a été prise sur le mois de [fundingStart].
    if (type == EnvelopeType.LUMP) return if (month == fundingStart) allocated else Money.ZERO
    val catchUp = catchUpFrom?.takeIf { type.isProvision && it > period.firstMonth() && it <= period.lastMonth() }
        ?: return baseShare(month, period, allocated)
    if (month < catchUp) return baseShare(month, period, allocated)
    if (month > period.lastMonth()) return Money.ZERO
    val before = fundedBefore(catchUp, period, allocated, since)
    val missing = (allocated - before).let { if (it.isPositive) it else Money.ZERO }
    return missing.divideCeil(monthsBetween(catchUp, period.lastMonth()))
}

/** Mis de côté avec la part habituelle, du premier mois suivi de la période jusqu'au mois avant [month]. */
private fun Envelope.fundedBefore(month: YearMonth, period: Period, allocated: Money, since: YearMonth?): Money {
    var current = listOfNotNull(period.firstMonth(), since, fundingStart?.takeIf { type.isProvision }).max()
    var total = Money.ZERO
    while (current < month) {
        total += baseShare(current, period, allocated)
        current = current.plusMonths(1)
    }
    return total
}

/** Part de [month] sans rattrapage d'une provision en retard. */
private fun Envelope.baseShare(month: YearMonth, period: Period, allocated: Money): Money {
    // Période aux bornes choisies (« 26 € jusqu'au 1er janvier ») : le montant se répartit sur ses mois.
    if (period is Period.Fixed && type.isProvision) {
        val from = listOfNotNull(period.firstMonth(), fundingStart).max()
        if (month < from || month > period.lastMonth()) return Money.ZERO
        return allocated.divideCeil(monthsBetween(from, period.lastMonth()))
    }
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
fun Envelope.fundedThrough(month: YearMonth, period: Period, allocated: Money, since: YearMonth?): Money {
    val first = listOfNotNull(period.firstMonth(), since, fundingStart?.takeIf { type.isProvision }).max()
    val last = minOf(month, period.lastMonth())
    if (last < first) return Money.ZERO
    var total = Money.ZERO
    var current = first
    while (current <= last) {
        total += monthShare(current, period, allocated, since)
        current = current.plusMonths(1)
    }
    return minOf(total, allocated)
}

/** Rattrapage proposé à une provision en retard : ce qui manque, réparti à partir de ce mois. */
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
    // Déjà rattrapée sur cette période : plus de proposition.
    if (catchUpFrom != null && catchUpFrom > period.firstMonth() && catchUpFrom <= period.lastMonth()) return null
    if (since <= period.firstMonth() || since > month || period.lastMonth() <= period.firstMonth() || month > period.lastMonth()) return null
    // Ce qui manque, réparti sur les mois restants (celui-ci compris) ; les mois passés gardent leur part.
    val funded = fundedBefore(month, period, allocated, since)
    val missing = allocated - funded
    if (!missing.isPositive) return null
    val months = monthsBetween(month, period.lastMonth())
    val share = missing.divideCeil(months)
    val current = baseShare(month, period, allocated)
    if (share <= current) return null
    return CatchUpProposal(month, months.toInt(), share, current)
}
