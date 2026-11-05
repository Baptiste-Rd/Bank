package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.bank.normalizeLabel
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.money.sumOfMoney
import java.time.LocalDate
import java.time.YearMonth

/*
 * Le salaire lu à la banque, au lieu d'un montant tapé à la main.
 *
 * L'app repère les virements reçus qui reviennent chaque mois du même émetteur ; on confirme une
 * fois lequel est le salaire. Le revenu d'un mois est alors le salaire réellement reçu ; tant qu'il
 * n'est pas arrivé, celui du dernier versement, marqué « attendu ». Un salaire versé en fin de mois
 * (après le 20) finance le mois suivant : celui du 28 octobre est le revenu de novembre.
 */

/** Un virement reçu : libellé, jour, montant (positif). */
data class Inflow(val label: String, val date: LocalDate, val amount: Money)

/** Un salaire possible : un même émetteur, chaque mois. */
data class SalaryCandidate(
    /** Libellé du dernier versement, tel qu'affiché. */
    val label: String,
    /** Clé de reconnaissance : le libellé sans chiffres ni noms de mois (« VIR SEPA RECU SOCIETE X SALAIRE »). */
    val key: String,
    val lastAmount: Money,
    val lastDate: LocalDate,
    /** Mois distincts où il apparaît. */
    val months: Int,
    /** Jour habituel du versement. */
    val typicalDay: Int,
) {
    /** Versé après le 20 : il finance le mois suivant. */
    val paysNextMonth: Boolean get() = typicalDay >= 20
}

/** Revenu d'un mois : reçu, ou attendu d'après le dernier versement. */
data class MonthIncome(val amount: Money, val received: Boolean)

private val monthWords = Regex(
    """\b(JANV(IER)?|FEVR?(IER)?|MARS|AVR(IL)?|MAI|JUIN|JUIL(LET)?|AOUT|SEPT(EMBRE)?|OCT(OBRE)?|NOV(EMBRE)?|DEC(EMBRE)?)\b""",
)

/** Clé d'un virement reçu : le même émetteur donne la même clé d'un mois à l'autre. */
fun salaryKey(label: String): String =
    normalizeLabel(label)
        .replace(monthWords, " ")
        .replace(Regex("""\d+"""), " ")
        .replace(Regex("""[^A-Z ]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

/** Montant minimal d'un salaire : en dessous, un virement reçu est un remboursement. */
private val MIN_SALARY = Money.euros(300)

/** Salaires possibles parmi [inflows] : au moins deux mois distincts, le plus gros d'abord. */
fun salaryCandidates(inflows: List<Inflow>): List<SalaryCandidate> =
    inflows
        .filter { it.amount >= MIN_SALARY }
        .groupBy { salaryKey(it.label) }
        .filterKeys { it.isNotBlank() }
        .mapNotNull { (key, items) ->
            val months = items.map { YearMonth.from(it.date) }.distinct().size
            if (months < 2) return@mapNotNull null
            val last = items.maxBy { it.date }
            val days = items.map { it.date.dayOfMonth }.sorted()
            SalaryCandidate(last.label, key, last.amount, last.date, months, days[days.size / 2])
        }
        .sortedByDescending { it.lastAmount }

/**
 * Revenu de [month] : les versements reconnus par [key] (du mois précédent si [paysNextMonth]).
 * Pas encore reçu : le dernier versement connu, attendu. Rien avant (premier mois suivi, dont le
 * salaire est tombé avant le début des données) : le premier versement connu après. Aucun : null.
 */
fun monthIncome(month: YearMonth, inflows: List<Inflow>, key: String, paysNextMonth: Boolean): MonthIncome? {
    val payments = inflows.filter { salaryKey(it.label) == key }
    val source = if (paysNextMonth) month.minusMonths(1) else month
    val received = payments.filter { YearMonth.from(it.date) == source }
    if (received.isNotEmpty()) return MonthIncome(received.sumOfMoney { it.amount }, received = true)
    val nearest = payments.filter { YearMonth.from(it.date) < source }.maxByOrNull { it.date }
        ?: payments.filter { YearMonth.from(it.date) > source }.minByOrNull { it.date }
        ?: return null
    return MonthIncome(nearest.amount, received = false)
}
