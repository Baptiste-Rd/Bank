package fr.baptiste.bank.data

import androidx.room.withTransaction
import fr.baptiste.bank.data.local.AccountEntity
import fr.baptiste.bank.data.local.BankDatabase
import fr.baptiste.bank.data.local.EnvelopeEntity
import fr.baptiste.bank.data.local.ImputationEntity
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.local.RuleEntity
import fr.baptiste.bank.data.local.TransactionEntity
import fr.baptiste.bank.data.local.TransferEntity
import fr.baptiste.bank.data.local.TransferRow
import fr.baptiste.bank.domain.bank.BankOperation
import fr.baptiste.bank.domain.bank.LabelRule
import fr.baptiste.bank.domain.bank.ManualEntry
import fr.baptiste.bank.domain.bank.cleanBankLabel
import fr.baptiste.bank.domain.bank.findManualMatch
import fr.baptiste.bank.domain.bank.importKeys
import fr.baptiste.bank.domain.bank.matching
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.SavingsOutlook
import fr.baptiste.bank.domain.budget.Spending
import fr.baptiste.bank.domain.budget.Transfer
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.budget.computeDashboard
import fr.baptiste.bank.domain.budget.dashboardDateRange
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.ImputationMode
import fr.baptiste.bank.domain.model.RuleMatchType
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

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
    private val transferDao = database.transferDao()

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
            val transfers = transferDao.observeActive(range.start, range.endInclusive)
                .map { rows -> rows.map { it.toDomain() } }
            combine(allocations, spendings, transfers, settings.monthlyIncome) { allocs, spent, moves, income ->
                computeDashboard(envelopes, allocs, spent, income, today, transfers = moves)
            }
        }
    }

    /**
     * Épargne des [months] derniers mois, le mois en cours compris (en dernier). Chaque mois passé
     * est calculé comme le tableau de bord de son dernier jour : ses chiffres sont alors réels.
     * Les enveloppes sont celles d'aujourd'hui ; une enveloppe glissante (achat prévu...) ne
     * compte qu'à partir de son début.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun savingsHistory(today: LocalDate = LocalDate.now(), months: Int = 6): Flow<List<MonthSavings>> {
        val allocations = database.allocationDao().observeAll()
            .map { rows -> rows.associate { (it.envelopeId to it.periodKey) to Money(it.amountCents) } }
        val current = YearMonth.from(today)
        val dates = (months - 1 downTo 1).map { current.minusMonths(it.toLong()).atEndOfMonth() } + today
        return envelopes.flatMapLatest { active ->
            val ranges = dates.map { dashboardDateRange(it, active) }
            val from = ranges.minOf { it.start }
            val to = ranges.maxOf { it.endInclusive }
            val spendings = imputationDao.observeSpendings(from, to)
                .map { rows -> rows.map { Spending(it.envelopeId, it.date, Money(it.amountCents), it.wholePeriod) } }
            val transfers = transferDao.observeActive(from, to).map { rows -> rows.map { it.toDomain() } }
            combine(allocations, spendings, transfers, settings.monthlyIncome) { allocs, spent, moves, income ->
                dates.map { date ->
                    MonthSavings(
                        month = YearMonth.from(date),
                        outlook = computeDashboard(active, allocs, spent, income, date, transfers = moves).savings,
                    )
                }
            }
        }
    }

    /**
     * Bilan d'une année : l'épargne de chaque mois (de janvier au mois en cours, ou aux douze pour
     * une année passée), ce qui a été dépensé dans chaque enveloppe sur l'année, et le tableau de
     * bord du dernier jour compté (budgets et provisions annuelles).
     */
    fun yearOverview(year: Int, today: LocalDate = LocalDate.now()): Flow<YearOverview> {
        val end = if (year == today.year) today else LocalDate.of(year, 12, 31)
        val spent = imputationDao.observeSpendings(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            .map { rows ->
                rows.groupBy { it.envelopeId }.mapValues { (_, list) -> Money(list.sumOf { it.amountCents }) }
            }
        return combine(savingsHistory(end, end.monthValue), dashboard(end), spent) { months, board, byEnvelope ->
            YearOverview(year, months, byEnvelope, board, end.monthValue)
        }
    }

    fun transferJournal(limit: Int = 200): Flow<List<TransferRow>> = transferDao.observeJournal(limit)

    /**
     * Enregistre un ou plusieurs transferts datés d'aujourd'hui vers ou depuis [envelopeId].
     * Chaque part indique l'autre extrémité (null = portefeuille). Tout est écrit d'un bloc.
     */
    suspend fun addTransfers(
        legs: List<TransferLeg>,
        envelopeId: Long,
        incoming: Boolean,
        reason: String?,
        date: LocalDate = LocalDate.now(),
    ) {
        val rows = legs.filter { it.amount.isPositive }.map { leg ->
            TransferEntity(
                effectiveDate = date,
                sourceEnvelopeId = if (incoming) leg.sourceEnvelopeId else envelopeId,
                targetEnvelopeId = if (incoming) envelopeId else leg.sourceEnvelopeId,
                amountCents = leg.amount.cents,
                reason = reason?.takeIf { it.isNotBlank() },
            )
        }
        if (rows.isNotEmpty()) transferDao.insertAll(rows)
    }

    suspend fun cancelTransfer(id: Long) {
        transferDao.cancel(id)
    }

    fun recentOperations(limit: Int = 200): Flow<List<OperationRow>> =
        transactionDao.observeRecentOperations(limit)

    /** Crée ou met à jour une enveloppe ; renvoie son identifiant. */
    suspend fun saveEnvelope(envelope: Envelope): Long {
        val rowId = envelopeDao.upsert(envelope.toEntity())
        // Upsert renvoie -1 pour une mise à jour : l'identifiant est alors celui qu'on avait.
        return if (envelope.id != 0L) envelope.id else rowId
    }

    suspend fun archiveEnvelope(id: Long) {
        envelopeDao.archive(id)
    }

    /** Enveloppes archivées : elles gardent leurs dépenses et peuvent être restaurées. */
    val archivedEnvelopes: Flow<List<Envelope>> = envelopeDao.observeArchived()
        .map { entities -> entities.map { it.toDomain() } }

    suspend fun restoreEnvelope(id: Long) {
        envelopeDao.restore(id)
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

    /** Nombre d'opérations ni rangées ni mises hors budget. */
    val unsortedCount: Flow<Int> = transactionDao.observeUnsortedCount()

    /**
     * Relie le compte bancaire synchronisé au compte de l'app. Le compte courant déjà créé pour
     * les saisies manuelles est réutilisé : saisies et opérations bancaires vivent au même endroit.
     */
    suspend fun linkBankAccount(uid: String, name: String, ibanSuffix: String?): Long {
        val accountDao = database.accountDao()
        accountDao.findByExternalUid(uid)?.let { return it.id }
        val existing = accountDao.unlinkedCurrentAccount()
        return if (existing != null) {
            accountDao.link(existing.id, uid, name, ibanSuffix)
            existing.id
        } else {
            accountDao.insert(AccountEntity(name = name, type = AccountType.CURRENT, ibanSuffix = ibanSuffix, externalUid = uid))
        }
    }

    /**
     * Enregistre les opérations reçues de la banque, en un seul bloc :
     * - celles déjà importées sont ignorées (clé de dédoublonnage) ;
     * - une dépense qui correspond à une saisie manuelle la remplace et reprend son enveloppe ;
     * - les autres sont rangées par les règles de tri quand l'une s'applique ;
     * - le reste attend d'être trié.
     */
    suspend fun importBankOperations(
        accountUid: String,
        accountName: String,
        ibanSuffix: String?,
        operations: List<BankOperation>,
    ): ImportResult {
        if (operations.isEmpty()) return ImportResult(0, 0, 0)
        val accountId = linkBankAccount(accountUid, accountName, ibanSuffix)
        val keys = operations.importKeys(accountUid)
        return database.withTransaction {
            val ids = transactionDao.insertAll(
                operations.zip(keys) { operation, key ->
                    TransactionEntity(
                        accountId = accountId,
                        date = operation.date,
                        amountCents = operation.amountCents,
                        rawLabel = operation.rawLabel,
                        cleanLabel = cleanBankLabel(operation.rawLabel),
                        importHash = key,
                        source = TransactionSource.API,
                    )
                },
            )
            val added = transactionDao.byIds(ids.filter { it > 0 })
            if (added.isEmpty()) return@withTransaction ImportResult(0, 0, 0)

            val envelopes = envelopeDao.active().map { it.toDomain() }.associateBy { it.id }
            val rules = database.ruleDao().all().map { it.toDomain() }
            val manual = transactionDao.manualBetween(
                added.minOf { it.date }.minusDays(7),
                added.maxOf { it.date }.plusDays(2),
            ).map { ManualEntry(it.id, it.date, it.amountCents) }.toMutableList()

            var reconciled = 0
            var sorted = 0
            for (transaction in added.sortedBy { it.date }) {
                val match = if (transaction.amountCents < 0) {
                    findManualMatch(transaction.date, transaction.amountCents, manual)
                } else {
                    null
                }
                if (match != null) {
                    imputationDao.moveToTransaction(match.id, transaction.id)
                    transactionDao.markReconciled(transaction.id, match.id)
                    transactionDao.deleteManual(match.id)
                    manual.remove(match)
                    reconciled++
                    continue
                }
                val envelope = rules.matching(transaction.cleanLabel)?.let { envelopes[it.envelopeId] } ?: continue
                imputationDao.insert(transaction.imputationIn(envelope, ImputationMode.RULE))
                sorted++
            }
            ImportResult(added = added.size, reconciled = reconciled, sortedByRule = sorted)
        }
    }

    /**
     * Range une opération dans une enveloppe. Avec [ruleKeyword], crée aussi une règle
     * (« tout ce qui contient ce texte va ici ») et l'applique aux autres opérations à trier.
     * Renvoie le nombre d'autres opérations rangées par cette nouvelle règle.
     */
    suspend fun sortOperation(transactionId: Long, envelope: Envelope, ruleKeyword: String?): Int =
        database.withTransaction {
            val transaction = transactionDao.byId(transactionId) ?: return@withTransaction 0
            imputationDao.deleteForTransaction(transactionId)
            transactionDao.setIgnored(transactionId, false)
            imputationDao.insert(transaction.imputationIn(envelope, ImputationMode.MANUAL))
            val keyword = ruleKeyword?.trim().orEmpty()
            if (keyword.isEmpty()) return@withTransaction 0
            val rule = RuleEntity(matchType = RuleMatchType.CONTAINS, pattern = keyword, envelopeId = envelope.id)
            database.ruleDao().insert(rule)
            val single = listOf(rule.toDomain())
            transactionDao.unsorted()
                .filter { single.matching(it.cleanLabel) != null }
                .onEach { imputationDao.insert(it.imputationIn(envelope, ImputationMode.RULE)) }
                .size
        }

    /** Met une opération hors budget : elle reste visible mais n'est comptée dans aucune enveloppe. */
    suspend fun ignoreOperation(transactionId: Long) {
        database.withTransaction {
            imputationDao.deleteForTransaction(transactionId)
            transactionDao.setIgnored(transactionId, true)
        }
    }

    /** Compte courant auquel rattacher les saisies ; créé au premier besoin. */
    private suspend fun currentAccountId(): Long {
        val accountDao = database.accountDao()
        return accountDao.currentAccountId()
            ?: accountDao.insert(AccountEntity(name = "Compte courant", type = AccountType.CURRENT))
    }
}

/** Épargne d'un mois, pour l'historique. */
data class MonthSavings(val month: YearMonth, val outlook: SavingsOutlook)

/**
 * Bilan d'une année.
 * @property monthsCounted mois écoulés pris en compte (le mois en cours compris).
 */
data class YearOverview(
    val year: Int,
    val months: List<MonthSavings>,
    val spentByEnvelope: Map<Long, Money>,
    val dashboard: Dashboard,
    val monthsCounted: Int,
) {
    /** Épargne de l'année : réelle pour les mois passés, prévue pour le mois en cours. */
    val savings: Money get() = months.fold(Money.ZERO) { total, month -> total + month.outlook.forecast }
}

/** Résultat d'une synchronisation : opérations nouvelles, rapprochées d'une saisie, rangées par une règle. */
data class ImportResult(val added: Int, val reconciled: Int, val sortedByRule: Int) {
    val toSort: Int get() = added - reconciled - sortedByRule
}

/**
 * Imputation d'une opération dans une enveloppe, sur la période qui contient sa date. Le montant
 * imputé est l'opposé de celui de l'opération : une dépense (négative) consomme l'enveloppe, un
 * remboursement (positif) lui rend de l'argent.
 */
private fun TransactionEntity.imputationIn(envelope: Envelope, mode: ImputationMode) = ImputationEntity(
    transactionId = id,
    envelopeId = envelope.id,
    periodKey = envelope.periodContaining(date).key,
    amountCents = -amountCents,
    mode = mode,
)

private fun RuleEntity.toDomain() = LabelRule(id, matchType, pattern, envelopeId, priority)

private fun TransferEntity.toDomain() = Transfer(
    id = id,
    date = effectiveDate,
    sourceEnvelopeId = sourceEnvelopeId,
    targetEnvelopeId = targetEnvelopeId,
    amount = Money(amountCents),
    reason = reason,
)

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
    group = groupName,
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
    groupName = group?.trim()?.takeIf { it.isNotEmpty() },
)
