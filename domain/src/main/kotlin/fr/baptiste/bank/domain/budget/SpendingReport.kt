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

/**
 * Une catégorie du mois. [spent] et [budget] comprennent ceux de ses sous-catégories ([children]) ;
 * [envelopes] ne liste que ses enveloppes directes.
 */
data class CategorySpent(
    val name: String,
    val spent: Money,
    val budget: Money,
    val envelopes: List<EnvelopeSpent>,
    val children: List<CategorySpent> = emptyList(),
) {
    /** Cette catégorie et toutes ses sous-catégories, à plat. */
    val descendants: List<CategorySpent> get() = children.flatMap { listOf(it) + it.descendants }

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
fun Envelope.monthlyBudget(month: YearMonth, since: YearMonth? = null): Money {
    val period = periodContaining(month.atEndOfMonth())
    return monthShare(month, period, amountIn(period), since)
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

/**
 * Dépensé de [month] cumulé à la fin de chaque jour qui a une dépense comptée : « Cumul du mois »
 * de la liste des opérations. Même règle que le total du mois ([countedSpendings]) : une part
 * d'épargne d'une dépense répartie n'y compte pas.
 */
fun cumulativeByDay(month: YearMonth, envelopes: List<Envelope>, spendings: List<Spending>): Map<LocalDate, Money> {
    var total = Money.ZERO
    return countedSpendings(month, envelopes, spendings)
        .groupBy { it.date }
        .toSortedMap()
        .mapValues { (_, list) -> total += list.sumOfMoney { it.amount }; total }
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
    /** Catégories imbriquées : la mère de chaque catégorie (voir parentsOf). */
    parents: Map<String, String> = emptyMap(),
    /** Ajouts et retraits non annulés : ceux datés du mois changent le budget de l'enveloppe, comme dans Budget. */
    transfers: List<Transfer> = emptyList(),
    /** Début du suivi : le rattrapage d'une provision en dépend (voir monthShare). */
    since: YearMonth? = null,
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
            val planned = if (known && envelope.existedDuring(month)) envelope.monthlyBudget(month, since) else Money.ZERO
            // Un dépassement couvert (ou de l'argent retiré) ce mois-ci change ce dont l'enveloppe disposait.
            val moved = transfers.filter { YearMonth.from(it.date) == month }
                .sumOfMoney { t -> if (t.targetEnvelopeId == envelope.id) t.amount else if (t.sourceEnvelopeId == envelope.id) -t.amount else Money.ZERO }
            val budget = planned + moved
            EnvelopeSpent(envelope, spentById[envelope.id] ?: Money.ZERO, budget)
        }
        .filter { it.spent != Money.ZERO || it.budget != Money.ZERO }
    val direct = lines.groupBy { it.envelope.category }
    val categories = categoryTree(direct, parents)
    return MonthSpending(month, tracked = true, categories = categories, unsorted = unsorted, outOfBudget = outOfBudget, toAccounts = toAccounts)
}

/**
 * Range les enveloppes de chaque catégorie dans l'arbre : une catégorie mère apparaît dès qu'une de
 * ses sous-catégories a une ligne, et additionne leurs dépenses et budgets aux siens. Rendu : les
 * catégories principales, la plus grosse dépense en premier (de même à chaque niveau).
 */
private fun categoryTree(direct: Map<String, List<EnvelopeSpent>>, parents: Map<String, String>): List<CategorySpent> {
    val key: (String) -> String = { fr.baptiste.bank.domain.bank.envelopeNameKey(it) }
    // Toutes les catégories utiles : celles qui ont des lignes, et leurs mères.
    val names = (direct.keys + direct.keys.flatMap { fr.baptiste.bank.domain.model.ancestorsOf(it, parents) })
        .distinctBy(key)
    val byKey = names.associateBy(key)
    fun parentKey(name: String): String? = fr.baptiste.bank.domain.model.ancestorsOf(name, parents).firstOrNull()?.let(key)
    fun build(name: String): CategorySpent {
        val own = direct.entries.firstOrNull { key(it.key) == key(name) }?.value.orEmpty()
        val children = names.filter { parentKey(it) == key(name) }.map(::build)
            .sortedWith(compareByDescending<CategorySpent> { it.spent }.thenByDescending { it.budget })
        return CategorySpent(
            name = byKey[key(name)] ?: name,
            spent = own.sumOfMoney { it.spent } + children.sumOfMoney { it.spent },
            budget = own.sumOfMoney { it.budget } + children.sumOfMoney { it.budget },
            envelopes = own.sortedByDescending { it.spent },
            children = children,
        )
    }
    return names.filter { parentKey(it) == null }.map(::build)
        .sortedWith(compareByDescending<CategorySpent> { it.spent }.thenByDescending { it.budget })
}

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
data class YearSpending(
    val year: Int,
    val months: List<MonthSpending>,
    val inProgress: YearMonth? = null,
    /** Mois écartés des moyennes (voir excludedMonths) : comptés dans le total, pas dans les moyennes. */
    val excluded: Set<YearMonth> = emptySet(),
) {
    val tracked: List<MonthSpending> get() = months.filter { it.tracked }

    /** Mois renseignés, terminés et comptés : la base des moyennes et des écarts. */
    val complete: List<MonthSpending> get() = tracked.filter { it.month != inProgress && it.month !in excluded }

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
    excluded: Set<YearMonth> = emptySet(),
    parents: Map<String, String> = emptyMap(),
    transfers: List<Transfer> = emptyList(),
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
            parents,
            transfers,
            trackingStart,
        )
    }
    return YearSpending(year, months, inProgress = current.takeIf { it.year == year }, excluded = excluded)
}
