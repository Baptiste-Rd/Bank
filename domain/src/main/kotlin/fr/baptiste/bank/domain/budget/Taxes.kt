package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.money.Money

/*
 * Net estimé d'un placement : sa valeur moins l'impôt sur la plus-value si tout était retiré
 * aujourd'hui. Une estimation au niveau du compte : elle ignore les moins-values reportées, l'option
 * pour le barème progressif et les prélèvements déjà payés en cours de route (fonds en euros).
 *
 * Taux 2026 (loi de financement de la Sécurité sociale pour 2026) :
 * - compte-titres et PEA de moins de 5 ans : flat tax de 31,4 % (12,8 % d'impôt + 18,6 % de
 *   prélèvements sociaux) ;
 * - PEA de plus de 5 ans : prélèvements sociaux seuls, 18,6 % ;
 * - assurance-vie, épargnée par la hausse : 17,2 % de prélèvements sociaux, plus 12,8 % d'impôt
 *   avant 8 ans, ou 7,5 % au-delà de l'abattement annuel de 4 600 € (personne seule) après 8 ans.
 */

enum class TaxWrapper {
    /** Compte-titres ordinaire. */
    SECURITIES,
    PEA_UNDER_5,
    PEA_OVER_5,
    LIFE_INSURANCE_UNDER_8,
    LIFE_INSURANCE_OVER_8,
}

data class NetEstimate(val value: Money, val invested: Money, val tax: Money) {
    /** Plus-value (négative : moins-value). */
    val gain: Money get() = value - invested
    val net: Money get() = value - tax
}

private const val LIFE_INSURANCE_ALLOWANCE_CENTS = 460_000L

/** [cents] × [basisPoints] / 10 000, arrondi au centime le plus proche. */
private fun Money.rate(basisPoints: Long): Money = Money(Math.floorDiv(cents * basisPoints + 5_000, 10_000))

/** Impôt estimé sur la plus-value [gain] dans l'enveloppe [wrapper] (zéro pour une moins-value). */
fun TaxWrapper.taxOn(gain: Money): Money {
    if (!gain.isPositive) return Money.ZERO
    return when (this) {
        TaxWrapper.SECURITIES, TaxWrapper.PEA_UNDER_5 -> gain.rate(3_140)
        TaxWrapper.PEA_OVER_5 -> gain.rate(1_860)
        TaxWrapper.LIFE_INSURANCE_UNDER_8 -> gain.rate(3_000)
        TaxWrapper.LIFE_INSURANCE_OVER_8 -> {
            val taxable = Money(maxOf(0L, gain.cents - LIFE_INSURANCE_ALLOWANCE_CENTS))
            gain.rate(1_720) + taxable.rate(750)
        }
    }
}

/** Net estimé, ou null sans montant investi ou sans enveloppe fiscale connue. */
fun netEstimate(wrapper: TaxWrapper?, value: Money, invested: Money?): NetEstimate? {
    if (wrapper == null || invested == null) return null
    return NetEstimate(value, invested, wrapper.taxOn(value - invested))
}
