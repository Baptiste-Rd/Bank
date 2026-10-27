package fr.baptiste.bank.domain.money

private val amountPattern = Regex("""^(-)?(\d+)(?:[.,](\d{1,2}))?$""")

/**
 * Lit un montant saisi à la main : « 12 », « 12,5 », « 12.50 », « 1 234,56 ».
 * Les espaces (y compris insécables) et le symbole € sont ignorés.
 * Retourne null si le texte n'est pas un montant valide ou a plus de deux décimales.
 */
fun parseMoney(text: String): Money? {
    val compact = text
        .replace("€", "")
        .filterNot { it.isWhitespace() || it == ' ' || it == ' ' }
    val match = amountPattern.matchEntire(compact) ?: return null
    val (minus, euros, decimals) = match.destructured
    val cents = euros.toLongOrNull()?.let { it * 100 } ?: return null
    val fraction = if (decimals.isEmpty()) 0L else decimals.padEnd(2, '0').toLong()
    val total = cents + fraction
    return Money(if (minus.isEmpty()) total else -total)
}
