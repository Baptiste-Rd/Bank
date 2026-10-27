package fr.baptiste.bank.data

import androidx.room.withTransaction
import fr.baptiste.bank.data.local.AccountEntity
import fr.baptiste.bank.data.local.BankDatabase
import fr.baptiste.bank.data.local.EnvelopeEntity
import fr.baptiste.bank.data.local.ImputationEntity
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.local.TransactionEntity
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.Spending
import fr.baptiste.bank.domain.budget.computeDashboard
import fr.baptiste.bank.domain.budget.dashboardDateRange
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.ImputationMode
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Point d'accès unique aux données du budget pour les écrans.
 * Il traduit les entités Room en objets du domaine, et délègue tous les calculs au domaine.
 */
class BudgetRepository(
    private val database: BankDatabase,
    private val settings: SettingsRepository,
) {
    private val envelopeDao = database.envelopeDao()
    private val imputationDao = database.imputationDao()
    private val transactionDao = database.transactionDao()

    val envelopes: Flow<List<Envelope>> = envelopeDao.observeActive()
        .map { entities -> entities.map { it.toDomain() } }

    val monthlyIncome: Flow<Money?> = settings.monthlyIncome

    /** Tableau de bord recalculé à chaque changement d'enveloppe, de dépense, d'allocation ou de salaire. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun dashboard(today: LocalDate = LocalDate.now()): Flow<Dashboard> {
        val allocations = database.allocationDao().observeAll()
            .map { rows -> rows.associate { (it.envelopeId to it.periodKey) to Money(it.amountCents) } }
        // La plage de dépenses à charger dépend des périodes des enveloppes (une période de
        // 3 mois peut commencer avant l'année en cours) : elle est recalculée quand elles changent.
        return envelopes.flatMapLatest { envelopes ->
            val range = dashboardDateRange(today, envelopes)
            val spendings = imputationDao.observeSpendings(range.start, range.endInclusive)
                .map { rows -> rows.map { Spending(it.envelopeId, it.date, Money(it.amountCents), it.wholePeriod) } }
            combine(allocations, spendings, settings.monthlyIncome) { allocs, spent, income ->
                computeDashboard(envelopes, allocs, spent, income, today)
            }
        }
    }

    fun recentOperations(limit: Int = 200): Flow<List<OperationRow>> =
        transactionDao.observeRecentOperations(limit)

    suspend fun saveEnvelope(envelope: Envelope) {
        envelopeDao.upsert(envelope.toEntity())
    }

    suspend fun archiveEnvelope(id: Long) {
        envelopeDao.archive(id)
    }

    suspend fun setMonthlyIncome(income: Money) {
        settings.setMonthlyIncome(income)
    }

    /**
     * Enregistre une dépense saisie à la main et l'impute sur une enveloppe.
     * La transaction porte un montant négatif (sortie d'argent), l'imputation un montant
     * positif (ce qui est consommé dans l'enveloppe), sur la période de l'enveloppe à cette date.
     *
     * @param wholePeriod rattrapage : la dépense couvre toute la période de l'enveloppe contenant [date].
     */
    suspend fun addManualExpense(
        amount: Money,
        date: LocalDate,
        label: String,
        envelope: Envelope,
        wholePeriod: Boolean = false,
    ) {
        require(amount.isPositive) { "Une dépense doit avoir un montant positif" }
        database.withTransaction {
            val transactionId = transactionDao.insert(
                TransactionEntity(
                    accountId = currentAccountId(),
                    date = date,
                    amountCents = -amount.cents,
                    rawLabel = label,
                    cleanLabel = label,
                    source = TransactionSource.MANUAL,
                ),
            )
            imputationDao.insert(
                ImputationEntity(
                    transactionId = transactionId,
                    envelopeId = envelope.id,
                    periodKey = envelope.periodContaining(date).key,
                    amountCents = amount.cents,
                    mode = ImputationMode.MANUAL,
                    wholePeriod = wholePeriod,
                ),
            )
        }
    }

    /** Supprime une saisie manuelle. Les opérations importées de la banque ne se suppriment pas. */
    suspend fun deleteManualExpense(transactionId: Long) {
        database.withTransaction {
            imputationDao.deleteForTransaction(transactionId)
            transactionDao.deleteManual(transactionId)
        }
    }

    /** Compte courant auquel rattacher les saisies ; créé au premier besoin. */
    private suspend fun currentAccountId(): Long {
        val accountDao = database.accountDao()
        return accountDao.currentAccountId()
            ?: accountDao.insert(AccountEntity(name = "Compte courant", type = AccountType.CURRENT))
    }
}

private fun EnvelopeEntity.toDomain() = Envelope(
    id = id,
    name = name,
    type = type,
    frequency = Frequency(every, scale),
    amount = Money(amountCents),
    status = status,
    priority = priority,
    carryOver = carryOver,
    archived = archived,
    anchor = anchorDate,
)

private fun Envelope.toEntity() = EnvelopeEntity(
    id = id,
    name = name,
    type = type,
    scale = frequency.scale,
    amountCents = amount.cents,
    status = status,
    priority = priority,
    carryOver = carryOver,
    archived = archived,
    every = frequency.every,
    anchorDate = anchor,
)
