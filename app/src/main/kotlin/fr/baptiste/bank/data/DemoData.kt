package fr.baptiste.bank.data

import fr.baptiste.bank.domain.bank.BankOperation
import fr.baptiste.bank.domain.budget.plannedPurchase
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import androidx.room.withTransaction
import fr.baptiste.bank.data.local.BankDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/**
 * Données d'exemple du mode démo : un mois type, avec des courses régulières, une enveloppe
 * Essence qui dérape (pour montrer une alerte), un achat de projet, un coiffeur toutes les
 * 6 semaines, des budgets annuels, un achat prévu et cinq mois d'historique pour l'épargne. Elles vont dans la base de démo, jamais dans la vraie.
 */
object DemoData {

    /**
     * Remplit la base de démo en une seule transaction : si l'app est arrêtée en cours de route,
     * rien n'est écrit et tout est refait au lancement suivant (pas de démo à moitié remplie).
     */
    suspend fun seedIfEmpty(database: BankDatabase, repository: BudgetRepository, today: LocalDate = LocalDate.now()) {
        repository.setMonthlyIncome(Money.euros(2400))
        database.withTransaction { seed(repository, today) }
    }

    private suspend fun seed(repository: BudgetRepository, today: LocalDate) {
        if (repository.envelopes.first().isNotEmpty()) return

        fun envelope(name: String, type: EnvelopeType, euros: Long, frequency: Frequency = type.defaultFrequency) =
            Envelope(
                id = 0,
                name = name,
                type = type,
                frequency = frequency,
                amount = Money.euros(euros),
                status = type.defaultStatus,
                anchor = if (frequency.isCalendar) null else today.minusDays(20),
            )
        listOf(
            envelope("Courses", EnvelopeType.DAILY, 320),
            envelope("Essence", EnvelopeType.DAILY, 120),
            envelope("Sorties", EnvelopeType.DAILY, 80),
            envelope("Coiffeur", EnvelopeType.RECURRING, 30, Frequency(6, Scale.WEEK)),
            envelope("Abonnements", EnvelopeType.FIXED, 45),
            envelope("Prêt", EnvelopeType.FIXED, 400),
            envelope("Projets", EnvelopeType.ANNUAL, 1200),
            envelope("Vacances", EnvelopeType.ANNUAL, 1800),
            envelope("Épargne", EnvelopeType.SAVINGS, 300),
            // L'achat prévu du scénario : créé ce mois-ci, il ne pèse pas sur les mois passés.
            plannedPurchase("Ordinateur", Money.euros(1200), YearMonth.from(today).plusMonths(5), today),
        ).forEach { repository.saveEnvelope(it) }

        val byName = repository.envelopes.first().associateBy { it.name }
        suspend fun spend(name: String, day: Int, cents: Long, label: String = name) {
            if (day > today.dayOfMonth) return
            val envelope = byName.getValue(name)
            repository.addManualExpense(Money(cents), today.withDayOfMonth(day), label, envelope)
        }

        listOf(3 to 4260L, 6 to 1890L, 9 to 5520L, 12 to 2340L, 15 to 6180L, 19 to 1530L, 22 to 4710L, 25 to 2990L)
            .forEach { (day, cents) -> spend("Courses", day, cents, "Supermarché") }
        spend("Courses", today.dayOfMonth, 1240L, "Boulangerie")
        spend("Essence", 2, 5800L, "Station")
        spend("Essence", 14, 6250L, "Station")
        spend("Sorties", 8, 2400L, "Cinéma")
        spend("Sorties", 20, 3650L, "Restaurant")
        spend("Projets", 5, 10000L, "Vélo")
        spend("Coiffeur", maxOf(1, today.dayOfMonth - 10), 3000L)

        // Cinq mois d'historique, pour l'épargne mois par mois : le quotidien varie d'un mois à l'autre.
        val monthlyDaily = listOf(118000L, 131000L, 96000L, 124000L, 109000L)
        monthlyDaily.forEachIndexed { index, total ->
            val month = YearMonth.from(today).minusMonths(index + 1L)
            val split = listOf("Courses" to 0.62, "Essence" to 0.18, "Sorties" to 0.20)
            split.forEachIndexed { part, (name, share) ->
                repository.addManualExpense(
                    Money((total * share).toLong()),
                    month.atDay(5 + part * 7),
                    if (name == "Courses") "Supermarché" else name,
                    byName.getValue(name),
                )
            }
        }

        // Opérations « reçues de la banque », passées par le vrai chemin d'import :
        // la boulangerie du jour est rapprochée de la saisie manuelle (même montant, même date)
        // et reprend son enveloppe ; les trois autres attendent d'être triées.
        val short = today.format(java.time.format.DateTimeFormatter.ofPattern("ddMM"))
        val yesterday = today.withDayOfMonth(maxOf(1, today.dayOfMonth - 1))
        repository.importBankOperations(
            accountUid = "demo",
            accountName = "Compte courant ••1234",
            ibanSuffix = "1234",
            operations = listOf(
                BankOperation("demo-1", today, -1240, "PAIEMENT CB $short PARIS BOULANGERIE PAUL CARTE 4970XXXXXXXX1234"),
                BankOperation("demo-2", yesterday, -4520, "PAIEMENT CB $short PARIS CARREFOUR CITY CARTE 4970XXXXXXXX1234"),
                BankOperation("demo-3", yesterday, -1999, "PRLV SEPA FREE MOBILE"),
                BankOperation("demo-4", today.withDayOfMonth(1), 240000, "VIR SEPA RECU DE SOCIETE EXEMPLE SALAIRE"),
            ),
        )
    }
}
