package fr.baptiste.bank.data

import fr.baptiste.bank.domain.bank.BankOperation
import fr.baptiste.bank.domain.budget.plannedPurchase
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.AccountType
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
        repository.moveCategory("Bars", "Nourriture")
        // Une vraie enveloppe dans une autre : Cinéma dans Sorties, qui la couvre (80 € dont 30 €).
        val all = repository.envelopes.first()
        val cinema = all.firstOrNull { it.name == "Cinéma" }
        val outings = all.firstOrNull { it.name == "Sorties" }
        if (cinema != null && outings != null && cinema.parentId != outings.id) {
            repository.moveInTree(cinema.id, fr.baptiste.bank.domain.model.TreeDrop.Into(outings.id))
        }
        // L'installation de la démo n'est pas un changement de l'utilisateur : l'historique part vide.
        database.journalDao().clear()
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
                // Les enveloppes de la démo existent depuis le début de son historique.
                start = today.minusYears(2).withDayOfYear(1),
            )
        listOf(
            // Une catégorie partagée : Courses et Bar sont réunis sous « Nourriture ».
            envelope("Courses", EnvelopeType.DAILY, 320).copy(group = "Nourriture"),
            // Une sous-catégorie, pour montrer l'arbre : Bars dans Nourriture.
            envelope("Bar", EnvelopeType.DAILY, 60).copy(group = "Bars"),
            envelope("Essence", EnvelopeType.DAILY, 120).copy(group = "Transports"),
            envelope("Sorties", EnvelopeType.DAILY, 80).copy(group = "Loisirs"),
            envelope("Cinéma", EnvelopeType.DAILY, 30).copy(group = "Loisirs"),
            envelope("Coiffeur", EnvelopeType.RECURRING, 30, Frequency(6, Scale.WEEK)).copy(group = "Santé"),
            envelope("Abonnements", EnvelopeType.FIXED, 45).copy(group = "Abonnements"),
            envelope("Prêt", EnvelopeType.FIXED, 400).copy(group = "Logement"),
            envelope("Projets", EnvelopeType.ANNUAL, 1200),
            envelope("Vacances", EnvelopeType.ANNUAL, 1800).copy(group = "Voyages"),
            // Une somme mise à part au début du mois, prise sur le compte : elle ne pèse pas sur le « par jour ».
            envelope("Week-end à Lisbonne", EnvelopeType.LUMP, 600).copy(start = today.withDayOfMonth(1), anchor = today.withDayOfMonth(1), repeats = false),
            envelope("Précaution", EnvelopeType.SAVINGS, 200).copy(goal = Money.euros(6000)),
            envelope("Voiture", EnvelopeType.SAVINGS, 540).copy(goal = Money.euros(8000), goalBy = YearMonth.from(today).plusMonths(14)),
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
        // Un concert en début de mois : Sorties dépasse, pour montrer « Ajouter de l'argent ».
        spend("Sorties", 1, 9500L, "Concert")
        spend("Sorties", 8, 2400L, "Cinéma")
        spend("Sorties", 20, 3650L, "Restaurant")
        spend("Bar", 16, 1800L, "Bar du coin")
        spend("Projets", 5, 10000L, "Vélo")
        spend("Week-end à Lisbonne", 1, 21000L, "TAP AIR PORTUGAL")
        spend("Week-end à Lisbonne", 2, 18500L, "HOTEL LISBOA")
        spend("Coiffeur", maxOf(1, today.dayOfMonth - 10), 3000L)
        // Les charges fixes sont payées chaque mois : sans elles, leur montant passerait pour un reste.
        spend("Prêt", 5, 40000L, "Échéance prêt")
        spend("Abonnements", 10, 4200L, "Abonnements")

        // Cinq mois d'historique, pour l'épargne mois par mois : le quotidien varie d'un mois à l'autre.
        val monthlyDaily = listOf(62000L, 71000L, 58000L, 66000L, 69000L)
        monthlyDaily.forEachIndexed { index, total ->
            val month = YearMonth.from(today).minusMonths(index + 1L)
            val split = listOf("Courses" to 0.62, "Essence" to 0.18, "Sorties" to 0.20)
            repository.addManualExpense(Money.euros(400), month.atDay(5), "Échéance prêt", byName.getValue("Prêt"))
            repository.addManualExpense(Money(4200), month.atDay(10), "Abonnements", byName.getValue("Abonnements"))
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
        // Le mois dernier, le même supermarché a été rangé dans Courses : l'app le propose pour celui d'hier.
        val lastMonth = today.minusMonths(1)
        repository.importBankOperations(
            accountUid = "demo",
            accountName = "Compte courant ••1234",
            ibanSuffix = "1234",
            operations = listOf(
                BankOperation("demo-0", lastMonth, -3870, "PAIEMENT CB 1209 PARIS CARREFOUR CITY CARTE 4970XXXXXXXX1234"),
            ),
        )
        repository.recentOperations().first()
            .firstOrNull { it.bankLabel.contains("Carrefour", ignoreCase = true) }
            ?.let { repository.sortOperation(it.id, byName.getValue("Courses"), ruleKeyword = null) }
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
        // Autres comptes : un livret alimenté chaque mois, et un compte d'investissement saisi à la main.
        val livret = repository.addManualAccount("Livret A", AccountType.SAVINGS, Money.euros(5600))
        listOf(5L to 4850L, 4L to 5000L, 3L to 5150L, 2L to 5300L, 1L to 5450L).forEach { (back, euros) ->
            repository.updateBalance(livret, Money.euros(euros), today.minusMonths(back).withDayOfMonth(3))
        }
        val trading = repository.addManualAccount("Trade Republic", AccountType.INVESTMENT, Money.euros(3120))
        repository.updateBalance(trading, Money.euros(3000), today.minusMonths(1).withDayOfMonth(15))
        // Montant versé et enveloppe fiscale : l'app en estime le net après impôt.
        repository.setInvestment(trading, Money.euros(3000), fr.baptiste.bank.domain.budget.TaxWrapper.SECURITIES)
        // Objectifs : Voiture versé sur le livret, et les livrets répartis entre les deux.
        val goals = repository.envelopes.first().associateBy { it.name }
        repository.saveEnvelope(goals.getValue("Voiture").copy(accountId = livret))
        repository.placeSavings(mapOf(goals.getValue("Précaution").id to Money.euros(3000), goals.getValue("Voiture").id to Money.euros(2600)), start = true)
        // Versement de 100 € par mois à faire soi-même : l'accueil le rappelle.
        repository.setMonthlyTarget(trading, Money.euros(100))
        val pea = repository.addManualAccount("PEA", AccountType.INVESTMENT, Money.euros(2400))
        repository.setInvestment(pea, Money.euros(2000), fr.baptiste.bank.domain.budget.TaxWrapper.PEA_OVER_5)
        // Le solde du compte courant, tel que la banque l'afficherait : il contient une réserve (mois passés).
        repository.setCurrentAccountBalance(Money.euros(3450))
        // Le virement du mois vers le livret : reconnu, il n'est pas une dépense.
        repository.importBankOperations(
            accountUid = "demo",
            accountName = "Compte courant ••1234",
            ibanSuffix = "1234",
            operations = listOf(
                BankOperation("demo-5", today.withDayOfMonth(minOf(3, today.dayOfMonth)), -15000, "VIR SEPA VERS LIVRET A"),
            ),
        )
        repository.recentOperations().first()
            .firstOrNull { it.bankLabel.contains("Livret", ignoreCase = true) }
            ?.let { repository.markTransferToAccount(it.id, livret, keyword = "Livret A") }

        // Un alias : le prélèvement s'affiche sous un nom lisible.
        repository.recentOperations().first()
            .firstOrNull { it.bankLabel.contains("Free Mobile", ignoreCase = true) }
            ?.let { repository.renameOperation(it.id, "Forfait mobile", pattern = "Free Mobile") }
    }
}
