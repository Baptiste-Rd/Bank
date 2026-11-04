package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Period
import java.time.LocalDate

/**
 * Projection de fin de période d'une enveloppe.
 *
 * @property projected dépense estimée à la fin de la période.
 * @property overrun dépassement estimé par rapport au budget disponible (0 s'il n'y en a pas).
 * @property isAlert vrai si le dépassement estimé franchit le seuil d'alerte.
 */
data class Projection(
    val spent: Money,
    val available: Money,
    val projected: Money,
    val overrun: Money,
    val isAlert: Boolean,
)

/** Jours écoulés en dessous desquels on ne prolonge pas un rythme : trois jours ne disent rien d'un mois. */
const val MIN_DAYS_FOR_PROJECTION = 10

/**
 * Estime la dépense de fin de période.
 *
 * Avant [minDays] jours écoulés, on ne prolonge pas le rythme du mois, et aucune alerte n'est levée
 * (58 € de courses en trois jours ne font pas 580 € dans le mois) : les jours restants sont comptés
 * au rythme de [habit] s'il est connu, sinon la projection est le dépensé. Ensuite, les jours restants sont
 * comptés à un rythme qui mélange celui de la période et [habit], la dépense habituelle d'une
 * période entière (moyenne des douze derniers mois) : le rythme de la période pèse d'autant plus
 * que la période avance. Sans habitude connue, seul le rythme de la période compte.
 *
 * Une alerte est levée quand la projection dépasse le disponible de plus de
 * [alertThresholdPercent] % et d'au moins [minimumAlert] : sur une petite enveloppe (un parking à
 * 6 €), quelques centimes de rythme en trop ne sont pas une décision à prendre. Un dépassement
 * déjà constaté reste signalé à part (reste négatif).
 *
 * @param available budget de la période (allocation, reliquat et transferts compris).
 * @param spent total dépensé sur la période, rattrapages compris.
 * @param lumpSum part de [spent] saisie en rattrapage sur toute la période : elle n'est pas
 *   extrapolée, puisqu'elle ne reflète pas un rythme de dépense mais un total déjà connu.
 * @param habit dépense habituelle sur une période entière (null si inconnue).
 */
fun project(
    spent: Money,
    available: Money,
    period: Period,
    today: LocalDate,
    alertThresholdPercent: Int = 10,
    lumpSum: Money = Money.ZERO,
    minimumAlert: Money = Money.euros(3),
    habit: Money? = null,
    minDays: Int = MIN_DAYS_FOR_PROJECTION,
): Projection {
    require(alertThresholdPercent >= 0) { "Le seuil d'alerte ne peut pas être négatif" }
    val length = period.lengthInDays.toLong()
    val elapsed = period.daysElapsedAt(today).toLong()
    val remaining = length - elapsed
    val paced = spent - lumpSum
    val tooEarly = elapsed < minOf(minDays.toLong(), length) && remaining > 0 && paced.isPositive
    val projected = when {
        // Début de période : le rythme habituel seul (l'épargne prévue du 4 ne suppose plus qu'on ne dépensera rien).
        remaining > 0 && elapsed < minOf(minDays.toLong(), length) && habit != null && habit.isPositive ->
            spent + (habit * remaining).divideCeil(length)
        elapsed == 0L || remaining == 0L || tooEarly || !paced.isPositive -> spent
        habit == null || !habit.isPositive -> lumpSum + (paced * length).divideCeil(elapsed)
        // Rythme des jours restants : (écoulés/durée) × rythme de la période + (restants/durée) ×
        // rythme habituel, soit paced/durée + habit × restants/durée² par jour.
        else -> spent + (paced * (remaining * length) + habit * (remaining * remaining)).divideCeil(length * length)
    }
    val overrun = if (projected > available) projected - available else Money.ZERO
    val percent = if (available.isPositive) {
        (available * alertThresholdPercent.toLong()).divideFloor(100)
    } else {
        Money.ZERO
    }
    val tolerance = maxOf(percent, minimumAlert)
    return Projection(
        spent = spent,
        available = available,
        projected = projected,
        overrun = overrun,
        isAlert = !tooEarly && overrun > tolerance,
    )
}
