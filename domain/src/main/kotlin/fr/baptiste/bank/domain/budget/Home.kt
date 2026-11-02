package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import java.time.LocalDate
import java.time.YearMonth

/*
 * L'accueil, tableau de bord du mois. Chaque bloc répond à une question :
 * 1. Combien j'ai, et où j'en suis ? Le solde du compte, le dépensé du mois face au budget, et ce
 *    qui doit encore sortir (charges pas encore prélevées).
 * 2. Mes chiffres sont-ils complets ? Ce qui reste à trier (rien s'il n'y a rien).
 * 3. Mon épargne avance-t-elle ? Les autres comptes, et le versement du mois à faire.
 * 4. Est-ce que je tiens le rythme ? Le dépensé cumulé du mois face à la ligne du budget.
 * 5. Où reste-t-il de l'argent ? Les enveloppes, en une carte de six lignes au plus.
 */

/** Charges du mois pas encore prélevées (rien n'y est dépensé) : ce qui doit encore sortir du compte. */
fun chargesToCome(lines: List<EnvelopeLine>, today: LocalDate): Money =
    lines.filter { it.envelope.type == EnvelopeType.FIXED && YearMonth.from(it.period.endInclusive) == YearMonth.from(today) }
        .filter { !it.spent.isPositive && it.remaining.isPositive }
        .sumOfMoney { it.remaining }

/** Le dépensé cumulé du mois, jour par jour, face au rythme du budget. */
data class MonthPace(
    val month: YearMonth,
    /** Dépensé cumulé à la fin de chaque jour, du 1er à aujourd'hui (les jours à venir n'y sont pas). */
    val cumulative: List<Money>,
    val budget: Money,
) {
    val days: Int get() = month.lengthOfMonth()
    val spent: Money get() = cumulative.lastOrNull() ?: Money.ZERO

    /** Ce que le budget prévoyait d'avoir dépensé à la fin d'aujourd'hui (au prorata des jours). */
    val expected: Money get() = (budget * cumulative.size.toLong()).divideFloor(days.toLong())

    /** Écart au rythme (positif : au-dessus, on dépense plus vite que prévu). */
    val gap: Money get() = spent - expected
}

/** Rythme de [month] à [today] : [spent] (jour, montant) cumulé, face à [budget]. */
fun monthPace(month: YearMonth, today: LocalDate, spent: List<Pair<LocalDate, Money>>, budget: Money): MonthPace {
    val last = when {
        YearMonth.from(today) == month -> today.dayOfMonth
        YearMonth.from(today).isAfter(month) -> month.lengthOfMonth()
        else -> 0
    }
    val byDay = spent.filter { YearMonth.from(it.first) == month }.groupBy { it.first.dayOfMonth }
    var total = Money.ZERO
    val cumulative = (1..last).map { day ->
        total += byDay[day].orEmpty().sumOfMoney { it.second }
        total
    }
    return MonthPace(month, cumulative, budget)
}

/** La carte des enveloppes de l'accueil : quotidien et budgets rares ligne à ligne, charges en une ligne. */
data class HomeBudget(
    val daily: List<EnvelopeLine>,
    val charges: List<EnvelopeLine>,
    val rare: List<EnvelopeLine>,
    /** Lignes non montrées (« Tout voir »). */
    val hidden: Int,
) {
    /** Charges déjà prélevées (quelque chose y est dépensé). */
    val chargesPaid: Int get() = charges.count { it.spent.isPositive }
}

/**
 * Lignes de l'accueil, [maxRows] au plus (la ligne des charges compte pour une) : un dépassement
 * d'abord, puis le quotidien, puis les budgets rares dont la remise à zéro arrive le plus tôt.
 */
fun homeBudget(lines: List<EnvelopeLine>, maxRows: Int = 6): HomeBudget {
    val overspentFirst = compareByDescending<EnvelopeLine> { it.remaining.isNegative }
    val daily = lines.filter { it.envelope.type == EnvelopeType.DAILY }.sortedWith(overspentFirst)
    val charges = lines.filter { it.envelope.type == EnvelopeType.FIXED }
    val rare = lines.filter { it.envelope.type in setOf(EnvelopeType.RECURRING, EnvelopeType.ANNUAL, EnvelopeType.PLANNED) }
        .sortedWith(overspentFirst.thenBy { it.period.endInclusive })
    val room = maxRows - if (charges.isEmpty()) 0 else 1
    val shownDaily = daily.take(room)
    val shownRare = rare.take((room - shownDaily.size).coerceAtLeast(0))
    return HomeBudget(shownDaily, charges, shownRare, hidden = daily.size + rare.size - shownDaily.size - shownRare.size)
}

/** Le versement du mois vers un compte d'épargne : à faire, ou fait. */
sealed interface SavingReminder {
    data class Due(val amount: Money) : SavingReminder
    data class Done(val on: LocalDate) : SavingReminder
}

/** Jours pendant lesquels un versement fait reste affiché (« versé le 2 »). */
private const val DONE_VISIBLE_DAYS = 3L

/**
 * Rappel d'un compte qui a un versement prévu chaque mois : ce qui reste à verser ce mois-ci, ou
 * le versement fait (trois jours), sinon rien.
 */
fun HoldingLine.reminder(today: LocalDate): SavingReminder? {
    val target = account.monthlyTarget?.takeIf { it.isPositive } ?: return null
    val due = target - transferredThisMonth
    if (due.isPositive) return SavingReminder.Due(due)
    val last = lastTransfer ?: return null
    return if (!last.plusDays(DONE_VISIBLE_DAYS).isBefore(today)) SavingReminder.Done(last) else null
}
