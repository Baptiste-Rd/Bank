package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import java.time.LocalDate

/**
 * Montant dépensé sur une enveloppe (positif pour une dépense).
 * Une transaction répartie sur plusieurs enveloppes donne plusieurs dépenses.
 *
 * @property wholePeriod dépense de rattrapage : elle porte sur toute la période de l'enveloppe
 *   contenant [date], pas sur ce jour précis. Elle compte comme déjà dépensée et ne réduit
 *   jamais la part du jour.
 */
data class Spending(
    val envelopeId: Long,
    val date: LocalDate,
    val amount: Money,
    val wholePeriod: Boolean = false,
)

/**
 * Mouvement d'argent daté entre deux enveloppes, ou entre une enveloppe et le portefeuille
 * (une extrémité à null). Il compte sur la période de chaque enveloppe qui contient [date] :
 * les périodes déjà closes ne changent pas, la période en cours est recalculée à partir de lui.
 */
data class Transfer(
    val id: Long,
    val date: LocalDate,
    val sourceEnvelopeId: Long?,
    val targetEnvelopeId: Long?,
    val amount: Money,
    val reason: String? = null,
)

/**
 * Situation d'une enveloppe sur sa période en cours.
 *
 * @property allocated montant prévu pour la période (montant de l'enveloppe ou allocation de la période).
 * @property transferred solde des transferts de la période (positif si l'enveloppe a reçu de l'argent).
 * @property available ce qui peut être dépensé sur la période : [allocated] + [transferred].
 */
data class EnvelopeLine(
    val envelope: Envelope,
    val period: Period,
    val allocated: Money,
    val transferred: Money,
    val available: Money,
    val spent: Money,
    val remaining: Money,
    val projection: Projection,
) {
    val needsCover: Boolean get() = remaining.isNegative || projection.isAlert
}

/** Un jour du mois en cours dans la bande de l'accueil. */
data class DayCell(
    val date: LocalDate,
    /** Dépenses quotidiennes datées de ce jour (hors rattrapages). */
    val spent: Money,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/**
 * Tout ce que l'écran d'accueil affiche, calculé à partir des données brutes.
 *
 * @property dailyBudget ce qui se dépense au quotidien ce mois-ci : avec un salaire, tout ce qui n'est
 *   pas réservé par une autre enveloppe ; sans salaire, la somme des enveloppes quotidiennes.
 * @property dailyAssigned part de [dailyBudget] rangée dans des enveloppes quotidiennes.
 * @property averageDailyShare dailyBudget réparti également sur les jours du mois : la référence de la bande.
 * @property catchUpSpent rattrapages du mois sur les enveloppes quotidiennes.
 * @property suggestedDailyBudget ce que le salaire permet d'allouer au quotidien (null sans salaire).
 * @property unallocated portefeuille : salaire du mois rangé dans aucune enveloppe (null sans salaire).
 *   Il fait partie du budget du quotidien.
 */
data class Dashboard(
    val today: LocalDate,
    val dailyAllowance: DailyAllowance,
    val dailyBudget: Money,
    val dailyAssigned: Money,
    val dailySpent: Money,
    val daysRemaining: Int,
    val averageDailyShare: Money,
    val catchUpSpent: Money,
    val monthDays: List<DayCell>,
    val suggestedDailyBudget: Money?,
    val unallocated: Money?,
    val envelopes: List<EnvelopeLine>,
    /** Épargne du mois : objectif, part à l'abri, prévision de fin de mois. */
    val savings: SavingsOutlook,
) {
    val alerts: List<EnvelopeLine> get() = envelopes.filter { it.needsCover }
}

/**
 * Calcule le tableau de bord du jour.
 *
 * @param allocations montants alloués pour une période précise, indexés par (id d'enveloppe, clé de période).
 *   En l'absence d'allocation, le montant par défaut de l'enveloppe s'applique.
 * @param spendings dépenses couvrant au moins la période en cours de chaque enveloppe.
 * @param transfers transferts non annulés, couvrant la même plage.
 */
fun computeDashboard(
    envelopes: List<Envelope>,
    allocations: Map<Pair<Long, String>, Money>,
    spendings: List<Spending>,
    monthlyIncome: Money?,
    today: LocalDate,
    alertThresholdPercent: Int = 10,
    transfers: List<Transfer> = emptyList(),
): Dashboard {
    val active = envelopes.filter { !it.archived && it.existsOn(today) }
    val spendingsByEnvelope = spendings.groupBy { it.envelopeId }
    val month = Scale.MONTH.periodContaining(today)

    /** Solde des transferts d'une enveloppe dont la date tombe dans [window]. */
    fun netTransfers(envelopeId: Long, window: Period): Money =
        transfers.filter { it.date in window && it.targetEnvelopeId == envelopeId }.sumOfMoney { it.amount } -
            transfers.filter { it.date in window && it.sourceEnvelopeId == envelopeId }.sumOfMoney { it.amount }

    val lines = active.map { envelope ->
        val period = envelope.periodContaining(today)
        val allocated = allocations[envelope.id to period.key] ?: envelope.amount
        val transferred = netTransfers(envelope.id, period)
        val available = allocated + transferred
        val inPeriod = spendingsByEnvelope[envelope.id].orEmpty().filter { it.date in period }
        val spent = inPeriod.sumOfMoney { it.amount }
        val lumpSum = inPeriod.filter { it.wholePeriod }.sumOfMoney { it.amount }
        EnvelopeLine(
            envelope = envelope,
            period = period,
            allocated = allocated,
            transferred = transferred,
            available = available,
            spent = spent,
            remaining = available - spent,
            // Seul le quotidien se dépense au fil des jours : on prolonge son rythme. Une charge, une
            // provision ou un achat se paie en une fois ; prolonger son rythme annoncerait à tort un
            // dépassement (un prêt payé le 5 « prévu » à 442 € pour 400). Tout y compte comme ponctuel.
            projection = project(
                spent,
                available,
                period,
                today,
                alertThresholdPercent,
                if (envelope.type == EnvelopeType.DAILY) lumpSum else spent,
            ),
        )
    }

    val dailyLines = lines.filter { it.envelope.type == EnvelopeType.DAILY }
    // Part du mois rangée dans les enveloppes quotidiennes, transferts du mois compris.
    // Un transfert n'est pas converti : c'est un montant ponctuel.
    val dailyAssigned = dailyLines.sumOfMoney {
        it.allocated.convert(it.envelope.frequency, Frequency.MONTHLY) + netTransfers(it.envelope.id, month)
    }
    // Portefeuille : le salaire moins la part mensuelle de chaque enveloppe, plus ce qui lui a
    // été rendu ce mois-ci, moins ce qui en est sorti.
    val wallet = monthlyIncome?.let { income ->
        val reserved = lines.sumOfMoney { it.allocated.convert(it.envelope.frequency, Frequency.MONTHLY) }
        val walletIn = transfers.filter { it.date in month && it.targetEnvelopeId == null }.sumOfMoney { it.amount }
        val walletOut = transfers.filter { it.date in month && it.sourceEnvelopeId == null }.sumOfMoney { it.amount }
        income - reserved + walletIn - walletOut
    }
    // Budget du quotidien : avec un salaire, tout ce qui n'est pas réservé ailleurs se dépense au
    // jour le jour, que ce soit rangé dans une enveloppe quotidienne ou resté dans le portefeuille.
    // Sans salaire, seules les enveloppes quotidiennes sont connues.
    val dailyBudget = if (wallet != null) wallet + dailyAssigned else dailyAssigned
    val dailyIds = dailyLines.map { it.envelope.id }.toSet()
    val dailyThisMonth = spendings.filter { it.envelopeId in dailyIds && it.date in month }
    val (catchUps, dated) = dailyThisMonth.partition { it.wholePeriod }

    val allowance = dailyAllowance(
        monthlyDailyBudget = dailyBudget,
        spentBeforeToday = catchUps.sumOfMoney { it.amount } +
            dated.filter { it.date < today }.sumOfMoney { it.amount },
        spentToday = dated.filter { it.date == today }.sumOfMoney { it.amount },
        today = today,
    )

    val spentByDay = dated.groupBy { it.date }
    val monthDays = (0 until month.lengthInDays).map { offset ->
        val date = month.start.plusDays(offset.toLong())
        DayCell(
            date = date,
            spent = spentByDay[date].orEmpty().sumOfMoney { it.amount },
            isToday = date == today,
            isFuture = date > today,
        )
    }

    val suggested = monthlyIncome?.let { suggestedDailyBudget(it, active) }

    // Épargne : objectif, transferts du mois, reste du quotidien au rythme actuel, dépassements.
    val savingsLines = lines.filter { it.envelope.type == EnvelopeType.SAVINGS }
    val savingsIds = savingsLines.map { it.envelope.id }.toSet()
    val dailyProjected = project(
        spent = dailyThisMonth.sumOfMoney { it.amount },
        available = dailyBudget,
        period = month,
        today = today,
        lumpSum = catchUps.sumOfMoney { it.amount },
    ).projected
    // Un dépassement compte le mois où sa période se termine (un mois pour une charge mensuelle,
    // le mois de l'achat pour un achat prévu) : pas une fois par mois tant qu'il dure.
    val overruns = lines
        .filter { it.envelope.type != EnvelopeType.DAILY && it.envelope.type != EnvelopeType.SAVINGS }
        .filter { it.period.endInclusive in month }
        .sumOfMoney { if (it.remaining.isNegative) -it.remaining else Money.ZERO }
    val savingsSpent = spendings.filter { it.envelopeId in savingsIds && it.date in month }.sumOfMoney { it.amount }
    // Le reste d'une enveloppe dont la période est terminée va à l'épargne (voir SavingsOutlook).
    val surpluses = lines
        .filter { it.envelope.type != EnvelopeType.DAILY && it.envelope.type != EnvelopeType.SAVINGS }
        // Seule une période qui se termine aujourd'hui est close : c'est le cas, au dernier jour du mois,
        // des charges mensuelles et des provisions qui se terminent ce mois-là.
        .filter { it.period.endInclusive == today }
        .sumOfMoney { if (it.remaining.isPositive) it.remaining else Money.ZERO }
    val savings = SavingsOutlook(
        target = savingsLines.sumOfMoney { it.allocated.convert(it.envelope.frequency, Frequency.MONTHLY) },
        transferred = savingsIds.fold(Money.ZERO) { total, id -> total + netTransfers(id, month) },
        dailyMargin = dailyBudget - dailyProjected,
        overruns = overruns + savingsSpent,
        isFinal = today == month.endInclusive,
        surpluses = surpluses,
    )

    return Dashboard(
        today = today,
        dailyAllowance = allowance,
        dailyBudget = dailyBudget,
        dailyAssigned = dailyAssigned,
        dailySpent = dailyThisMonth.sumOfMoney { it.amount },
        daysRemaining = month.daysRemainingFrom(today),
        averageDailyShare = dailyBudget.divideFloor(month.lengthInDays.toLong()),
        catchUpSpent = catchUps.sumOfMoney { it.amount },
        monthDays = monthDays,
        suggestedDailyBudget = suggested,
        unallocated = wallet,
        envelopes = lines,
        savings = savings,
    )
}

/**
 * Plage de dates à charger pour que [computeDashboard] dispose de toutes les dépenses utiles :
 * la période en cours de chaque enveloppe, et au minimum l'année et la semaine en cours.
 */
fun dashboardDateRange(today: LocalDate, envelopes: List<Envelope> = emptyList()): ClosedRange<LocalDate> {
    val periods = envelopes.map { it.periodContaining(today) } +
        Scale.YEAR.periodContaining(today) + Scale.WEEK.periodContaining(today)
    return periods.minOf { it.start }..periods.maxOf { it.endInclusive }
}

/** Une part d'un plan de couverture : d'où vient l'argent (null = portefeuille) et combien. */
data class TransferLeg(val sourceEnvelopeId: Long?, val amount: Money)

/**
 * Proposition pour couvrir une enveloppe en dépassement (constaté ou prévu).
 * @property shortfall ce qui manque encore si les sources disponibles ne suffisent pas.
 */
data class CoveragePlan(
    val target: EnvelopeLine,
    val needed: Money,
    val legs: List<TransferLeg>,
    val shortfall: Money,
)

/**
 * Prépare le transfert qui couvre [target], sans rien enregistrer : l'utilisateur valide.
 *
 * Le besoin est le dépassement constaté, sinon le dépassement prévu, arrondi à l'euro supérieur.
 * L'argent vient d'abord du portefeuille, puis des enveloppes flexibles qui ont du restant et ne
 * sont pas elles-mêmes en alerte, par priorité croissante puis par restant décroissant.
 * Une enveloppe protégée n'est jamais sollicitée.
 */
fun Dashboard.coveragePlan(target: EnvelopeLine): CoveragePlan? {
    // Le dépassement prévu jusqu'à la fin de la période comprend le constaté (on ne dépense pas
    // moins qu'on a déjà dépensé) : couvrir le prévu évite que l'enveloppe revienne dans « À faire ».
    val raw = when {
        target.projection.isAlert -> maxOf(target.projection.overrun, -target.remaining)
        target.remaining.isNegative -> -target.remaining
        else -> return null
    }
    val needed = raw.divideCeil(100) * 100
    val legs = mutableListOf<TransferLeg>()
    var left = needed

    val wallet = unallocated
    if (wallet != null && wallet.isPositive && left.isPositive) {
        val taken = minOf(wallet, left)
        legs += TransferLeg(sourceEnvelopeId = null, amount = taken)
        left -= taken
    }
    val sources = envelopes
        .filter {
            it.envelope.id != target.envelope.id &&
                it.envelope.status == EnvelopeStatus.FLEXIBLE &&
                it.remaining.isPositive &&
                !it.needsCover
        }
        .sortedWith(compareBy<EnvelopeLine> { it.envelope.priority }.thenByDescending { it.remaining })
    for (source in sources) {
        if (!left.isPositive) break
        val taken = minOf(source.remaining, left)
        legs += TransferLeg(sourceEnvelopeId = source.envelope.id, amount = taken)
        left -= taken
    }
    return CoveragePlan(target = target, needed = needed, legs = legs, shortfall = left)
}

/** Reste du mois du quotidien : son budget moins ce qui en est déjà dépensé. */
val Dashboard.monthLeft: Money get() = dailyBudget - dailySpent

/**
 * Reste du mois après un transfert de [amount] de [sourceId] vers [targetId] (null = non réparti),
 * fait aujourd'hui. Le quotidien réunit le non réparti (quand le salaire est connu) et les
 * enveloppes du quotidien : un transfert à l'intérieur ne change rien, un transfert qui en sort
 * (vers l'épargne, une charge...) le réduit d'autant, un transfert qui y entre l'augmente.
 */
fun Dashboard.monthLeftAfterTransfer(sourceId: Long?, targetId: Long?, amount: Money): Money {
    fun inDaily(id: Long?): Boolean =
        if (id == null) unallocated != null else envelopes.any { it.envelope.id == id && it.envelope.type == EnvelopeType.DAILY }
    val delta = when {
        inDaily(sourceId) == inDaily(targetId) -> Money.ZERO
        inDaily(sourceId) -> Money.ZERO - amount
        else -> amount
    }
    return monthLeft + delta
}
