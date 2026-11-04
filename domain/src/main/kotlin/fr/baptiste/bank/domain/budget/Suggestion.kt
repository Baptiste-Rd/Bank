package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import fr.baptiste.bank.domain.period.Frequency
import java.time.LocalDate
import java.time.YearMonth

/*
 * Montant proposé pour un budget : ce que l'on a vraiment dépensé, en moyenne, sur les douze
 * derniers mois terminés (ou moins : trois mois de données donnent la moyenne de ces trois mois).
 * On l'ajuste ensuite ; il ne change plus seul.
 */

data class AmountSuggestion(
    /** Montant par période de la fréquence demandée. */
    val amount: Money,
    /** Mois terminés pris en compte. */
    val months: Int,
    val monthlyAverage: Money,
)

/**
 * Proposition pour un budget de [frequency], d'après [spent] (jour, montant positif) : moyenne par
 * mois des mois terminés avant [today], depuis [since] (premier mois où l'on a des données :
 * début du suivi ou plus ancienne opération) et sur [maxMonths] au plus.
 * Null sans mois terminé ni dépense.
 */
fun suggestedAmount(
    spent: List<Pair<LocalDate, Money>>,
    frequency: Frequency,
    today: LocalDate,
    since: YearMonth? = null,
    maxMonths: Int = 12,
    /** Mois écartés des moyennes (voir excludedMonths). */
    excluded: Set<YearMonth> = emptySet(),
): AmountSuggestion? {
    val current = YearMonth.from(today)
    val months = (1..maxMonths)
        .map { current.minusMonths(it.toLong()) }
        .filter { (since == null || !it.isBefore(since)) && it !in excluded }
    if (months.isEmpty()) return null
    val total = spent.filter { YearMonth.from(it.first) in months }.sumOfMoney { it.second }
    if (!total.isPositive) return null
    val monthly = total.divideCeil(months.size.toLong())
    return AmountSuggestion(monthly.convert(Frequency.MONTHLY, frequency), months.size, monthly)
}

/**
 * Dépense habituelle par mois de chaque enveloppe quotidienne : moyenne des mois terminés (douze
 * au plus) depuis le début du suivi et le départ de l'enveloppe. Sert à tempérer la projection du
 * mois en cours. Une enveloppe sans mois terminé n'a pas d'habitude.
 */
fun dailyHabits(
    envelopes: List<fr.baptiste.bank.domain.model.Envelope>,
    spendings: List<Spending>,
    today: LocalDate,
    trackingStart: YearMonth?,
    excluded: Set<YearMonth> = emptySet(),
): Map<Long, Money> {
    val byEnvelope = spendings.filter { !it.wholePeriod }.groupBy { it.envelopeId }
    return envelopes.filter { it.type == fr.baptiste.bank.domain.model.EnvelopeType.DAILY }.mapNotNull { envelope ->
        val since = listOfNotNull(trackingStart, envelope.start?.let { YearMonth.from(it) }).maxOrNull()
        val spent = byEnvelope[envelope.id].orEmpty().map { it.date to it.amount }
        suggestedAmount(spent, Frequency.MONTHLY, today, since, excluded = excluded)?.let { envelope.id to it.monthlyAverage }
    }.toMap()
}
