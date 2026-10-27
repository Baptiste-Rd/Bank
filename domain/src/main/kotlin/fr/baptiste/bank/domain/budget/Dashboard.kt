package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.domain.period.Scale
import java.time.LocalDate

/**
 * Montant dépensé sur une enveloppe à une date donnée (positif pour une dépense).
 * Une transaction répartie sur plusieurs enveloppes donne plusieurs dépenses.
 */
data class Spending(
    val envelopeId: Long,
    val date: LocalDate,
    val amount: Money,
)

/** Situation d'une enveloppe sur sa période en cours. */
data class EnvelopeLine(
    val envelope: Envelope,
    val period: Period,
    val available: Money,
    val spent: Money,
    val remaining: Money,
    val projection: Projection,
)

/**
 * Tout ce que l'écran d'accueil affiche, calculé à partir des données brutes.
 *
 * @property dailyBudget total du mois alloué aux enveloppes quotidiennes.
 * @property suggestedDailyBudget ce que le salaire permet d'allouer au quotidien (null sans salaire).
 * @property unallocated portefeuille : salaire non attribué à une enveloppe (null sans salaire).
 */
data class Dashboard(
    val today: LocalDate,
    val dailyAllowance: DailyAllowance,
    val dailyBudget: Money,
    val suggestedDailyBudget: Money?,
    val unallocated: Money?,
    val envelopes: List<EnvelopeLine>,
) {
    val alerts: List<EnvelopeLine> get() = envelopes.filter { it.projection.isAlert }
}

/**
 * Calcule le tableau de bord du jour.
 *
 * @param allocations montants alloués pour une période précise, indexés par (id d'enveloppe, clé de période).
 *   En l'absence d'allocation, le montant par défaut de l'enveloppe s'applique.
 * @param spendings dépenses couvrant au moins l'année en cours et la semaine en cours.
 */
fun computeDashboard(
    envelopes: List<Envelope>,
    allocations: Map<Pair<Long, String>, Money>,
    spendings: List<Spending>,
    monthlyIncome: Money?,
    today: LocalDate,
    alertThresholdPercent: Int = 10,
): Dashboard {
    val active = envelopes.filterNot { it.archived }
    val spendingsByEnvelope = spendings.groupBy { it.envelopeId }

    val lines = active.map { envelope ->
        val period = envelope.scale.periodContaining(today)
        val available = allocations[envelope.id to period.key] ?: envelope.amount
        val spent = spendingsByEnvelope[envelope.id].orEmpty()
            .filter { it.date in period }
            .sumOfMoney { it.amount }
        EnvelopeLine(
            envelope = envelope,
            period = period,
            available = available,
            spent = spent,
            remaining = available - spent,
            projection = project(spent, available, period, today, alertThresholdPercent),
        )
    }

    val dailyLines = lines.filter { it.envelope.type == EnvelopeType.DAILY }
    val dailyBudget = dailyLines.sumOfMoney { it.available.convert(it.envelope.scale, Scale.MONTH) }
    val month = Scale.MONTH.periodContaining(today)
    val dailyIds = dailyLines.map { it.envelope.id }.toSet()
    val dailySpendingsThisMonth = spendings.filter { it.envelopeId in dailyIds && it.date in month }

    val allowance = dailyAllowance(
        monthlyDailyBudget = dailyBudget,
        spentBeforeToday = dailySpendingsThisMonth.filter { it.date < today }.sumOfMoney { it.amount },
        spentToday = dailySpendingsThisMonth.filter { it.date == today }.sumOfMoney { it.amount },
        today = today,
    )

    val suggested = monthlyIncome?.let { suggestedDailyBudget(it, active) }

    return Dashboard(
        today = today,
        dailyAllowance = allowance,
        dailyBudget = dailyBudget,
        suggestedDailyBudget = suggested,
        unallocated = suggested?.let { it - dailyBudget },
        envelopes = lines,
    )
}

/** Plage de dates à charger pour que [computeDashboard] dispose de toutes les dépenses utiles. */
fun dashboardDateRange(today: LocalDate): ClosedRange<LocalDate> {
    val year = Scale.YEAR.periodContaining(today)
    val week = Scale.WEEK.periodContaining(today)
    return minOf(year.start, week.start)..maxOf(year.endInclusive, week.endInclusive)
}
