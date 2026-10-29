package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.category
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import java.time.LocalDate
import java.time.YearMonth

/*
 * Dépenses réelles, par catégorie et par enveloppe : ce qui s'est passé, face au budget.
 *
 * Compte : toute dépense rangée dans une enveloppe (quotidien, charges, provisions), remboursements
 * déduits. Ne compte pas : l'épargne (enveloppes d'épargne), les virements vers mes comptes et le
 * hors budget (ils ne sont rangés dans aucune enveloppe). Ce qui reste à trier n'appartient à
 * aucune catégorie : il est rendu à part, pour qu'un mois incomplet ne paraisse pas complet.
 *
 * Budget d'un mois : la part mensuelle de chaque enveloppe (une provision annuelle de 1 200 €
 * compte 100 € par mois), pour les enveloppes actives qui existent ce mois-là.
 */

data class EnvelopeSpent(val envelope: Envelope, val spent: Money, val budget: Money)

data class CategorySpent(val name: String, val spent: Money, val budget: Money, val envelopes: List<EnvelopeSpent>) {
    /** Dépassement du budget (zéro si dans le budget). */
    val over: Money get() = (spent - budget).let { if (it.isPositive) it else Money.ZERO }
}

data class MonthSpending(
    val month: YearMonth,
    /** Faux pour un mois non renseigné (avant le début du suivi, ou à venir) : il ne compte nulle part. */
    val tracked: Boolean,
    /** Catégories, la plus grosse dépense en premier. */
    val categories: List<CategorySpent>,
    /** Dépenses encore à trier (positif). */
    val unsorted: Money = Money.ZERO,
) {
    val total: Money get() = categories.sumOfMoney { it.spent }
    val budget: Money get() = categories.sumOfMoney { it.budget }
}

/** Part du budget d'une enveloppe pour [month] (rattrapage compris pour une provision). */
fun Envelope.monthlyBudget(month: YearMonth): Money =
    monthShare(month, periodContaining(month.atEndOfMonth()), amount)

/** Dépenses de [month] par catégorie. [envelopes] : toutes, archivées comprises (leurs dépenses restent). */
fun monthSpending(
    month: YearMonth,
    envelopes: List<Envelope>,
    spendings: List<Spending>,
    unsorted: Money = Money.ZERO,
    tracked: Boolean = true,
): MonthSpending {
    if (!tracked) return MonthSpending(month, tracked = false, categories = emptyList())
    val spentById = spendings
        .filter { YearMonth.from(it.date) == month }
        .groupBy { it.envelopeId }
        .mapValues { (_, list) -> list.sumOfMoney { it.amount } }
    val lines = envelopes
        .filter { it.type != EnvelopeType.SAVINGS }
        .map { envelope ->
            val budget = if (!envelope.archived && envelope.existsOn(month.atEndOfMonth())) envelope.monthlyBudget(month) else Money.ZERO
            EnvelopeSpent(envelope, spentById[envelope.id] ?: Money.ZERO, budget)
        }
        .filter { it.spent != Money.ZERO || it.budget != Money.ZERO }
    val categories = lines.groupBy { it.envelope.category }
        .map { (name, list) ->
            CategorySpent(
                name = name,
                spent = list.sumOfMoney { it.spent },
                budget = list.sumOfMoney { it.budget },
                envelopes = list.sortedByDescending { it.spent },
            )
        }
        .sortedWith(compareByDescending<CategorySpent> { it.spent }.thenByDescending { it.budget })
    return MonthSpending(month, tracked = true, categories = categories, unsorted = unsorted)
}

/** Une catégorie sur l'année : total, moyenne par mois renseigné, écart au budget (positif : dépassé). */
data class CategoryYear(val name: String, val total: Money, val average: Money, val budget: Money) {
    val gap: Money get() = total - budget
}

data class YearSpending(val year: Int, val months: List<MonthSpending>) {
    val tracked: List<MonthSpending> get() = months.filter { it.tracked }
    val total: Money get() = tracked.sumOfMoney { it.total }

    /** Moyenne par mois renseigné (zéro sans mois renseigné). */
    val monthlyAverage: Money get() = if (tracked.isEmpty()) Money.ZERO else total.divideFloor(tracked.size.toLong())

    val unsorted: Money get() = tracked.sumOfMoney { it.unsorted }

    /** Les catégories de l'année, la plus grosse en premier. */
    fun categories(): List<CategoryYear> {
        val count = tracked.size.toLong()
        if (count == 0L) return emptyList()
        return tracked.flatMap { it.categories }
            .groupBy { it.name }
            .map { (name, list) ->
                val total = list.sumOfMoney { it.spent }
                CategoryYear(name, total, total.divideFloor(count), list.sumOfMoney { it.budget })
            }
            .sortedByDescending { it.total }
    }
}

/**
 * Dépenses de [year], mois par mois, jusqu'au mois de [today] pour l'année en cours. Un mois avant
 * [trackingStart] est non renseigné. [unsortedByMonth] : ce qui reste à trier, par mois.
 */
fun yearSpending(
    year: Int,
    today: LocalDate,
    trackingStart: YearMonth?,
    envelopes: List<Envelope>,
    spendings: List<Spending>,
    unsortedByMonth: Map<YearMonth, Money> = emptyMap(),
): YearSpending {
    val current = YearMonth.from(today)
    val last = if (year == current.year) current.monthValue else if (year < current.year) 12 else 0
    val months = (1..last).map { number ->
        val month = YearMonth.of(year, number)
        val tracked = trackingStart == null || !month.isBefore(trackingStart)
        monthSpending(month, envelopes, spendings, unsortedByMonth[month] ?: Money.ZERO, tracked)
    }
    return YearSpending(year, months)
}
