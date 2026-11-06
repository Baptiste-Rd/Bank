package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Period
import fr.baptiste.bank.domain.period.Scale
import java.time.LocalDate

/** Nature d'une enveloppe : détermine comment elle entre dans le calcul du budget quotidien. */
enum class EnvelopeType {
    /** Dépenses courantes pilotées au jour le jour (nourriture, essence, sorties). */
    DAILY,

    /** Dépenses à fréquence connue (coiffeur, abonnements annuels). */
    RECURRING,

    /** Enveloppes consommées irrégulièrement sur l'année (projets, CBD). */
    ANNUAL,

    /** Prélèvements certains (mensualité de prêt, loyer). */
    FIXED,

    /** Objectif d'épargne (virements vers le livret A, le PEA...). */
    SAVINGS,

    /** Achat prévu à une date : son prix est réparti sur les mois qui la précèdent. */
    PLANNED,
    ;

    /** Statut proposé à la création : on ne prélève jamais automatiquement sur un engagement. */
    val defaultStatus: EnvelopeStatus
        get() = when (this) {
            FIXED, SAVINGS, PLANNED -> EnvelopeStatus.PROTECTED
            DAILY, RECURRING, ANNUAL -> EnvelopeStatus.FLEXIBLE
        }

    /** Fréquence proposée à la création. */
    val defaultFrequency: Frequency
        get() = when (this) {
            DAILY, FIXED, SAVINGS -> Frequency.MONTHLY
            RECURRING, ANNUAL -> Frequency.YEARLY
            PLANNED -> Frequency(6, Scale.MONTH)
        }
}

/** Une enveloppe flexible peut servir de source à un transfert ; une enveloppe protégée jamais automatiquement. */
enum class EnvelopeStatus { FLEXIBLE, PROTECTED }

/**
 * Enveloppe budgétaire.
 *
 * [amount] est exprimé pour une période de [frequency] : 300 € par mois pour les courses,
 * 30 € toutes les 6 semaines pour le coiffeur. Le montant réellement alloué pour une
 * période donnée est stocké à part (allocation), ce qui permet de modifier la période en
 * cours sans réécrire le passé.
 */
data class Envelope(
    val id: Long,
    val name: String,
    val type: EnvelopeType,
    val frequency: Frequency,
    val amount: Money,
    val status: EnvelopeStatus,
    /** Ordre de prélèvement parmi les enveloppes flexibles : la plus petite valeur est sollicitée en premier. */
    val priority: Int = 0,
    /** Reliquat de fin de période reporté sur la période suivante (sinon renvoyé au portefeuille). */
    val carryOver: Boolean = false,
    val archived: Boolean = false,
    /**
     * Début d'une des périodes, pour une fréquence glissante (tous les 6 semaines...).
     * Ignoré pour une fréquence calendaire (mois civil, année civile, semaine ISO).
     */
    val anchor: LocalDate? = null,
    /**
     * Groupe d'affichage (« Nourriture » pour « Courses » et « Bar ») : un seul niveau, sans budget
     * propre. Le groupe additionne les budgets et les dépenses de ses enveloppes ; une dépense est
     * toujours imputée à une enveloppe, jamais au groupe, et n'est donc comptée qu'une fois.
     */
    val group: String? = null,
    /**
     * Provision : mois à partir duquel on met de côté. Rien ne l'a été avant ; le montant se
     * répartit sur les mois qui restent jusqu'à la fin de la période (Noël créé en octobre : le
     * montant sur trois mois). Null : la part habituelle (un douzième pour une annuelle).
     */
    val fundingStart: java.time.YearMonth? = null,
    /**
     * Provision en retard : mois à partir duquel on rattrape. Les mois d'avant gardent leur part
     * habituelle (le passé ne se réécrit pas) ; ce qui manque se répartit sur les mois restants,
     * celui-ci compris. Ne vaut que pour la période qui contient ce mois. Null : pas de rattrapage.
     */
    val catchUpFrom: java.time.YearMonth? = null,
    /**
     * Départ : jour à partir duquel l'enveloppe existe. Une dépense antérieure peut y être rangée
     * (elle reste dans les statistiques de son mois) mais ne compte pas dans son reste : un billet
     * payé en août ne vide pas une enveloppe Vacances créée en octobre. Null : depuis toujours
     * (enveloppes créées avant que l'app ne retienne ce jour).
     */
    val start: LocalDate? = null,
    /** Premier montant, s'il diffère (« 26 € jusqu'au 1er janvier, puis 26 € tous les 3 mois »). */
    val firstAmount: Money? = null,
    /**
     * Remise à zéro du premier budget : sa période va du départ à la veille de ce jour, puis les
     * suivantes s'enchaînent selon [frequency] à partir de ce jour. Null : rythme seul ([anchor]).
     */
    val firstReset: LocalDate? = null,
    /** Faux : budget ponctuel, une seule période (vacances de cette année, un achat prévu). */
    val repeats: Boolean = true,
    /** Retirée ce jour : elle n'a plus de période ensuite, mais reste dans les mois qu'elle a couverts. */
    val retiredOn: LocalDate? = null,
    /** Enveloppe qui la contient (voir EnvelopeTree) ; null : en haut de l'arbre. */
    val parentId: Long? = null,
    /** Rang parmi les enveloppes du même parent. */
    val position: Int = 0,
    /**
     * Périodes enregistrées, de la plus ancienne à la plus récente : le passé figé. Une période
     * commencée garde ses bornes et son montant même si l'enveloppe change ensuite.
     */
    val periods: List<BudgetPeriod> = emptyList(),
) {
    val scale: Scale get() = frequency.scale

    /**
     * Une enveloppe existe ce jour-là : après son départ (et son ancre, pour une glissante), avant
     * son retrait, et pendant son unique période si elle est ponctuelle.
     */
    fun existsOn(date: LocalDate): Boolean =
        countsOn(date) &&
            (frequency.isCalendar || anchor == null || !anchor.isAfter(date)) &&
            (retiredOn == null || !date.isAfter(retiredOn)) &&
            (repeats || onlyPeriod()?.let { !date.isAfter(it.endInclusive) } ?: true)

    /** Une dépense de ce jour compte dans l'enveloppe : elle n'est pas antérieure à son départ. */
    fun countsOn(date: LocalDate): Boolean = start == null || !date.isBefore(start)

    /** L'enveloppe a existé pendant au moins un jour de [month] (retirée ou ponctuelle comprise). */
    fun existedDuring(month: java.time.YearMonth): Boolean {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        if (start != null && start.isAfter(last)) return false
        if (!frequency.isCalendar && anchor != null && anchor.isAfter(last)) return false
        if (retiredOn != null && retiredOn.isBefore(first)) return false
        if (!repeats) onlyPeriod()?.let { if (it.endInclusive.isBefore(first)) return false }
        return true
    }

    /** Période de cette enveloppe qui contient [date] : l'enregistrée s'il y en a une, sinon celle du rythme. */
    fun periodContaining(date: LocalDate): Period {
        periods.firstOrNull { date in it.start..it.endInclusive }?.let { return it.toPeriod(frequency.scale) }
        val derived = rulePeriod(date)
        // Après une période figée d'un ancien rythme, la suivante commence le lendemain.
        val before = periods.lastOrNull { it.endInclusive.isBefore(date) }
        if (before != null && !derived.start.isAfter(before.endInclusive)) {
            return Period.Fixed(before.endInclusive.plusDays(1), derived.endInclusive, frequency.scale)
        }
        return derived
    }

    /** Montant d'une période : l'enregistré, sinon le premier montant pour la première, sinon le montant. */
    fun amountIn(period: Period): Money {
        periods.firstOrNull { it.key == period.key }?.let { return it.amount }
        if (firstAmount != null && firstReset != null && period.start == start) return firstAmount
        return amount
    }

    private fun rulePeriod(date: LocalDate): Period {
        val reset = firstReset
        val origin = start
        if (reset != null && origin != null && reset.isAfter(origin)) {
            if (!date.isBefore(origin) && date.isBefore(reset)) return Period.Fixed(origin, reset.minusDays(1), frequency.scale)
            return frequency.periodContaining(date, reset)
        }
        return frequency.periodContaining(date, anchor ?: DEFAULT_ANCHOR)
    }

    /**
     * La seule période d'une enveloppe ponctuelle : du départ à la remise à zéro, sinon celle du
     * rythme qui contient l'ancre (un achat prévu : de sa création au mois de l'achat).
     */
    private fun onlyPeriod(): Period? = (if (firstReset != null) start else anchor ?: start)?.let { periodContaining(it) }

    private companion object {
        /** Ancre de secours, stable, pour une enveloppe glissante créée sans date de départ. */
        val DEFAULT_ANCHOR: LocalDate = LocalDate.of(2026, 1, 5)
    }
}

/** Budget enregistré d'une période : figé une fois la période commencée. */
data class BudgetPeriod(
    val key: String,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val amount: Money,
) {
    /** La période, avec sa clé d'origine (mois, année, glissante, ou bornes fixes). */
    fun toPeriod(scale: Scale): Period =
        runCatching { Period.parse(key) }.getOrNull()?.takeIf { it.start == start && it.endInclusive == endInclusive }
            ?: Period.Fixed(start, endInclusive, scale, key)
}
