package fr.baptiste.bank.ui.components

import fr.baptiste.bank.domain.money.Money

/** Montant de départ d'un champ : vide pour zéro, sinon « 12,50 ». */
internal fun Money.toFieldText(): String =
    if (cents == 0L) "" else "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
