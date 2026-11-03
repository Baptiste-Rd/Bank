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
    /** Sorties classées hors budget (positif) : pas comptées ; à vérifier si le total paraît bas. */
    val outOfBudget: Money = Money.ZERO,
    /** Virements vers mes comptes (positif) : de l'épargne, pas des dépenses. */
    val toAccounts: Money = Money.ZERO,
) {
    val total: Money get() = categories.sumOfMoney { it.spent }
    val budget: Money get() = categories.sumOfMoney { it.budget }
}

/**
 * Part du budget d'une enveloppe pour [month] (rattrapage compris pour une provision), d'après la
 * période enregistrée de ce mois-là : changer un montant aujourd'hui ne réécrit pas le passé.
 */
fun Envelope.monthlyBudget(month: YearMonth): Money {
    val period = periodContaining(month.atEndOfMonth())
    return monthShare(month, period, amountIn(period))
}

/**
 * Les dépenses comptées dans [month] : celles des enveloppes connues, hors épargne. Une seule règle
 * pour le total du mois de l'analyse ([monthSpending]) et le dépensé de l'accueil ([monthPace]) :
 * les deux chiffres ne peuvent pas diverger.
 */
fun countedSpendings(month: YearMonth, envelopes: List<Envelope>, spendings: List<Spending>): List<Spending> {
    val counted = envelopes.filter { it.type != EnvelopeType.SAVINGS }.map { it.id }.toSet()
    return spendings.filter { YearMonth.from(it.date) == month && it.envelopeId in counted }
}

/** Dépenses de [month] par catégorie. [envelopes] : toutes, archivées comprises (leurs dépenses restent). */
fun monthSpending(
    month: YearMonth,
    envelopes: List<Envelope>,
    spendings: List<Spending>,
    unsorted: Money = Money.ZERO,
    tracked: Boolean = true,
    outOfBudget: Money = Money.ZERO,
    toAccounts: Money = Money.ZERO,
): MonthSpending {
    if (!tracked) return MonthSpending(month, tracked = false, categories = emptyList())
    val spentById = countedSpendings(month, envelopes, spendings)
        .groupBy { it.envelopeId }
        .mapValues { (_, list) -> list.sumOfMoney { it.amount } }
    val lines = envelopes
        .filter { it.type != EnvelopeType.SAVINGS }
        .map { envelope ->
            // Une enveloppe retirée garde son budget dans les mois qu'elle a couverts. Une archivée
            // avant que l'app ne retienne la date du retrait n'en a plus : on ne sait pas quand.
            val known = !envelope.archived || envelope.retiredOn != null
            val budget = if (known && envelope.existedDuring(month)) envelope.monthlyBudget(month) else Money.ZERO
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
    return MonthSpending(month, tracked = true, categories = categories, unsorted = unsorted, outOfBudget = outOfBudget, toAccounts = toAccounts)
}

/** Une catégorie sur l'année : total, moyenne par mois renseigné, écart au budget (positif : dépassé). */
/**
 * Une catégorie sur l'année. [average] et [gap] ne portent que sur les mois terminés : un mois en
 * cours (un loyer pas encore prélevé) ferait croire à une économie.
 *
 * @property total dépensé sur l'année, mois en cours compris.
 * @property budget budget des mois terminés.
 * @property completeSpent dépensé sur les mois terminés.
 */
data class CategoryYear(
    val name: String,
    val total: Money,
    val average: Money,
    val budget: Money,
    val completeSpent: Money = total,
) {
    val gap: Money get() = completeSpent - budget
}

/**
 * @property inProgress le mois en cours, s'il fait partie de l'année : il compte dans le total mais
 *   pas dans les moyennes ni les écarts au budget.
 */
data class YearSpending(val year: Int, val months: List<MonthSpending>, val inProgress: YearMonth? = null) {
    val tracked: List<MonthSpending> get() = months.filter { it.tracked }

    /** Mois renseignés et terminés : la base des moyennes et des écarts. */
    val complete: List<MonthSpending> get() = tracked.filter { it.month != inProgress }

    val total: Money get() = tracked.sumOfMoney { it.total }

    /** Moyenne par mois renseigné terminé (zéro sans mois terminé). */
    val monthlyAverage: Money get() = if (complete.isEmpty()) Money.ZERO else complete.sumOfMoney { it.total }.divideFloor(complete.size.toLong())

    val unsorted: Money get() = tracked.sumOfMoney { it.unsorted }

    /** Les catégories de l'année, la plus grosse en premier. */
    fun categories(): List<CategoryYear> {
        if (tracked.isEmpty()) return emptyList()
        val done = complete.map { it.month }.toSet()
        val count = done.size.toLong()
        return tracked.flatMap { month -> month.categories.map { month.month to it } }
            .groupBy { it.second.name }
            .map { (name, list) ->
                val finished = list.filter { it.first in done }.map { it.second }
                val completeSpent = finished.sumOfMoney { it.spent }
                CategoryYear(
                    name = name,
                    total = list.sumOfMoney { it.second.spent },
                    average = if (count == 0L) Money.ZERO else completeSpent.divideFloor(count),
                    budget = finished.sumOfMoney { it.budget },
                    completeSpent = completeSpent,
                )
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
    outOfBudgetByMonth: Map<YearMonth, Money> = emptyMap(),
    toAccountsByMonth: Map<YearMonth, Money> = emptyMap(),
): YearSpending {
    val current = YearMonth.from(today)
    val last = if (year == current.year) current.monthValue else if (year < current.year) 12 else 0
    val months = (1..last).map { number ->
        val month = YearMonth.of(year, number)
        val tracked = trackingStart == null || !month.isBefore(trackingStart)
        monthSpending(
            month,
            envelopes,
            spendings,
            unsortedByMonth[month] ?: Money.ZERO,
            tracked,
            outOfBudgetByMonth[month] ?: Money.ZERO,
            toAccountsByMonth[month] ?: Money.ZERO,
        )
    }
    return YearSpending(year, months, inProgress = current.takeIf { it.year == year })
}
