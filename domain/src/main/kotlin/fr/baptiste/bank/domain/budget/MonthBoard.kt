package fr.baptiste.bank.domain.budget

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth

/*
 * Le tableau d'un jour, quel que soit le mois : une seule fonction pour le Résumé, le Bilan d'un
 * mois terminé, l'épargne mois par mois et l'année. Chaque date reçoit le salaire de son mois, les
 * enveloppes qui existaient alors (retirées depuis comprises) et l'habitude de dépense des douze
 * mois qui la précèdent. Deux écrans qui montrent le même mois montrent donc les mêmes chiffres.
 */

/**
 * Tout ce qu'il faut pour le tableau d'un jour.
 *
 * @property envelopes actives et retirées (avec leur date de retrait).
 * @property spendings dépenses de la plage [boardLoadRange] : les périodes des dates voulues et les
 *   douze mois qui les précèdent (habitudes).
 * @property inflows virements reçus de la même plage, un mois plus tôt compris (salaire versé la
 *   veille du mois qu'il finance).
 * @property salary clé du salaire confirmé et s'il finance le mois suivant ; null : [typedIncome].
 * @property typedIncome revenu tapé à la main autrefois, avant la lecture du salaire à la banque.
 */
data class BudgetData(
    val envelopes: List<Envelope>,
    val allocations: Map<Pair<Long, String>, Money>,
    val spendings: List<Spending>,
    val transfers: List<Transfer>,
    val inflows: List<Inflow>,
    val salary: Pair<String, Boolean>?,
    val typedIncome: Money?,
    val trackingStart: YearMonth?,
    val excluded: Set<YearMonth> = emptySet(),
)

/** Revenu de [month] : le salaire versé pour ce mois-là, pas celui d'aujourd'hui. */
fun BudgetData.incomeOf(month: YearMonth): Money? =
    if (salary != null) monthIncome(month, inflows, salary.first, salary.second)?.amount else typedIncome

/** Le tableau de [date] : salaire de son mois, enveloppes de ce jour-là, habitudes d'avant. */
fun dashboardAt(data: BudgetData, date: LocalDate): Dashboard = computeDashboard(
    envelopes = data.envelopes,
    allocations = data.allocations,
    spendings = data.spendings,
    monthlyIncome = data.incomeOf(YearMonth.from(date)),
    today = date,
    transfers = data.transfers,
    trackingStart = data.trackingStart,
    habits = dailyHabits(data.envelopes, data.spendings, date, data.trackingStart, data.excluded),
)

/** L'épargne d'un mois : celle du tableau de son dernier jour (ou d'aujourd'hui), comptée ou non dans les moyennes. */
data class MonthOutlook(val month: YearMonth, val outlook: SavingsOutlook, val counted: Boolean)

/**
 * Épargne mois par mois, une ligne par date de [dates] (le dernier jour de chaque mois passé, puis
 * aujourd'hui) : le même calcul que le Bilan de chaque mois. Un mois d'avant le début du suivi n'est
 * pas « tout épargné » : il est absent ; le mois [current] reste toujours.
 */
fun savingsByMonth(data: BudgetData, dates: List<LocalDate>, current: YearMonth): List<MonthOutlook> {
    val start = data.trackingStart
    return dates
        .filter { start == null || YearMonth.from(it) >= start || YearMonth.from(it) == current }
        .map { date -> MonthOutlook(YearMonth.from(date), dashboardAt(data, date).savings, YearMonth.from(date) !in data.excluded) }
}

/**
 * Plage de dates à charger pour les tableaux de [dates] : la période de chaque enveloppe à chaque
 * date (voir [dashboardDateRange]) et les douze mois avant la plus ancienne (habitudes).
 */
fun boardLoadRange(dates: List<LocalDate>, envelopes: List<Envelope>): ClosedRange<LocalDate> {
    val ranges = dates.map { dashboardDateRange(it, envelopes) }
    val habitsFrom = YearMonth.from(dates.min()).minusMonths(12).atDay(1)
    return minOf(habitsFrom, ranges.minOf { it.start })..ranges.maxOf { it.endInclusive }
}

/**
 * Plage des virements reçus utiles aux salaires de [dates] : quelques mois plus tôt (un salaire pas
 * encore reçu reprend le dernier versé) et deux mois plus tard (un premier mois suivi reprend le
 * premier versé après lui).
 */
fun incomeLoadRange(dates: List<LocalDate>): ClosedRange<LocalDate> =
    YearMonth.from(dates.min()).minusMonths(4).atDay(1)..YearMonth.from(dates.max()).plusMonths(2).atEndOfMonth()
