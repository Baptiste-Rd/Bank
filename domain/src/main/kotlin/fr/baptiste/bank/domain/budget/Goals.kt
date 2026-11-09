package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/*
 * Les objectifs d'épargne : des enveloppes d'épargne avec un montant visé, et une date si on veut.
 * Ce qu'un objectif contient est la somme de ses mouvements (Placement) : la répartition de départ
 * des livrets, puis ce qui est placé au fil des virements. Tout reste annulable.
 */

/** Un mouvement d'argent dans un objectif. [isStart] : la répartition de départ des livrets. */
data class Placement(val envelopeId: Long, val date: LocalDate, val amount: Money, val isStart: Boolean = false)

/** Un objectif : l'enveloppe d'épargne et ce qu'elle contient. */
data class SavingsGoalLine(val envelope: Envelope, val held: Money) {
    val goal: Money? get() = envelope.goal

    /** Part du mois : celle de l'enveloppe (recalculée chaque mois pour un objectif daté). */
    val monthly: Money get() = envelope.amount
    val reached: Boolean get() = goal?.let { held >= it } == true

    /** Ce qui manque encore, zéro si atteint ; null sans objectif chiffré. */
    val missing: Money? get() = goal?.let { (it - held).let { left -> if (left.isPositive) left else Money.ZERO } }
}

/** Ce que montre Épargne : les objectifs, ce qui reste à placer, et d'où partir. */
data class SavingsState(
    val goals: List<SavingsGoalLine>,
    /** Versés sur les comptes depuis [since], pas encore rangés dans un objectif ; négatif : un retrait. */
    val toPlace: Money,
    val since: LocalDate,
    /** Ce que chaque compte a reçu depuis [since] et qui n'est pas encore rangé dans un objectif relié. */
    val received: Map<Long, Money>,
    /** Vrai une fois les livrets répartis entre les objectifs. */
    val started: Boolean,
    /** Total des livrets (les placements n'entrent pas dans la répartition de départ). */
    val livrets: Money,
)

/** Mois pour atteindre [by] depuis [month], tous deux compris ; au moins un. */
fun monthsUntil(month: YearMonth, by: YearMonth): Int = maxOf(1, ChronoUnit.MONTHS.between(month, by).toInt() + 1)

/** Part du mois d'un objectif daté : ce qui manque, réparti sur les mois restants, arrondi à 10 € au-dessus. */
fun goalMonthly(goal: Money, held: Money, by: YearMonth, month: YearMonth): Money {
    val missing = goal - held
    if (!missing.isPositive) return Money.ZERO
    val months = monthsUntil(month, by).toLong()
    val perMonth = (missing.cents + months - 1) / months
    return Money((perMonth + 999) / 1000 * 1000)
}

/** La part du mois si l'enveloppe est un objectif daté, sinon null. */
fun Envelope.datedGoalMonthly(held: Money, month: YearMonth): Money? {
    if (type != EnvelopeType.SAVINGS) return null
    return goalMonthly(goal ?: return null, held, goalBy ?: return null, month)
}

fun List<Placement>.heldByEnvelope(): Map<Long, Money> =
    groupBy { it.envelopeId }.mapValues { (_, list) -> list.sumOfMoney { it.amount } }

/** Les virements comptent dans « à placer » depuis la répartition de départ, sinon depuis [trackingStart]. */
fun List<Placement>.placingSince(trackingStart: LocalDate): LocalDate =
    // Le solde des livrets du jour de la répartition contient déjà les virements de ce jour-là.
    filter { it.isStart }.maxOfOrNull { it.date }?.plusDays(1) ?: trackingStart

/**
 * Versés sur les comptes depuis [since] (un montant positif est un versement, négatif un retrait),
 * moins ce qui a été rangé dans les objectifs hors répartition de départ.
 */
fun toPlace(transfers: List<AccountTransfer>, placements: List<Placement>, since: LocalDate): Money =
    transfers.filter { !it.date.isBefore(since) }.sumOfMoney { it.amount } - placements.filter { !it.isStart }.sumOfMoney { it.amount }

/**
 * Parts proposées pour [amount] arrivé sur les comptes : chaque objectif relié à un compte qui a
 * reçu de l'argent prend sa part du mois au plus, le reste va au premier objectif non atteint.
 */
fun proposeSplit(goals: List<SavingsGoalLine>, received: Map<Long, Money>, amount: Money): Map<Long, Money> {
    if (!amount.isPositive) return emptyMap()
    val parts = linkedMapOf<Long, Money>()
    val left = received.toMutableMap()
    var rest = amount
    goals.forEach { line ->
        val account = line.envelope.accountId ?: return@forEach
        val came = left[account]?.takeIf { it.isPositive } ?: return@forEach
        val part = minOf(line.monthly, came, rest)
        if (!part.isPositive) return@forEach
        parts[line.envelope.id] = part
        left[account] = came - part
        rest -= part
    }
    if (rest.isPositive) (goals.firstOrNull { !it.reached } ?: goals.firstOrNull())?.let { parts[it.envelope.id] = (parts[it.envelope.id] ?: Money.ZERO) + rest }
    return parts
}

/** Répartition de départ : chaque objectif dans l'ordre jusqu'à son montant visé, le reste au premier sans montant. */
fun fillInOrder(goals: List<SavingsGoalLine>, amount: Money): Map<Long, Money> {
    val parts = linkedMapOf<Long, Money>()
    var rest = amount
    goals.forEach { line ->
        val goal = line.goal ?: return@forEach
        val part = minOf(goal, rest)
        if (part.isPositive) {
            parts[line.envelope.id] = part
            rest -= part
        }
    }
    if (rest.isPositive) (goals.firstOrNull { it.goal == null } ?: goals.lastOrNull())?.let { parts[it.envelope.id] = (parts[it.envelope.id] ?: Money.ZERO) + rest }
    return parts
}

/**
 * Le « par jour » si [extra] était réservé en plus ce mois-ci : le même calcul que le tableau. Null
 * sans salaire : le quotidien ne vient pas alors de ce qui reste, une épargne ne le change pas.
 */
fun Dashboard.perDayWithReserved(extra: Money): Money? {
    if (income == null) return null
    return dailyAllowance(dailyBudget - extra, dailySpentBeforeToday, dailyAllowance.spentToday, today).allowance
}
