package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Period
import java.time.LocalDate

/**
 * Projection de fin de période d'une enveloppe, au rythme de dépense actuel.
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

/**
 * Extrapole la dépense au rythme moyen observé depuis le début de la période.
 *
 * Projection = dépensé × jours de la période / jours écoulés.
 * Une alerte est levée quand la projection dépasse le disponible de plus de
 * [alertThresholdPercent] % et d'au moins [minimumAlert] : sur une petite enveloppe (un parking à
 * 6 €), quelques centimes de rythme en trop ne sont pas une décision à prendre. Un dépassement
 * déjà constaté reste signalé à part (reste négatif).
 *
 * @param available budget de la période (allocation, reliquat et transferts compris).
 * @param spent total dépensé sur la période, rattrapages compris.
 * @param lumpSum part de [spent] saisie en rattrapage sur toute la période : elle n'est pas
 *   extrapolée, puisqu'elle ne reflète pas un rythme de dépense mais un total déjà connu.
 */
fun project(
    spent: Money,
    available: Money,
    period: Period,
    today: LocalDate,
    alertThresholdPercent: Int = 10,
    lumpSum: Money = Money.ZERO,
    minimumAlert: Money = Money.euros(3),
): Projection {
    require(alertThresholdPercent >= 0) { "Le seuil d'alerte ne peut pas être négatif" }
    val elapsed = period.daysElapsedAt(today).toLong()
    val paced = spent - lumpSum
    val projected = if (elapsed == 0L) {
        spent
    } else {
        lumpSum + (paced * period.lengthInDays.toLong()).divideCeil(elapsed)
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
        isAlert = overrun > tolerance,
    )
}
