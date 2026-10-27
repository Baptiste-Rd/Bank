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
enum class Scale(val periodsPerYear: Long, internal val letter: Char) {
    DAY(365, 'D'),
    WEEK(52, 'W'),
    MONTH(12, 'M'),
    YEAR(1, 'Y');

    fun periodContaining(date: LocalDate): Period = when (this) {
        DAY -> Period.Span(date, date, Frequency(1, DAY))
        WEEK -> Period.Week.of(date)
        MONTH -> Period.Month(YearMonth.from(date))
        YEAR -> Period.Year(date.year)
    }

    /** Ajoute [count] unités de cette échelle à une date. */
    fun add(date: LocalDate, count: Long): LocalDate = when (this) {
        DAY -> date.plusDays(count)
        WEEK -> date.plusWeeks(count)
        MONTH -> date.plusMonths(count)
        YEAR -> date.plusYears(count)
    }
}

/**
 * Fréquence d'une enveloppe : « tous les [every] [scale] », par exemple tous les 6 semaines.
 *
 * Avec every = 1 et une échelle semaine, mois ou an, les périodes suivent le calendrier
 * (semaine ISO, mois civil, année civile). Sinon, elles sont glissantes : elles s'enchaînent
 * à partir d'une date de départ (l'ancre), sans lien avec le calendrier.
 */
data class Frequency(val every: Int, val scale: Scale) {
    init {
        require(every >= 1) { "Une fréquence porte sur au moins une unité : $every" }
    }

    val isCalendar: Boolean get() = every == 1 && scale != Scale.DAY

    /**
     * Période qui contient [date]. [anchor] fixe le début d'une période glissante ;
     * il est ignoré pour une période calendaire.
     */
    fun periodContaining(date: LocalDate, anchor: LocalDate): Period {
        if (isCalendar || (every == 1 && scale == Scale.DAY)) return scale.periodContaining(date)
        // Chaque début est recalculé depuis l'ancre (et non depuis le début précédent) :
        // une ancre au 31 donne bien 31 janvier, 28 février, 31 mars...
        var index = when (scale) {
            Scale.DAY -> Math.floorDiv(ChronoUnit.DAYS.between(anchor, date), every.toLong())
            Scale.WEEK -> Math.floorDiv(ChronoUnit.DAYS.between(anchor, date), 7L * every)
            Scale.MONTH -> Math.floorDiv(ChronoUnit.MONTHS.between(anchor, date), every.toLong())
            Scale.YEAR -> Math.floorDiv(ChronoUnit.YEARS.between(anchor, date), every.toLong())
        }
        while (startOf(anchor, index) > date) index--
        while (startOf(anchor, index + 1) <= date) index++
        return Period.Span(startOf(anchor, index), startOf(anchor, index + 1).minusDays(1), this)
    }

    private fun startOf(anchor: LocalDate, index: Long): LocalDate = scale.add(anchor, index * every)

    companion object {
        val MONTHLY = Frequency(1, Scale.MONTH)
        val YEARLY = Frequency(1, Scale.YEAR)
        val WEEKLY = Frequency(1, Scale.WEEK)
    }
}

/**
 * Période concrète, identifiée par une clé textuelle stable :
 * "2026-10" (mois), "2026" (année), "2026-W43" (semaine ISO),
 * "2026-10-12+6W" (période glissante de 6 semaines commençant le 12 octobre).
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

    /** Période glissante (ou journée seule), de [start] à [endInclusive]. */
    data class Span(
        override val start: LocalDate,
        override val endInclusive: LocalDate,
        val frequency: Frequency,
    ) : Period {
        override val key: String get() = "$start+${frequency.every}${frequency.scale.letter}"
        override val scale: Scale get() = frequency.scale
    }

    companion object {
        private val spanKey = Regex("""^(\d{4}-\d{2}-\d{2})\+(\d+)([DWMY])$""")
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
            spanKey.matchEntire(key)?.let { match ->
                val (start, every, letter) = match.destructured
                val scale = Scale.entries.first { it.letter == letter.single() }
                val startDate = LocalDate.parse(start)
                val frequency = Frequency(every.toInt(), scale)
                return Span(startDate, scale.add(startDate, every.toLong()).minusDays(1), frequency)
            }
            throw IllegalArgumentException("Clé de période invalide : $key")
        }
    }
}
