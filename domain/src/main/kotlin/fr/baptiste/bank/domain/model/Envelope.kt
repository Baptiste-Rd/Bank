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
) {
    val scale: Scale get() = frequency.scale

    /**
     * Une enveloppe glissante commence à son ancre : avant, elle n'existait pas encore et ne
     * compte dans aucun calcul (un achat prévu créé en octobre ne pèse pas sur septembre).
     */
    fun existsOn(date: LocalDate): Boolean = frequency.isCalendar || anchor == null || !anchor.isAfter(date)

    /** Période de cette enveloppe qui contient [date]. */
    fun periodContaining(date: LocalDate): Period =
        frequency.periodContaining(date, anchor ?: DEFAULT_ANCHOR)

    private companion object {
        /** Ancre de secours, stable, pour une enveloppe glissante créée sans date de départ. */
        val DEFAULT_ANCHOR: LocalDate = LocalDate.of(2026, 1, 5)
    }
}
