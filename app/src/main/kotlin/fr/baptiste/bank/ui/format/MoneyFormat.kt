package fr.baptiste.bank.ui.format

import fr.baptiste.bank.domain.money.Money
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val euroFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE).apply {
    currency = Currency.getInstance("EUR")
}

/**
 * Affiche un montant au format français : « 1 234,56 € », « −12,40 € ».
 *
 * Deux retouches au format standard :
 * - les espaces fines insécables (U+202F) que Java place entre les milliers et avant « € »
 *   deviennent des espaces insécables ordinaires : la police Inter ne leur donne aucune largeur
 *   hors chiffres tabulaires, ce qui collait « 1183,33 » ;
 * - le trait d'union devient un vrai signe moins (U+2212), de la largeur d'un chiffre.
 */
fun Money.formatted(): String = euroFormat.format(cents / 100.0)
    .replace(' ', ' ')
    .replace('-', '−')

private val wholeEuroFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE).apply {
    currency = Currency.getInstance("EUR")
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}

/**
 * Un chiffre de synthèse (solde, total du mois, épargne prévue) : en euros ronds, « 1 027 € ».
 * Une opération garde ses centimes ([formatted]). Sous 10 €, les centimes restent : « 0,50 € »
 * ne doit pas devenir « 1 € ».
 */
fun Money.summary(): String {
    if (kotlin.math.abs(cents) < 1000 && cents % 100 != 0L) return formatted()
    return wholeEuroFormat.format(Math.round(cents / 100.0).toDouble())
        .replace('\u202F', '\u00A0')
        .replace('-', '−')
}

/** « 1 règle », « 3 règles » : jamais « règle(s) ». */
fun plural(count: Int, one: String, many: String): String = "$count ${if (count > 1) many else one}"
