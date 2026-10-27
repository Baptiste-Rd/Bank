package fr.baptiste.bank.domain.money

/**
 * Montant en centimes d'euro, stocké sous forme d'entier.
 *
 * Les nombres à virgule flottante ne représentent pas exactement les décimales
 * (0,1 + 0,2 != 0,3) : tous les calculs se font donc en centimes entiers.
 * Les divisions précisent toujours leur sens d'arrondi.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(cents, other.cents))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(cents, other.cents))

    operator fun unaryMinus(): Money = Money(Math.negateExact(cents))

    operator fun times(factor: Long): Money = Money(Math.multiplyExact(cents, factor))

    operator fun times(factor: Int): Money = times(factor.toLong())

    /** Division arrondie au centime inférieur (vers moins l'infini). */
    fun divideFloor(divisor: Long): Money {
        require(divisor > 0) { "Le diviseur doit être strictement positif : $divisor" }
        return Money(Math.floorDiv(cents, divisor))
    }

    /** Division arrondie au centime supérieur (vers plus l'infini). */
    fun divideCeil(divisor: Long): Money {
        require(divisor > 0) { "Le diviseur doit être strictement positif : $divisor" }
        return Money(-Math.floorDiv(-cents, divisor))
    }

    val isNegative: Boolean get() = cents < 0

    val isPositive: Boolean get() = cents > 0

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    override fun toString(): String {
        val sign = if (cents < 0) "-" else ""
        val absolute = Math.abs(cents)
        return "$sign${absolute / 100},${(absolute % 100).toString().padStart(2, '0')} €"
    }

    companion object {
        val ZERO = Money(0)

        fun euros(euros: Long, cents: Int = 0): Money {
            require(cents in 0..99) { "Les centimes doivent être entre 0 et 99 : $cents" }
            val sign = if (euros < 0) -1 else 1
            return Money(euros * 100 + sign * cents)
        }
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { total, amount -> total + amount }

fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money =
    fold(Money.ZERO) { total, item -> total + selector(item) }
