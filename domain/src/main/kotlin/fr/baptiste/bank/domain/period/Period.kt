package fr.baptiste.bank.domain.period

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Échelle de temps d'une enveloppe ou d'une vue.
 * [periodsPerYear] sert aux conversions d'un montant d'une échelle à l'autre.
 */
enum class Scale(val periodsPerYear: Long) {
    WEEK(52),
    MONTH(12),
    YEAR(1);

    fun periodContaining(date: LocalDate): Period = when (this) {
        WEEK -> Period.Week.of(date)
        MONTH -> Period.Month(YearMonth.from(date))
        YEAR -> Period.Year(date.year)
    }
}

/**
 * Période calendaire concrète, identifiée par une clé textuelle stable :
 * "2026-10" (mois), "2026" (année), "2026-W43" (semaine ISO).
 * C'est cette clé qui est stockée en base.
 */
sealed interface Period {
    val key: String
    val scale: Scale
    val start: LocalDate
    val endInclusive: LocalDate

    val lengthInDays: Int
        get() = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    /** Nombre de jours restants, la date donnée incluse. 0 si la période est terminée. */
    fun daysRemainingFrom(date: LocalDate): Int = when {
        date < start -> lengthInDays
        date > endInclusive -> 0
        else -> (ChronoUnit.DAYS.between(date, endInclusive) + 1).toInt()
    }

    /** Nombre de jours écoulés, la date donnée incluse. */
    fun daysElapsedAt(date: LocalDate): Int = when {
        date < start -> 0
        date > endInclusive -> lengthInDays
        else -> (ChronoUnit.DAYS.between(start, date) + 1).toInt()
    }

    data class Month(val yearMonth: YearMonth) : Period {
        override val key: String get() = yearMonth.toString()
        override val scale: Scale get() = Scale.MONTH
        override val start: LocalDate get() = yearMonth.atDay(1)
        override val endInclusive: LocalDate get() = yearMonth.atEndOfMonth()
    }

    data class Year(val year: Int) : Period {
        override val key: String get() = year.toString()
        override val scale: Scale get() = Scale.YEAR
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val endInclusive: LocalDate get() = LocalDate.of(year, 12, 31)
    }

    /** Semaine ISO 8601 : du lundi au dimanche, la semaine 1 contient le premier jeudi de l'année. */
    data class Week(val weekBasedYear: Int, val week: Int) : Period {
        init {
            val maxWeek = LocalDate.of(weekBasedYear, 6, 1)
                .with(IsoFields.WEEK_BASED_YEAR, weekBasedYear.toLong())
                .range(IsoFields.WEEK_OF_WEEK_BASED_YEAR).maximum
            require(week in 1..maxWeek) { "Semaine invalide : $weekBasedYear-W$week" }
        }

        override val key: String get() = "$weekBasedYear-W${week.toString().padStart(2, '0')}"
        override val scale: Scale get() = Scale.WEEK
        override val start: LocalDate
            get() = LocalDate.of(weekBasedYear, 6, 1)
                .with(IsoFields.WEEK_BASED_YEAR, weekBasedYear.toLong())
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        override val endInclusive: LocalDate get() = start.plusDays(6)

        companion object {
            fun of(date: LocalDate): Week = Week(
                weekBasedYear = date.get(IsoFields.WEEK_BASED_YEAR),
                week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            )
        }
    }

    companion object {
        private val monthKey = Regex("""^(\d{4})-(\d{2})$""")
        private val yearKey = Regex("""^(\d{4})$""")
        private val weekKey = Regex("""^(\d{4})-W(\d{2})$""")

        fun parse(key: String): Period {
            monthKey.matchEntire(key)?.let { match ->
                val (year, month) = match.destructured
                return Month(YearMonth.of(year.toInt(), month.toInt()))
            }
            yearKey.matchEntire(key)?.let { match ->
                return Year(match.groupValues[1].toInt())
            }
            weekKey.matchEntire(key)?.let { match ->
                val (year, week) = match.destructured
                return Week(year.toInt(), week.toInt())
            }
            throw IllegalArgumentException("Clé de période invalide : $key")
        }
    }
}
