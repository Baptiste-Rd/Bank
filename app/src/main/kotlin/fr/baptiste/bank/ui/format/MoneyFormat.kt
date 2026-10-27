package fr.baptiste.bank.ui.format

import fr.baptiste.bank.domain.money.Money
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val euroFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE).apply {
    currency = Currency.getInstance("EUR")
}

/** Affiche un montant au format français : « 24,28 € ». */
fun Money.formatted(): String = euroFormat.format(cents / 100.0)
