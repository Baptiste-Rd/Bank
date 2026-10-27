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
 * [alertThresholdPercent] %. Avec un disponible nul, toute dépense projetée déclenche l'alerte.
 *
 * @param available budget de la période (allocation, reliquat et transferts compris).
 */
fun project(
    spent: Money,
    available: Money,
    period: Period,
    today: LocalDate,
    alertThresholdPercent: Int = 10,
): Projection {
    require(alertThresholdPercent >= 0) { "Le seuil d'alerte ne peut pas être négatif" }
    val elapsed = period.daysElapsedAt(today).toLong()
    val projected = if (elapsed == 0L) {
        spent
    } else {
        (spent * period.lengthInDays.toLong()).divideCeil(elapsed)
    }
    val overrun = if (projected > available) projected - available else Money.ZERO
    val tolerance = if (available.isPositive) {
        (available * alertThresholdPercent.toLong()).divideFloor(100)
    } else {
        Money.ZERO
    }
    return Projection(
        spent = spent,
        available = available,
        projected = projected,
        overrun = overrun,
        isAlert = overrun > tolerance,
    )
}
