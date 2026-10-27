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
