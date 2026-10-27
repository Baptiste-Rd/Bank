package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/*
 * Achat prévu : « un vélo à 600 € en mars ».
 *
 * C'est une enveloppe dont la période va du mois en cours au mois de l'achat inclus (« tous les
 * 6 mois », à partir du 1er du mois en cours). Le reste du calcul existe déjà : son prix ramené
 * au mois (600 € / 6 = 100 €) est réservé chaque mois, sort du portefeuille, et l'achat se saisit
 * dans l'enveloppe le moment venu. Elle est protégée : la couverture automatique d'un
 * dépassement n'y puise jamais.
 */

/** Crée l'enveloppe d'un achat de [price] prévu en [purchaseMonth] (au plus tôt le mois en cours). */
fun plannedPurchase(name: String, price: Money, purchaseMonth: YearMonth, today: LocalDate): Envelope {
    val start = today.withDayOfMonth(1)
    val months = monthsUntil(purchaseMonth, today)
    return Envelope(
        id = 0,
        name = name,
        type = EnvelopeType.PLANNED,
        frequency = Frequency(months, Scale.MONTH),
        amount = price,
        status = EnvelopeStatus.PROTECTED,
        anchor = start,
    )
}

/** Nombre de mois réservés, mois en cours et mois de l'achat compris (au moins 1). */
fun monthsUntil(purchaseMonth: YearMonth, today: LocalDate): Int =
    (ChronoUnit.MONTHS.between(YearMonth.from(today), purchaseMonth) + 1).coerceAtLeast(1).toInt()

/** Mois de l'achat d'une enveloppe d'achat prévu : le dernier mois de sa première période. */
val Envelope.purchaseMonth: YearMonth?
    get() {
        if (type != EnvelopeType.PLANNED || frequency.scale != Scale.MONTH) return null
        val start = anchor ?: return null
        return YearMonth.from(start).plusMonths(frequency.every.toLong() - 1)
    }

/** Ce que l'achat prévu réserve chaque mois. */
val Envelope.monthlyShare: Money get() = amount.convert(frequency, Frequency.MONTHLY)
