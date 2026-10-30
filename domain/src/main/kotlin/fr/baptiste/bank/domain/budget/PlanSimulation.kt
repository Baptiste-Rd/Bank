package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import java.time.YearMonth

/*
 * « Essayer un plan » : un brouillon du plan du mois, sans rien changer au vrai budget.
 *
 * Question : « si je mets 2 000 € de côté d'ici 3 mois, que me reste-t-il pour vivre, et est-ce
 * tenable ? ». Le salaire et les charges sont fixes ; le projet prend sa part chaque mois (montant
 * ÷ mois), l'épargne mensuelle peut changer, et le quotidien est ce qui reste.
 *
 * Le verdict compare ce quotidien à ce que je dépense vraiment au quotidien : la moyenne des mois
 * renseignés choisis (les trois derniers mois terminés par défaut ; un mois exceptionnel s'exclut).
 */

enum class PlanVerdict {
    /** Le quotidien simulé couvre ma dépense habituelle. */
    REALISTIC,

    /** Jusqu'à 10 % en dessous : tenable en se serrant un peu. */
    TIGHT,

    /** Au-delà : pas tenable à ce rythme. */
    UNREALISTIC,

    /** Pas de mois de référence : rien à comparer. */
    UNKNOWN,
}

data class PlanInput(
    /** Montant du projet (zéro : pas de projet, seulement un changement d'épargne). */
    val project: Money,
    /** Mois pour le réunir, mois en cours compris (au moins 1). */
    val months: Int,
    /** Épargne mensuelle voulue. */
    val monthlySavings: Money,
    /** Ce que je compte dépenser de moins chaque mois au quotidien (sorties, bar...), le temps du projet. */
    val reductions: Money = Money.ZERO,
)

data class PlanResult(
    /** Part mensuelle du projet. */
    val projectShare: Money,
    /** Budget du quotidien aujourd'hui, et avec le plan. */
    val dailyBefore: Money,
    val dailyAfter: Money,
    /** Le même, par jour (sur 30 jours). */
    val perDayAfter: Money,
    /** Dépense du quotidien attendue : l'habituelle (moyenne des mois de référence) moins les réductions, ou null. */
    val reference: Money?,
    val verdict: PlanVerdict,
    /** Serré : ce qu'il faudrait dépenser de moins par jour. */
    val savePerDay: Money? = null,
    /** Peu réaliste : le nombre de mois qui rendrait le projet tenable (null : jamais à cette épargne). */
    val tenableMonths: Int? = null,
)

private const val DAYS_PER_MONTH = 30L

/**
 * Simule [input] sur le tableau de bord du mois. [currentSavings] : l'épargne mensuelle actuelle
 * (objectif des enveloppes d'épargne). Le projet est un achat prévu : sa part est montant ÷ mois.
 */
fun simulatePlan(dashboard: Dashboard, input: PlanInput, currentSavings: Money, usual: Money?): PlanResult {
    // Les réductions promises baissent la dépense attendue : c'est elle que le quotidien doit couvrir.
    val reference = usual?.let { (it - input.reductions).let { adjusted -> if (adjusted.isNegative) Money.ZERO else adjusted } }
    val months = input.months.coerceAtLeast(1)
    val share = if (input.project.isPositive) input.project.divideCeil(months.toLong()) else Money.ZERO
    val savingsChange = input.monthlySavings - currentSavings
    val before = dashboard.dailyBudget
    val after = before - share - savingsChange
    val perDay = after.divideFloor(DAYS_PER_MONTH)
    if (usual == null || reference == null || !usual.isPositive) {
        return PlanResult(share, before, after, perDay, reference, PlanVerdict.UNKNOWN)
    }
    val tightFloor = reference - reference.divideCeil(10)
    return when {
        after >= reference -> PlanResult(share, before, after, perDay, reference, PlanVerdict.REALISTIC)
        after >= tightFloor -> PlanResult(
            share, before, after, perDay, reference, PlanVerdict.TIGHT,
            savePerDay = (reference - after).divideCeil(DAYS_PER_MONTH),
        )
        else -> {
            // Combien de mois pour que la part du projet laisse au quotidien ma dépense habituelle ?
            val room = before - savingsChange - reference
            val tenable = if (room.isPositive && input.project.isPositive) {
                input.project.divideCeil(room.cents).cents.toInt().coerceAtLeast(months + 1)
            } else {
                null
            }
            PlanResult(share, before, after, perDay, reference, PlanVerdict.UNREALISTIC, tenableMonths = tenable)
        }
    }
}

/** Dépense du quotidien d'un mois : ce qui est rangé dans les enveloppes du quotidien. */
data class MonthDailySpending(val month: YearMonth, val spent: Money)

/**
 * Dépense du quotidien des [count] derniers mois terminés et renseignés avant [current]
 * (du plus récent au plus ancien). [envelopes] : toutes, archivées comprises.
 */
fun dailySpendingHistory(
    envelopes: List<Envelope>,
    spendings: List<Spending>,
    current: YearMonth,
    trackingStart: YearMonth?,
    count: Int = 3,
): List<MonthDailySpending> {
    val dailyIds = envelopes.filter { it.type == EnvelopeType.DAILY }.map { it.id }.toSet()
    return (1..count)
        .map { current.minusMonths(it.toLong()) }
        .filter { trackingStart == null || !it.isBefore(trackingStart) }
        .map { month ->
            MonthDailySpending(
                month,
                spendings.filter { it.envelopeId in dailyIds && YearMonth.from(it.date) == month }.sumOfMoney { it.amount },
            )
        }
}

/** Moyenne des mois retenus, ou null s'il n'y en a aucun. */
fun List<MonthDailySpending>.average(): Money? =
    if (isEmpty()) null else sumOfMoney { it.spent }.divideFloor(size.toLong())
