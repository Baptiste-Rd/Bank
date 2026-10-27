package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Scale

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
    ;

    /** Statut proposé à la création : on ne prélève jamais automatiquement sur un engagement. */
    val defaultStatus: EnvelopeStatus
        get() = when (this) {
            FIXED, SAVINGS -> EnvelopeStatus.PROTECTED
            DAILY, RECURRING, ANNUAL -> EnvelopeStatus.FLEXIBLE
        }

    /** Échelle proposée à la création. */
    val defaultScale: Scale
        get() = when (this) {
            DAILY, FIXED, SAVINGS -> Scale.MONTH
            RECURRING, ANNUAL -> Scale.YEAR
        }
}

/** Une enveloppe flexible peut servir de source à un transfert ; une enveloppe protégée jamais automatiquement. */
enum class EnvelopeStatus { FLEXIBLE, PROTECTED }

/**
 * Enveloppe budgétaire.
 *
 * [amount] est exprimé à l'échelle naturelle [scale] : 180 € par an pour le coiffeur,
 * 300 € par mois pour la nourriture. Le montant réellement alloué pour une période
 * donnée est stocké à part (allocation), ce qui permet de modifier le mois en cours
 * sans réécrire le passé.
 */
data class Envelope(
    val id: Long,
    val name: String,
    val type: EnvelopeType,
    val scale: Scale,
    val amount: Money,
    val status: EnvelopeStatus,
    /** Ordre de prélèvement parmi les enveloppes flexibles : la plus petite valeur est sollicitée en premier. */
    val priority: Int = 0,
    /** Reliquat de fin de période reporté sur la période suivante (sinon renvoyé au portefeuille). */
    val carryOver: Boolean = false,
    val archived: Boolean = false,
)
