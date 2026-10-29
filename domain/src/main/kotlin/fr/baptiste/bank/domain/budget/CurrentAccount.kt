package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import java.time.LocalDate

/*
 * Compte courant expliqué : passer du solde de la banque au reste du mois de l'app.
 *
 * Le solde du compte courant contient plus que ce qui reste à dépenser au quotidien :
 * - les charges du mois pas encore payées (prêt, abonnements qui tombent plus tard) ;
 * - l'épargne du mois pas encore virée vers un livret ou un placement ;
 * - le reste : argent des mois précédents jamais viré, provisions (vacances...) laissées sur le
 *   compte. C'est ce qui est « libre » sur le compte, hors budget du mois.
 * Si ce reste est négatif, le budget du mois prévoit plus que ce que contient le compte : salaire
 * pas encore arrivé, opérations à trier, ou dépenses passées hors de l'app.
 */

data class CurrentAccountExplained(
    val balance: Money,
    val asOf: LocalDate,
    /** Reste du mois : budget du quotidien moins ce qui en est dépensé. */
    val monthLeft: Money,
    /** Charges et provisions dont la période se termine ce mois-ci, pas encore dépensées. */
    val chargesDue: Money,
    /** Épargne du mois (objectif et transferts) moins ce qui a déjà été viré vers mes comptes. */
    val savingsPending: Money,
    /** Dépenses de la banque pas encore rangées : elles sont sorties du compte, pas du budget. */
    val unsortedOut: Money,
    /** Provisions mises de côté mois après mois (vacances, projets) et pas encore dépensées. */
    val provisionsHeld: Money = Money.ZERO,
) {
    /**
     * Ce qui reste sur le compte hors budget du mois (négatif : il manque de l'argent) : la réserve,
     * que l'on peut mettre de côté sans toucher au mois.
     */
    val free: Money get() = balance - monthLeft - chargesDue - savingsPending - provisionsHeld
}

/**
 * Décompose [balance], le solde du compte courant lu le [asOf], à partir du tableau de bord du
 * mois en cours. [transferredToAccounts] : virements du mois vers mes livrets et placements ;
 * [unsortedOut] : total des dépenses à trier (positif).
 */
fun Dashboard.explainCurrentAccount(
    balance: Money,
    asOf: LocalDate,
    transferredToAccounts: Money,
    unsortedOut: Money = Money.ZERO,
    /** Début des données : une provision ne compte pas les mois d'avant (ils n'ont pas été mis de côté). */
    since: LocalDate? = null,
): CurrentAccountExplained {
    val monthLeft = dailyBudget - dailySpent
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val chargesDue = envelopes
        .filter { it.envelope.type != EnvelopeType.DAILY && it.envelope.type != EnvelopeType.SAVINGS }
        .filter { !it.period.endInclusive.isAfter(monthEnd) }
        .fold(Money.ZERO) { sum, line -> if (line.remaining.isPositive) sum + line.remaining else sum }
    // Provisions qui courent au-delà du mois : ce qui en est mis de côté depuis le début de leur
    // période (ou du suivi, ou de leur mise de côté), moins ce qui en est déjà dépensé.
    val sinceMonth = since?.let { java.time.YearMonth.from(it) }
    val provisionsHeld = envelopes
        .filter { it.envelope.type in setOf(EnvelopeType.ANNUAL, EnvelopeType.RECURRING, EnvelopeType.PLANNED) }
        .filter { it.period.endInclusive.isAfter(monthEnd) }
        .fold(Money.ZERO) { sum, line ->
            val funded = line.envelope.fundedThrough(java.time.YearMonth.from(today), line.period, line.allocated, sinceMonth)
            val held = funded + line.fromReserve - line.spent
            if (held.isPositive) sum + held else sum
        }
    // L'épargne à virer comprend ce qui a été mis de côté depuis la réserve ce mois-ci.
    val pending = savings.secured + reserveToSavings - transferredToAccounts
    return CurrentAccountExplained(
        balance = balance,
        asOf = asOf,
        monthLeft = monthLeft,
        chargesDue = chargesDue,
        savingsPending = if (pending.isPositive) pending else Money.ZERO,
        unsortedOut = unsortedOut,
        provisionsHeld = provisionsHeld,
    )
}

/**
 * Objectifs d'épargne, par paliers : d'abord la précaution (sur ce qui est disponible tout de
 * suite, les livrets), puis un total pour les projets de vie (tous comptes). Au-delà : projets.
 */
data class SavingsGoals(
    val precaution: Money,
    val total: Money,
    /** Mois où le total doit être atteint (fin de ce mois), ou null : sans échéance. */
    val totalBy: java.time.YearMonth? = null,
)

/** Ce que demande l'échéance du total, comparé au rythme d'épargne du mois. */
data class GoalPace(
    /** Salaires restants d'ici l'échéance : les mois après le mois en cours, échéance comprise. */
    val monthsLeft: Int,
    /** À mettre de côté par mois pour y arriver (arrondi à l'euro supérieur). */
    val monthlyNeeded: Money,
    /** Épargne prévue ce mois-ci. */
    val monthlyPace: Money,
    /** À ce rythme, le mois où le total serait atteint (null : rythme nul ou négatif). */
    val reachedAt: java.time.YearMonth?,
) {
    val onTrack: Boolean get() = monthlyPace >= monthlyNeeded
}

data class GoalsProgress(
    val available: Money,
    val invested: Money,
    val goals: SavingsGoals,
) {
    val total: Money get() = available + invested
    val precautionReached: Boolean get() = !available.isNegative && available >= goals.precaution
    val totalReached: Boolean get() = total >= goals.total

    /** Ce qui manque encore à la précaution, puis au total (zéro si atteint). */
    val precautionMissing: Money get() = (goals.precaution - available).let { if (it.isPositive) it else Money.ZERO }
    val totalMissing: Money get() = (goals.total - total).let { if (it.isPositive) it else Money.ZERO }

    /** Au-delà du total visé : la part libre pour les projets personnels. */
    val beyond: Money get() = (total - goals.total).let { if (it.isPositive) it else Money.ZERO }

    /**
     * Rythme demandé par l'échéance, ou null (pas d'échéance, ou total déjà atteint). Le mois en
     * cours n'est pas compté : son salaire est déjà réparti ; ce qu'il reste à épargner se joue sur
     * les salaires suivants. Une échéance déjà passée ou ce mois-ci compte pour un mois.
     */
    fun pace(today: java.time.LocalDate, monthlySavings: Money): GoalPace? {
        val by = goals.totalBy ?: return null
        if (totalReached) return null
        val current = java.time.YearMonth.from(today)
        val monthsLeft = maxOf(1, java.time.temporal.ChronoUnit.MONTHS.between(current, by).toInt())
        val needed = totalMissing.divideCeil(monthsLeft.toLong() * 100) * 100
        val reachedAt = if (monthlySavings.isPositive) {
            current.plusMonths(maxOf(1L, totalMissing.divideCeil(monthlySavings.cents).cents))
        } else {
            null
        }
        return GoalPace(monthsLeft, needed, monthlySavings, reachedAt)
    }
}

/** Avancement vers les objectifs : les livrets comptent comme disponibles, les placements à part. */
fun Holdings.progress(goals: SavingsGoals): GoalsProgress {
    val available = lines.filter { it.account.type == fr.baptiste.bank.domain.model.AccountType.SAVINGS }
        .fold(Money.ZERO) { sum, line -> sum + (line.balance ?: Money.ZERO) }
    return GoalsProgress(available = available, invested = total - available, goals = goals)
}
