package fr.baptiste.bank.domain.bank

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/*
 * « Vérifier les dates » : comment la banque date tes opérations, et lesquelles l'app a mal datées.
 *
 * Pour chaque opération bancaire, l'app garde le jour retenu et, depuis la version 12 de la base,
 * le jour de comptabilisation et celui que la banque donne pour le paiement. Le diagnostic dit
 * d'où vient chaque jour (banque, libellé, comptabilisation), si la carte semble à débit immédiat
 * ou différé (délai entre achat et comptabilisation), et propose les jours à corriger : rien ne
 * change sans validation.
 */

/** Une opération bancaire telle qu'enregistrée. [bookingDate] et [bankDate] : null si inconnus. */
data class DatedOperation(
    val id: Long,
    val rawLabel: String,
    val amountCents: Long,
    val date: LocalDate,
    val bookingDate: LocalDate? = null,
    val bankDate: LocalDate? = null,
)

/** Débit de la carte, déduit des délais observés. */
enum class CardDebit { IMMEDIATE, DEFERRED, UNKNOWN }

/** Une opération, le jour que la règle actuelle lui donnerait et son origine. */
data class DateCheckLine(val operation: DatedOperation, val detail: DateDetail) {
    /** Jours entre le paiement et la comptabilisation (null : comptabilisation inconnue). */
    val delayDays: Long? get() = operation.bookingDate?.let { ChronoUnit.DAYS.between(detail.date, it) }
    val needsCorrection: Boolean get() = detail.date != operation.date
}

/** Un jour à corriger : l'ancien, le nouveau. */
data class DateCorrection(val operation: DatedOperation, val newDate: LocalDate) {
    val oldDate: LocalDate get() = operation.date

    /** La dépense change de mois : elle quittera le budget de l'un pour celui de l'autre. */
    val changesMonth: Boolean get() = YearMonth.from(oldDate) != YearMonth.from(newDate)
}

data class DateReport(
    /** Les opérations, la plus récente en premier. */
    val lines: List<DateCheckLine>,
    val bySource: Map<DateSource, Int>,
    /** Paiements par carte dont le délai est connu. */
    val cardCount: Int,
    /** Délai médian et maximal (jours) entre achat et comptabilisation des paiements par carte. */
    val medianCardDelay: Long?,
    val maxCardDelay: Long?,
    val debit: CardDebit,
    /** Lignes qui ressemblent au prélèvement global de la carte : à classer hors budget si les achats sont aussi détaillés. */
    val cardStatements: List<DatedOperation>,
    val corrections: List<DateCorrection>,
)

/** Nombre minimal de paiements par carte pour conclure sur le type de débit. */
private const val MIN_CARD_SAMPLE = 5

private val cardStatementLabel = Regex(
    """\b(?:FACTURE|FACT|RELEVE|TOTAL|PRELEVEMENT|DEBIT)\b.*\b(?:CARTE|CB)\b|\bCARTE\b.*\bDIFFERE|\bENCOURS\b""",
    RegexOption.IGNORE_CASE,
)

/** Le prélèvement global d'une carte à débit différé (« FACTURE CARTE DU 20/10 »). */
fun isCardStatement(rawLabel: String): Boolean = cardStatementLabel.containsMatchIn(rawLabel)

/**
 * Diagnostic des dates de [operations] (opérations bancaires, pas les saisies). Le jour proposé
 * applique la règle actuelle au jour de comptabilisation connu, à défaut au jour enregistré.
 */
fun dateReport(operations: List<DatedOperation>): DateReport {
    val lines = operations
        .map { op ->
            val detail = operationDateDetail(op.rawLabel, op.bookingDate ?: op.date, op.bankDate)
            DateCheckLine(op, detail)
        }
        .sortedWith(compareByDescending<DateCheckLine> { it.operation.date }.thenByDescending { it.operation.id })
    val statements = operations.filter { isCardStatement(it.rawLabel) }
    val cardDelays = lines
        .filter { isCardLabel(it.operation.rawLabel) && !isCardStatement(it.operation.rawLabel) && it.detail.source != DateSource.BOOKING }
        .mapNotNull { it.delayDays }
        .sorted()
    val median = if (cardDelays.isEmpty()) null else cardDelays[cardDelays.size / 2]
    val debit = when {
        cardDelays.size < MIN_CARD_SAMPLE || median == null -> CardDebit.UNKNOWN
        median <= 5 -> CardDebit.IMMEDIATE
        median >= 8 -> CardDebit.DEFERRED
        else -> CardDebit.UNKNOWN
    }
    return DateReport(
        lines = lines,
        bySource = lines.groupingBy { it.detail.source }.eachCount(),
        cardCount = cardDelays.size,
        medianCardDelay = median,
        maxCardDelay = cardDelays.lastOrNull(),
        debit = debit,
        cardStatements = statements,
        corrections = lines.filter { it.needsCorrection }.map { DateCorrection(it.operation, it.detail.date) },
    )
}
