package fr.baptiste.bank.data

import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import fr.baptiste.bank.domain.period.Scale
import androidx.room.withTransaction
import fr.baptiste.bank.data.local.BankDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Données d'exemple du mode démo : un mois type, avec des courses régulières, une enveloppe
 * Essence qui dérape (pour montrer une alerte), un achat de projet et un coiffeur toutes les
 * 6 semaines. Elles vont dans la base de démo, jamais dans la vraie.
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
            envelope("Épargne", EnvelopeType.SAVINGS, 300),
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
    }
}
