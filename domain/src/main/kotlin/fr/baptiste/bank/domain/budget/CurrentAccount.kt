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
    /** Salaire déjà reçu qui finance le mois suivant (versé en fin de mois) : il n'est pas libre. */
    val nextMonthSalary: Money = Money.ZERO,
) {
    /**
     * Ce qui reste sur le compte hors budget du mois (négatif : il manque de l'argent) : la réserve,
     * que l'on peut mettre de côté sans toucher au mois.
     */
    val free: Money get() = balance - monthLeft - chargesDue - savingsPending - provisionsHeld - nextMonthSalary
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
    /** Salaire déjà sur le compte pour le mois suivant (voir [nextMonthSalaryReceived]). */
    nextMonthSalary: Money = Money.ZERO,
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
    // Une somme mise à part : son reste est déjà pris, il n'est pas disponible.
    val lumpsHeld = envelopes.filter { it.envelope.type == EnvelopeType.LUMP && !it.stopped }
        .fold(Money.ZERO) { sum, line -> if (line.remaining.isPositive) sum + line.remaining else sum }
    // L'épargne à virer comprend ce qui a été mis de côté depuis la réserve ce mois-ci.
    val pending = savings.secured + reserveToSavings - transferredToAccounts
    return CurrentAccountExplained(
        balance = balance,
        asOf = asOf,
        monthLeft = monthLeft,
        chargesDue = chargesDue,
        savingsPending = if (pending.isPositive) pending else Money.ZERO,
        unsortedOut = unsortedOut,
        provisionsHeld = provisionsHeld + lumpsHeld,
        nextMonthSalary = nextMonthSalary,
    )
}

/**
 * Salaire déjà reçu le [today] pour le mois suivant : un salaire versé en fin de mois
 * ([paysNextMonth]) et arrivé ce mois-ci finance le mois prochain, pas celui-ci. Zéro sinon.
 */
fun nextMonthSalaryReceived(today: LocalDate, inflows: List<Inflow>, key: String, paysNextMonth: Boolean): Money {
    if (!paysNextMonth) return Money.ZERO
    val next = monthIncome(java.time.YearMonth.from(today).plusMonths(1), inflows.filter { !it.date.isAfter(today) }, key, true)
    return if (next != null && next.received) next.amount else Money.ZERO
}

