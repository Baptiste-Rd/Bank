package fr.baptiste.bank.data

import androidx.room.withTransaction
import fr.baptiste.bank.data.local.AccountEntity
import fr.baptiste.bank.ui.format.formatted
import fr.baptiste.bank.data.local.BankDatabase
import fr.baptiste.bank.data.local.BalanceEntity
import fr.baptiste.bank.data.local.EnvelopeEntity
import fr.baptiste.bank.data.local.ImputationEntity
import fr.baptiste.bank.data.local.LabelAliasEntity
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.local.RuleEntity
import fr.baptiste.bank.data.local.RuleRow
import fr.baptiste.bank.data.local.TransactionEntity
import fr.baptiste.bank.data.local.TransferRuleEntity
import fr.baptiste.bank.data.local.TransferEntity
import fr.baptiste.bank.data.local.TransferRow
import fr.baptiste.bank.domain.bank.BankOperation
import fr.baptiste.bank.domain.bank.LabelAlias
import fr.baptiste.bank.domain.bank.LabelRule
import fr.baptiste.bank.domain.bank.ProposalTarget
import fr.baptiste.bank.domain.bank.envelopeNameKey
import fr.baptiste.bank.domain.bank.SortedLabel
import fr.baptiste.bank.domain.bank.TransferRule
import fr.baptiste.bank.domain.bank.transferFor
import fr.baptiste.bank.domain.bank.aliasFor
import fr.baptiste.bank.domain.bank.ManualEntry
import fr.baptiste.bank.domain.bank.cleanBankLabel
import fr.baptiste.bank.domain.bank.findManualMatch
import fr.baptiste.bank.data.local.AllocationEntity
import fr.baptiste.bank.domain.model.BudgetPeriod
import fr.baptiste.bank.domain.budget.Inflow
import fr.baptiste.bank.domain.budget.MonthIncome
import fr.baptiste.bank.domain.budget.SalaryCandidate
import fr.baptiste.bank.domain.budget.missingPeriods
import fr.baptiste.bank.domain.budget.currentPeriodWithAmount
import fr.baptiste.bank.domain.bank.DateCorrection
import fr.baptiste.bank.domain.bank.DateReport
import fr.baptiste.bank.domain.bank.DatedOperation
import fr.baptiste.bank.domain.bank.dateReport
import fr.baptiste.bank.domain.bank.importKeys
import fr.baptiste.bank.domain.bank.matching
import fr.baptiste.bank.domain.bank.normalizeLabel
import fr.baptiste.bank.domain.budget.BalanceSnapshot
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.HoldingAccount
import fr.baptiste.bank.domain.budget.Holdings
import fr.baptiste.bank.domain.budget.computeHoldings
import fr.baptiste.bank.domain.budget.CurrentAccountExplained
import fr.baptiste.bank.domain.budget.SavingsGoals
import fr.baptiste.bank.domain.budget.explainCurrentAccount
import fr.baptiste.bank.domain.budget.SavingsOutlook
import fr.baptiste.bank.domain.budget.Spending
import fr.baptiste.bank.domain.budget.YearSpending
import fr.baptiste.bank.domain.budget.yearSpending
import fr.baptiste.bank.domain.budget.Transfer
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.budget.computeDashboard
import fr.baptiste.bank.domain.budget.dashboardDateRange
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.ImputationMode
import fr.baptiste.bank.domain.model.RuleMatchType
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.money.Money
import fr.baptiste.bank.domain.period.Frequency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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
    private val journalDao = database.journalDao()
    private val categoryDao = database.categoryDao()

    // --- Catégories imbriquées ---

    /** Les catégories enregistrées et leur mère. */
    val categories: Flow<List<fr.baptiste.bank.domain.model.CategoryNode>> = categoryDao.observeAll().map { rows ->
        rows.map { fr.baptiste.bank.domain.model.CategoryNode(it.name, it.parentName) }
    }

    /** La mère de chaque catégorie (voir parentsOf). */
    val categoryParents: Flow<Map<String, String>> = categories.map { fr.baptiste.bank.domain.model.parentsOf(it) }

    /** Crée la catégorie si elle n'existe pas encore ; vrai si elle vient d'être créée. « Autres » n'est jamais créée. */
    private suspend fun ensureCategory(name: String?): Boolean {
        val stored = name?.let { fr.baptiste.bank.domain.model.storedCategory(it) } ?: return false
        if (categoryDao.byName(stored) != null) return false
        categoryDao.insert(fr.baptiste.bank.data.local.CategoryEntity(name = stored))
        return true
    }

    /** Range une enveloppe dans [category] (créée au besoin, même encore vide) ; s'annule depuis l'historique. */
    suspend fun moveEnvelope(envelopeId: Long, category: String) = database.withTransaction {
        val before = envelopeState(envelopeId)
        val name = before.envelopesBefore.firstOrNull()?.name ?: return@withTransaction
        val stored = fr.baptiste.bank.domain.model.storedCategory(category)
        val created = ensureCategory(category)
        envelopeDao.setCategory(envelopeId, stored)
        record("$name rangée dans la catégorie ${stored ?: fr.baptiste.bank.domain.model.DEFAULT_CATEGORY}", before + JournalPayload(categoriesCreated = listOfNotNull(stored.takeIf { created })))
    }

    /**
     * Range une catégorie dans [parent] (null : catégorie principale). Faux si c'est impossible :
     * dans elle-même, dans une de ses sous-catégories, ou pour « Autres ».
     */
    suspend fun moveCategory(name: String, parent: String?): Boolean = database.withTransaction {
        val parents = fr.baptiste.bank.domain.model.parentsOf(categoryDao.all().map { fr.baptiste.bank.domain.model.CategoryNode(it.name, it.parentName) })
        val stored = fr.baptiste.bank.domain.model.storedCategory(name) ?: return@withTransaction false
        val target = parent?.let { fr.baptiste.bank.domain.model.storedCategory(it) ?: return@withTransaction false }
        if (!fr.baptiste.bank.domain.model.canMoveCategory(stored, target, parents)) return@withTransaction false
        val before = categoryDao.byName(stored)
        val created = listOfNotNull(stored.takeIf { ensureCategory(it) }, target?.takeIf { ensureCategory(it) })
        categoryDao.setParent(stored, target)
        record(
            if (target != null) "$stored rangée dans la catégorie $target" else "$stored devient une catégorie principale",
            JournalPayload(categoriesBefore = listOfNotNull(before?.let { CategoryState(it.name, it.parentName) }), categoriesCreated = created),
        )
        true
    }

    /**
     * Renomme une catégorie : ses enveloppes et ses sous-catégories suivent. Vers un nom qui existe
     * déjà, les deux catégories n'en font plus qu'une. Faux pour « Autres » ou un nom vide.
     */
    suspend fun renameCategory(oldName: String, newName: String): Boolean = database.withTransaction {
        val old = fr.baptiste.bank.domain.model.storedCategory(oldName) ?: return@withTransaction false
        val target = fr.baptiste.bank.domain.model.storedCategory(newName) ?: return@withTransaction false
        if (old.equals(target, ignoreCase = false)) return@withTransaction false
        val affected = envelopeDao.inCategory(old).map { envelopeState(it.id) }
        val oldRow = categoryDao.byName(old)
        val children = categoryDao.all().filter { it.parentName?.equals(old, ignoreCase = true) == true }
        val existing = categoryDao.byName(target)?.takeIf { !it.name.equals(old, ignoreCase = true) }
        envelopeDao.renameCategory(old, target)
        categoryDao.reparentChildren(old, target)
        when {
            existing != null -> categoryDao.delete(old)
            oldRow != null -> categoryDao.rename(old, target)
            else -> categoryDao.insert(fr.baptiste.bank.data.local.CategoryEntity(name = target))
        }
        record(
            "$old renommée $target",
            affected.fold(JournalPayload()) { all, part -> all + part } + JournalPayload(
                categoriesBefore = listOfNotNull(oldRow?.let { CategoryState(it.name, it.parentName) }) + children.map { CategoryState(it.name, it.parentName) },
                categoriesCreated = listOfNotNull(target.takeIf { existing == null }),
            ),
        )
        true
    }

    // --- Historique des changements (annulables) ---

    /** Les dix derniers changements, le plus récent en premier. */
    val journal: Flow<List<JournalLine>> = journalDao.observeRecent().map { rows ->
        val blocked = fr.baptiste.bank.domain.history.blockedEntries(rows.map { it.toHistoryEntry() })
        rows.map { JournalLine(it.id, it.at, it.label, it.undone, blocked = it.id in blocked) }
    }

    private fun fr.baptiste.bank.data.local.JournalEntity.toHistoryEntry(): fr.baptiste.bank.domain.history.HistoryEntry {
        val decoded = runCatching { JournalPayload.decode(payload) }.getOrNull()
            ?: return fr.baptiste.bank.domain.history.HistoryEntry(id, undone, emptySet())
        return fr.baptiste.bank.domain.history.HistoryEntry(id, undone, decoded.touched(), decoded.creates(), decoded.uses())
    }

    /** Changements en cours de regroupement (un tri assisté, un plan) : une seule entrée à la fin. */
    private var batch: MutableList<JournalPayload>? = null

    /** État actuel de ces opérations, à garder avant de les changer. */
    private suspend fun snapshot(ids: Collection<Long>): List<OperationState> = ids.distinct().mapNotNull { id ->
        val transaction = transactionDao.byId(id) ?: return@mapNotNull null
        OperationState(id, transaction.ignored, transaction.transferAccountId, imputationDao.forTransaction(id).map { it.copy(id = 0) })
    }

    /** État d'une opération à trier (rangée par une règle juste après). */
    private fun unsortedState(id: Long) = OperationState(id, ignored = false, transferAccountId = null, imputations = emptyList())

    private suspend fun envelopeState(id: Long): JournalPayload {
        val before = envelopeDao.byId(id) ?: return JournalPayload()
        return JournalPayload(envelopesBefore = listOf(before), allocationsBefore = database.allocationDao().forEnvelope(id).map { it.copy(id = 0) })
    }

    private suspend fun record(label: String, payload: JournalPayload) {
        batch?.let { it += payload; return }
        if (payload.isEmpty) return
        val stored = payload.copy(extraTouched = (payload.extraTouched + touchedNow(payload)).distinct())
        journalDao.insert(fr.baptiste.bank.data.local.JournalEntity(at = System.currentTimeMillis(), label = label, payload = stored.encode()))
        journalDao.trim()
    }

    /**
     * Ce qu'un changement touche, lu en base juste après lui : les enveloppes où vont maintenant ses
     * opérations, saisies, transferts et règles (annuler la création d'une enveloppe est alors
     * bloqué tant qu'un changement plus récent l'utilise), et le motif des règles et alias créés.
     */
    private suspend fun touchedNow(payload: JournalPayload): List<String> = buildList {
        (payload.operations.map { it.transactionId } + payload.manualCreated).distinct().forEach { id ->
            imputationDao.forTransaction(id).forEach { add("${USE_PREFIX}env:${it.envelopeId}") }
        }
        transferDao.byIds(payload.transfersCreated).forEach { t ->
            listOfNotNull(t.sourceEnvelopeId, t.targetEnvelopeId).forEach { add("${USE_PREFIX}env:$it") }
        }
        val rules = database.ruleDao().all()
        rules.filter { it.id in payload.rulesCreated }.forEach { add(ruleKey(it.pattern)); add("${USE_PREFIX}env:${it.envelopeId}") }
        database.aliasDao().all().filter { it.id in payload.aliasRowsCreated }.forEach { add(aliasKey(it.pattern)) }
    }

    /** Regroupe les changements faits par [block] en une seule entrée [label]. */
    private suspend fun <T> batched(label: String, block: suspend () -> T): T {
        if (batch != null) return block()
        val parts = mutableListOf<JournalPayload>()
        batch = parts
        val result = try {
            block()
        } finally {
            batch = null
        }
        record(label, parts.fold(JournalPayload()) { all, part -> all + part })
        return result
    }

    private suspend fun operationLabel(id: Long): String =
        transactionDao.byId(id)?.let { it.alias ?: it.cleanLabel } ?: "Opération"

    /**
     * Annule un changement de l'historique : chaque chose retrouve son état d'avant ce changement.
     * Renvoie son libellé, ou null s'il était déjà annulé.
     */
    suspend fun undo(entryId: Long): String? = database.withTransaction {
        val entry = journalDao.byId(entryId) ?: return@withTransaction null
        if (entry.undone) return@withTransaction null
        // Une entrée plus récente touche les mêmes éléments : l'annuler l'écraserait.
        val recent = journalDao.recent()
        if (entryId in fr.baptiste.bank.domain.history.blockedEntries(recent.map { it.toHistoryEntry() })) return@withTransaction null
        val payload = JournalPayload.decode(entry.payload)
        // Saisies supprimées : remises telles quelles, avec leurs parts.
        // Seules les enveloppes qui existent encore reçoivent ce qui revient (une supprimée depuis ne le peut plus).
        val alive = envelopeDao.allIds().toSet()
        payload.manualDeleted.forEach { manual ->
            val parts = manual.imputations.filter { it.envelopeId in alive }
            if (transactionDao.byId(manual.transaction.id) == null && parts.isNotEmpty()) {
                transactionDao.insert(manual.transaction)
                parts.forEach { imputationDao.insert(it.copy(id = 0)) }
            }
        }
        payload.transfersCancelled.forEach { transferDao.restore(it) }
        payload.aliasesBefore.forEach { transactionDao.setAlias(it.transactionId, it.alias) }
        database.aliasDao().delete(payload.aliasRowsCreated)
        payload.aliasRowsRemoved.forEach { database.aliasDao().insert(it.copy(id = 0)) }
        payload.monthCountsBefore.forEach { settings.restoreMonthCount(it.month, it.counted) }
        payload.manualCreated.forEach { id ->
            imputationDao.deleteForTransaction(id)
            transactionDao.deleteManual(id)
        }
        payload.transfersCreated.forEach { transferDao.cancel(it) }
        database.ruleDao().delete(payload.rulesCreated)
        payload.rulesRemoved.filter { it.envelopeId in alive }.forEach { database.ruleDao().insert(it) }
        database.transferRuleDao().delete(payload.transferRulesCreated)
        // Catégories : celles créées par le changement partent, les autres retrouvent leur mère.
        payload.categoriesCreated.forEach { categoryDao.delete(it) }
        payload.categoriesBefore.forEach { state ->
            if (categoryDao.byName(state.name) == null) categoryDao.insert(fr.baptiste.bank.data.local.CategoryEntity(name = state.name, parentName = state.parent))
            else categoryDao.setParent(state.name, state.parent)
        }
        // Une enveloppe supprimée définitivement depuis n'est pas recréée vide.
        payload.envelopesBefore.filter { it.id in alive }.forEach { before ->
            envelopeDao.upsert(before)
            database.allocationDao().deleteForEnvelope(before.id)
            database.allocationDao().insertAll(payload.allocationsBefore.filter { it.envelopeId == before.id })
        }
        val existing = envelopeDao.allIds().toSet() - payload.envelopesCreated.toSet()
        payload.operations.forEach { state ->
            if (transactionDao.byId(state.transactionId) == null) return@forEach
            imputationDao.deleteForTransaction(state.transactionId)
            transactionDao.setIgnored(state.transactionId, state.ignored)
            transactionDao.setTransferAccount(state.transactionId, state.transferAccountId)
            // Une enveloppe supprimée depuis ne peut plus recevoir sa part : l'opération reste à trier.
            state.imputations.filter { it.envelopeId in existing }.forEach { imputationDao.insert(it.copy(id = 0)) }
        }
        payload.envelopesCreated.forEach { deleteEnvelope(it) }
        if (payload.datesBefore.isNotEmpty()) {
            payload.datesBefore.forEach { transactionDao.setDate(it.transactionId, it.date) }
            realignImputationPeriods()
        }
        journalDao.markUndone(entryId)
        entry.label
    }

    val envelopes: Flow<List<Envelope>> = combine(envelopeDao.observeActive(), database.allocationDao().observeAll()) { entities, allocations ->
        entities.withPeriods(allocations)
    }

    /** Enveloppes actives, avec leurs périodes enregistrées (lecture ponctuelle). */
    private suspend fun activeEnvelopes(): List<Envelope> = envelopeDao.active().withPeriods(database.allocationDao().all())

    /** Toutes les enveloppes, archivées comprises, avec leurs périodes. */
    private suspend fun allEnvelopes(): List<Envelope> =
        (envelopeDao.active() + envelopeDao.archived()).withPeriods(database.allocationDao().all())

    /** Virements reçus des quatre derniers mois et du mois en cours (salaire). */
    private fun recentInflows(today: LocalDate = LocalDate.now()): Flow<List<Inflow>> =
        transactionDao.observeInflows(YearMonth.from(today).minusMonths(4).atDay(1), today)
            .map { rows -> rows.map { Inflow(it.label, it.date, Money(it.amountCents)) } }

    /** Salaires possibles, lus à la banque. */
    val salaryCandidates: Flow<List<SalaryCandidate>> = recentInflows().map { fr.baptiste.bank.domain.budget.salaryCandidates(it) }

    /** Salaire confirmé (clé, finance le mois suivant), null sinon. */
    val salary: Flow<Pair<String, Boolean>?> = settings.salary

    /** Revenu du mois en cours : le salaire lu à la banque une fois confirmé (reçu, ou attendu). */
    fun monthIncome(today: LocalDate = LocalDate.now()): Flow<MonthIncome?> =
        combine(settings.salary, recentInflows(today)) { salary, inflows ->
            salary?.let { (key, next) -> fr.baptiste.bank.domain.budget.monthIncome(YearMonth.from(today), inflows, key, next) }
        }

    suspend fun confirmSalary(candidate: SalaryCandidate?, nextMonth: Boolean = candidate?.paysNextMonth ?: true) =
        settings.setSalary(candidate?.key, nextMonth)

    suspend fun setSalaryNextMonth(nextMonth: Boolean) {
        settings.salary.first()?.let { (key, _) -> settings.setSalary(key, nextMonth) }
    }

    val glassTuning: Flow<String?> = settings.glassTuning

    val homeShowsDay: Flow<Boolean> = settings.homeShowsDay

    suspend fun setHomeShowsDay(day: Boolean) = settings.setHomeShowsDay(day)

    suspend fun setGlassTuning(encoded: String?) = settings.setGlassTuning(encoded)

    /**
     * Tout ce qu'il faut pour les tableaux de [dates] (voir BudgetData) : enveloppes actives et
     * retirées, dépenses et transferts de la plage utile, virements reçus pour le salaire de chaque mois.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun budgetData(dates: List<LocalDate>): Flow<fr.baptiste.bank.domain.budget.BudgetData> {
        val allocations = database.allocationDao().observeAll()
            .map { rows -> rows.associate { (it.envelopeId to it.periodKey) to Money(it.amountCents) } }
        // Les enveloppes retirées comptent dans les mois qu'elles ont couverts ; le calcul choisit
        // lesquelles figurent à chaque date.
        val known = combine(envelopes, archivedEnvelopes) { active, archived -> active + archived.filter { it.retiredOn != null } }
        val incomeRange = fr.baptiste.bank.domain.budget.incomeLoadRange(dates)
        val inflows = transactionDao.observeInflows(incomeRange.start, incomeRange.endInclusive)
            .map { rows -> rows.map { Inflow(it.label, it.date, Money(it.amountCents)) } }
        val income = combine(settings.salary, settings.monthlyIncome, inflows) { salary, typed, received -> Triple(salary, typed, received) }
        val startAndExcluded = combine(trackingStart(dates.max()), excludedMonths) { start, excluded -> start to excluded }
        return known.flatMapLatest { envelopes ->
            // La plage dépend des périodes des enveloppes (une période de 3 mois peut commencer avant
            // l'année) : elle est recalculée quand elles changent.
            val range = fr.baptiste.bank.domain.budget.boardLoadRange(dates, envelopes)
            val spendings = imputationDao.observeSpendings(range.start, range.endInclusive)
                .map { rows -> rows.map { Spending(it.envelopeId, it.date, Money(it.amountCents), it.wholePeriod) } }
            val transfers = transferDao.observeActive(range.start, range.endInclusive)
                .map { rows -> rows.map { it.toDomain() } }
            combine(allocations, spendings, transfers, income, startAndExcluded) { allocs, spent, moves, (salary, typed, received), (start, excluded) ->
                fr.baptiste.bank.domain.budget.BudgetData(
                    envelopes = envelopes,
                    allocations = allocs,
                    spendings = spent,
                    transfers = moves,
                    inflows = received,
                    salary = salary,
                    typedIncome = typed,
                    trackingStart = start,
                    excluded = excluded,
                )
            }
        }
    }

    /** Tableau d'un jour (le mois en cours, ou le dernier jour d'un mois passé) : voir dashboardAt. */
    fun dashboard(today: LocalDate = LocalDate.now()): Flow<Dashboard> =
        budgetData(listOf(today)).map { fr.baptiste.bank.domain.budget.dashboardAt(it, today) }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)

    /**
     * Épargne des [months] derniers mois, le mois en cours compris (en dernier). Chaque mois passé
     * est le tableau de son dernier jour : le même calcul que son Bilan, avec son salaire et les
     * enveloppes qui existaient alors.
     */
    fun savingsHistory(today: LocalDate = LocalDate.now(), months: Int = 6): Flow<List<MonthSavings>> {
        val current = YearMonth.from(today)
        val dates = (months - 1 downTo 1).map { current.minusMonths(it.toLong()).atEndOfMonth() } + today
        return budgetData(dates).map { data ->
            fr.baptiste.bank.domain.budget.savingsByMonth(data, dates, current).map { MonthSavings(it.month, it.outlook, it.counted) }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
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
        // Les mois et le tableau du dernier jour viennent des mêmes données : un seul chargement.
        val dates = (1 until end.monthValue).map { YearMonth.of(year, it).atEndOfMonth() } + end
        return combine(budgetData(dates), spent) { data, byEnvelope ->
            val months = fr.baptiste.bank.domain.budget.savingsByMonth(data, dates, YearMonth.from(end))
                .map { MonthSavings(it.month, it.outlook, it.counted) }
            YearOverview(year, months, byEnvelope, fr.baptiste.bank.domain.budget.dashboardAt(data, end), months.count { it.counted })
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
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
                // Pris sur « Disponible sur le compte » : le mois n'est pas touché.
                fromReserve = incoming && leg.fromReserve && leg.sourceEnvelopeId == null,
            )
        }
        if (rows.isEmpty()) return
        val ids = transferDao.insertAll(rows)
        val name = envelopeDao.byId(envelopeId)?.name.orEmpty()
        val total = Money(rows.sumOf { it.amountCents })
        record(if (incoming) "${total.formatted()} ajoutés à $name" else "${total.formatted()} retirés de $name", JournalPayload(transfersCreated = ids))
    }

    /** Met [amount] de côté dans une enveloppe depuis « Disponible sur le compte » : le mois n'est pas touché. */
    suspend fun addReserveTransfer(envelopeId: Long, amount: Money, reason: String?, date: LocalDate = LocalDate.now()) =
        addTransfers(listOf(TransferLeg(null, amount, fromReserve = true)), envelopeId, incoming = true, reason = reason, date = date)

    /** Annule un transfert depuis « Ajouts et retraits » : l'annulation s'inscrit à l'Historique (et s'y défait). */
    suspend fun cancelTransfer(id: Long) {
        val transfer = transferDao.byId(id)?.takeIf { !it.cancelled } ?: return
        transferDao.cancel(id)
        val target = transfer.targetEnvelopeId?.let { envelopeDao.byId(it)?.name } ?: "À répartir ce mois-ci"
        record("${Money(transfer.amountCents).formatted()} vers $target annulés", JournalPayload(transfersCancelled = listOf(id)))
    }

    fun envelopeOperations(envelopeId: Long, periodKey: String): Flow<List<OperationRow>> =
        transactionDao.observeEnvelopeOperations(envelopeId, periodKey)

    fun recentOperations(limit: Int = 200): Flow<List<OperationRow>> =
        transactionDao.observeRecentOperations(limit)

    /** Opérations dont le libellé contient [query], tous les mois (les 60 plus récentes). */
    fun searchOperations(query: String): Flow<List<OperationRow>> =
        transactionDao.searchOperations("%" + query.trim().replace("%", "").replace("_", "") + "%")

    /** Les opérations d'un mois. */
    fun monthOperations(month: YearMonth): Flow<List<OperationRow>> =
        transactionDao.observeOperationsBetween(month.atDay(1), month.atEndOfMonth())

    /** Crée ou met à jour une enveloppe ; renvoie son identifiant. */
    suspend fun saveEnvelope(envelope: Envelope): Long = database.withTransaction {
        val before = if (envelope.id != 0L) envelopeState(envelope.id) else null
        val id = saveEnvelopeUnrecorded(envelope)
        if (before != null) {
            record("${envelope.name} modifiée", before)
        } else {
            record("Nouvelle enveloppe ${envelope.name}", JournalPayload(envelopesCreated = listOf(id)))
        }
        id
    }

    private suspend fun saveEnvelopeUnrecorded(envelope: Envelope): Long {
        val today = LocalDate.now()
        // Une nouvelle enveloppe part du 1er du mois : une dépense plus ancienne n'y compte pas.
        val toSave = if (envelope.id == 0L && envelope.start == null) envelope.copy(start = today.withDayOfMonth(1)) else envelope
        var adjusted = toSave
        if (envelope.id != 0L) {
            // Le passé se fige avec les réglages d'avant ; un nouveau montant vaut pour la période en cours.
            ensurePeriods(today)
            val before = allEnvelopes().firstOrNull { it.id == envelope.id }
            if (before != null) {
                val current = before.periodContaining(today)
                if (before.amount != toSave.amount) {
                    before.currentPeriodWithAmount(today, toSave.amount)?.let {
                        database.allocationDao().setAmount(envelope.id, it.key, it.amount.cents)
                    }
                }
                // Une remise à zéro choisie (ou un budget devenu ponctuel) part de la période en cours :
                // une ancienne enveloppe sans départ prend le début de cette période, ses dépenses comptent toujours.
                val resetChanged = toSave.firstReset != null && toSave.firstReset != before.firstReset
                if ((resetChanged || (!toSave.repeats && before.repeats)) && adjusted.start == null) {
                    adjusted = adjusted.copy(start = current.start)
                }
                val reset = toSave.firstReset
                if (resetChanged && reset != null && reset.isAfter(today) && before.periods.any { it.key == current.key }) {
                    database.allocationDao().setEnd(envelope.id, current.key, reset.minusDays(1))
                }
            }
        }
        ensureCategory(adjusted.group)
        val rowId = envelopeDao.upsert(adjusted.toEntity())
        // Upsert renvoie -1 pour une mise à jour : l'identifiant est alors celui qu'on avait.
        val id = if (envelope.id != 0L) envelope.id else rowId
        ensurePeriods(today)
        return id
    }

    /**
     * Enregistre les périodes commencées qui manquent, du début du suivi à aujourd'hui : le passé figé.
     * Une enveloppe archivée sans date de retrait (d'avant la version 13) est laissée telle quelle.
     */
    suspend fun ensurePeriods(today: LocalDate = LocalDate.now()): Int {
        val from = trackingStart(today).first().atDay(1)
        val spent = imputationDao.spentByPeriod()
            .groupBy { it.envelopeId }
            .mapValues { (_, rows) -> rows.associate { it.periodKey to Money(it.amountCents) } }
        val rows = allEnvelopes()
            .filter { !it.archived || it.retiredOn != null }
            .flatMap { envelope ->
                envelope.missingPeriods(from, today, spent[envelope.id].orEmpty()).map {
                    AllocationEntity(
                        envelopeId = envelope.id,
                        periodKey = it.key,
                        amountCents = it.amount.cents,
                        startDate = it.start,
                        endDate = it.endInclusive,
                    )
                }
            }
        if (rows.isNotEmpty()) database.allocationDao().insertAll(rows)
        return rows.size
    }

    /** Ce qui est rangé dans une enveloppe, annoncé avant de la supprimer définitivement. */
    suspend fun envelopeUsage(id: Long): EnvelopeUsage {
        val rows = imputationDao.usage(id)
        val bank = rows.filter { it.source != TransactionSource.MANUAL }
        val manual = rows.filter { it.source == TransactionSource.MANUAL }
        return EnvelopeUsage(
            bankCount = bank.sumOf { it.count },
            bankAmount = Money(bank.sumOf { it.amountCents }),
            manualCount = manual.sumOf { it.count },
            manualAmount = Money(manual.sumOf { it.amountCents }),
        )
    }

    /**
     * Supprime une enveloppe définitivement (erreur de saisie) : ses dépenses de la banque repartent
     * à trier, ses saisies, règles, transferts et périodes sont effacés.
     */
    suspend fun deleteEnvelope(id: Long) = database.withTransaction {
        // Les changements qui la touchent ne peuvent plus s'annuler sans elle : ils quittent l'Historique.
        val touching = journalDao.recent().filter { entry ->
            runCatching { JournalPayload.decode(entry.payload) }.getOrNull()?.let { "env:$id" in it.touched() || "env:$id" in it.uses() } ?: false
        }
        journalDao.delete(touching.map { it.id })
        transactionDao.deleteManualIn(id)
        imputationDao.deleteForEnvelope(id)
        database.ruleDao().deleteForEnvelope(id)
        transferDao.deleteForEnvelope(id)
        envelopeDao.delete(id)
    }

    /** Une période enregistrée d'une enveloppe et ce qui y a été dépensé (historique de sa fiche). */
    data class PeriodLine(val period: BudgetPeriod, val spent: Money)

    /** Les périodes d'une enveloppe, la plus récente en premier. */
    fun envelopePeriods(id: Long): Flow<List<PeriodLine>> =
        combine(database.allocationDao().observeAll(), imputationDao.observeSpentByPeriod(id)) { allocations, spent ->
            val byKey = spent.associate { it.periodKey to Money(it.amountCents) }
            allocations
                .filter { it.envelopeId == id && it.startDate != null && it.endDate != null }
                .map { PeriodLine(BudgetPeriod(it.periodKey, it.startDate!!, it.endDate!!, Money(it.amountCents)), byKey[it.periodKey] ?: Money.ZERO) }
                .sortedByDescending { it.period.start }
        }

    /** Corrige le budget d'une période (passée comprise), d'un geste volontaire depuis la fiche. */
    suspend fun setPeriodAmount(id: Long, key: String, amount: Money) = database.withTransaction {
        val before = envelopeState(id)
        database.allocationDao().setAmount(id, key, amount.cents)
        record("${before.envelopesBefore.firstOrNull()?.name.orEmpty()} : budget d'une période corrigé", before)
    }

    /**
     * Montant proposé pour l'enveloppe [envelopeId] : la moyenne de ses mois terminés (douze au plus,
     * depuis le début du suivi et son départ). Une nouvelle enveloppe n'a pas d'historique propre :
     * rien n'est proposé. La moyenne d'une catégorie ne vaut pas pour une enveloppe nouvelle, puisque
     * ces dépenses sont déjà rangées dans les autres enveloppes (Cadeaux à 26 € ne valait pas 391 €
     * de Loisirs). [category] reste dans la signature pour l'appel du formulaire.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun suggestAmount(envelopeId: Long?, category: String?, frequency: fr.baptiste.bank.domain.period.Frequency): fr.baptiste.bank.domain.budget.AmountSuggestion? {
        val today = LocalDate.now()
        if (envelopeId == null || envelopeId == 0L) return null
        val envelope = allEnvelopes().firstOrNull { it.id == envelopeId }
        val from = YearMonth.from(today).minusMonths(12).atDay(1)
        val spent = imputationDao.observeSpendings(from, today).first()
            .filter { it.envelopeId == envelopeId }
            .map { it.date to Money(it.amountCents) }
        val since = listOfNotNull(trackingStart(today).first(), envelope?.start?.let { YearMonth.from(it) }).maxOrNull()
        return fr.baptiste.bank.domain.budget.suggestedAmount(spent, frequency, today, since = since, excluded = excludedMonths.first())
    }

    /** Retire une enveloppe : elle n'a plus de période ensuite, mais reste dans les mois qu'elle a couverts. */
    suspend fun archiveEnvelope(id: Long) {
        ensurePeriods()
        val before = envelopeState(id)
        envelopeDao.archive(id, LocalDate.now())
        record("${before.envelopesBefore.firstOrNull()?.name.orEmpty()} arrêtée", before)
    }

    /** Enveloppes archivées : elles gardent leurs dépenses et peuvent être restaurées. */
    val archivedEnvelopes: Flow<List<Envelope>> = combine(envelopeDao.observeArchived(), database.allocationDao().observeAll()) { entities, allocations ->
        entities.withPeriods(allocations)
    }

    suspend fun restoreEnvelope(id: Long) {
        val before = envelopeState(id)
        envelopeDao.restore(id)
        record("${before.envelopesBefore.firstOrNull()?.name.orEmpty()} de nouveau active", before)
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
            record("$label · ${amount.formatted()} → ${envelope.name}", JournalPayload(manualCreated = listOf(transactionId)))
        }
    }

    /** Supprime une saisie manuelle. Les opérations importées de la banque ne se suppriment pas. */
    suspend fun deleteManualExpense(transactionId: Long) {
        database.withTransaction {
            val transaction = transactionDao.byId(transactionId)?.takeIf { it.source == TransactionSource.MANUAL } ?: return@withTransaction
            val parts = imputationDao.forTransaction(transactionId)
            imputationDao.deleteForTransaction(transactionId)
            transactionDao.deleteManual(transactionId)
            record("${transaction.alias ?: transaction.cleanLabel} supprimée", JournalPayload(manualDeleted = listOf(ManualState(transaction, parts))))
        }
    }

    /** Nombre d'opérations à trier : la taille de la file elle-même (liste, cartes et pastilles disent le même nombre). */
    val unsortedCount: Flow<Int> get() = unsortedOperations.map { it.size }

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
            val aliases = database.aliasDao().all().map { it.toDomain() }
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
                        alias = aliases.aliasFor(cleanBankLabel(operation.rawLabel))?.alias,
                        bookingDate = operation.bookingDate,
                        bankDate = operation.bankDate,
                    )
                },
            )
            // Déjà importées : on complète les jours donnés par la banque, sans toucher au jour retenu.
            // Un jour à corriger est proposé dans « Vérifier les dates » et ne change qu'une fois validé.
            ids.forEachIndexed { index, id ->
                if (id <= 0) transactionDao.updateBankDates(keys[index], operations[index].bookingDate, operations[index].bankDate)
            }
            val added = transactionDao.byIds(ids.filter { it > 0 })
            if (added.isEmpty()) return@withTransaction ImportResult(0, 0, 0)

            val envelopes = activeEnvelopes().associateBy { it.id }
            val rules = database.ruleDao().all().map { it.toDomain() }
            val transferRules = database.transferRuleDao().all().map { TransferRule(it.id, it.pattern, it.accountId) }
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
                    // Le nom que tu avais donné à ta saisie (« Vélo ») reste, au lieu de « Retrait Paris ».
                    transactionDao.byId(match.id)?.cleanLabel?.takeIf { it.isNotBlank() }
                        ?.let { if (transaction.alias == null) transactionDao.setAlias(transaction.id, it) }
                    imputationDao.moveToTransaction(match.id, transaction.id)
                    transactionDao.markReconciled(transaction.id, match.id)
                    transactionDao.deleteManual(match.id)
                    manual.remove(match)
                    reconciled++
                    continue
                }
                // Un virement vers un de mes comptes passe avant les enveloppes : ce n'est pas une dépense.
                val transfer = transferRules.transferFor(transaction)
                if (transfer != null) {
                    transactionDao.setIgnored(transaction.id, true)
                    transactionDao.setTransferAccount(transaction.id, transfer.accountId)
                    sorted++
                    continue
                }
                val envelope = rules.matchingFor(transaction)?.let { envelopes[it.envelopeId] } ?: continue
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
            val before = snapshot(listOf(transactionId))
            val label = "${transaction.alias ?: transaction.cleanLabel} → ${envelope.name}"
            imputationDao.deleteForTransaction(transactionId)
            transactionDao.setIgnored(transactionId, false)
            transactionDao.setTransferAccount(transactionId, null)
            imputationDao.insert(transaction.imputationIn(envelope, ImputationMode.MANUAL))
            val keyword = ruleKeyword?.trim().orEmpty()
            if (keyword.isEmpty()) {
                record(label, JournalPayload(operations = before))
                return@withTransaction 0
            }
            val ruleDao = database.ruleDao()
            // Un même motif ne mène qu'à une enveloppe : la nouvelle règle remplace l'ancienne.
            val key = normalizeLabel(keyword)
            val replaced = ruleDao.all().filter { normalizeLabel(it.pattern) == key }
            ruleDao.delete(replaced.map { it.id })
            val rule = RuleEntity(matchType = RuleMatchType.CONTAINS, pattern = keyword, envelopeId = envelope.id)
            val ruleId = ruleDao.insert(rule)
            val single = listOf(rule.toDomain())
            val others = transactionDao.unsorted()
                .filter { single.matchingFor(it) != null }
                .onEach { imputationDao.insert(it.imputationIn(envelope, ImputationMode.RULE)) }
            record(
                if (others.isEmpty()) label else "$label, et ${others.size} autre${if (others.size > 1) "s" else ""}",
                JournalPayload(
                    operations = before + others.map { unsortedState(it.id) },
                    rulesCreated = listOf(ruleId),
                    rulesRemoved = replaced.map { it.copy(id = 0) },
                ),
            )
            others.size
        }

    /**
     * Recale la période de chaque dépense sur le jour de son opération (les rattrapages, qui portent
     * sur une période choisie, ne bougent pas). Une opération dont le jour a été corrigé après son
     * tri (jour du paiement plutôt que de comptabilisation) restait sinon dans la période de son
     * premier jour : une dépense d'août listée dans l'enveloppe d'octobre. Renvoie le nombre recalé.
     */
    /** Diagnostic des dates des opérations bancaires (voir [dateReport]). */
    val dateCheck: Flow<DateReport> = transactionDao.observeDatedOperations()
        .map { rows -> dateReport(rows.map { DatedOperation(it.id, it.rawLabel, it.amountCents, it.date, it.bookingDate, it.bankDate) }) }

    /** Applique les jours validés, puis replace chaque dépense dans la période de son nouveau jour. */
    suspend fun applyDateCorrections(corrections: List<DateCorrection>): Int = database.withTransaction {
        val before = corrections.mapNotNull { c -> transactionDao.byId(c.operation.id)?.let { DateState(it.id, it.date) } }
        corrections.forEach { transactionDao.setDate(it.operation.id, it.newDate) }
        record(if (corrections.size == 1) "Date d'achat corrigée" else "${corrections.size} dates d'achat corrigées", JournalPayload(datesBefore = before))
        realignImputationPeriods()
        corrections.size
    }

    suspend fun realignImputationPeriods(): Int {
        val envelopes = allEnvelopes().associateBy { it.id }
        var fixed = 0
        for (row in transactionDao.datedImputations()) {
            val envelope = envelopes[row.envelopeId] ?: continue
            val key = envelope.periodContaining(row.date).key
            if (key != row.periodKey) {
                transactionDao.setPeriodKey(row.id, key)
                fixed++
            }
        }
        return fixed
    }

    /** Règles de tri et alias, pour l'écran qui les liste. */
    val rules: Flow<List<RuleRow>> = database.ruleDao().observeAll()
    val aliases: Flow<List<LabelAlias>> = database.aliasDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun deleteRule(id: Long) = database.withTransaction {
        val rule = database.ruleDao().all().firstOrNull { it.id == id } ?: return@withTransaction
        database.ruleDao().delete(listOf(id))
        record("Règle « ${rule.pattern} » supprimée", JournalPayload(rulesRemoved = listOf(rule)))
    }

    /** Supprime un alias ; avec [restoreLabels], les opérations qui portent son nom reprennent celui de la banque. */
    suspend fun deleteAlias(id: Long, restoreLabels: Boolean) = database.withTransaction {
        val alias = database.aliasDao().all().firstOrNull { it.id == id } ?: return@withTransaction
        val named = if (restoreLabels) transactionDao.withAlias(alias.alias).map { AliasState(it.id, it.alias) } else emptyList()
        database.aliasDao().delete(listOf(id))
        if (restoreLabels) transactionDao.clearAlias(alias.alias)
        record("Nom « ${alias.alias} » supprimé", JournalPayload(aliasRowsRemoved = listOf(alias), aliasesBefore = named))
    }

    /** Historique du tri, pour proposer une enveloppe aux opérations à trier. */
    val sortedHistory: Flow<List<SortedLabel<Long>>> = transactionDao.observeSortedHistory()
        .map { rows -> rows.map { SortedLabel(it.label, it.envelopeId) } }

    /**
     * Renomme une opération. Un nom vide rend le libellé de la banque. Avec [pattern], crée aussi
     * un alias : les opérations sans alias dont le libellé contient ce texte prennent ce nom, et
     * les suivantes le prendront à l'import. Rend le nombre d'autres opérations renommées.
     */
    suspend fun renameOperation(transactionId: Long, alias: String, pattern: String?): Int =
        database.withTransaction {
            val name = alias.trim().ifEmpty { null }
            val previous = transactionDao.byId(transactionId) ?: return@withTransaction 0
            transactionDao.setAlias(transactionId, name)
            val label = "${previous.alias ?: previous.cleanLabel} renommée ${name ?: previous.cleanLabel}"
            val own = JournalPayload(aliasesBefore = listOf(AliasState(transactionId, previous.alias)))
            val text = pattern?.trim().orEmpty()
            if (name == null || text.isEmpty()) {
                record(label, own)
                return@withTransaction 0
            }
            val (aliasPart, renamed) = createAlias(text, name, exceptId = transactionId)
            record(label, own + aliasPart)
            renamed
        }

    /**
     * Crée l'alias [pattern] → [name] (il remplace un alias au même motif) et l'applique aux
     * opérations sans nom ; rend ce qu'il faut à l'Historique et le nombre d'opérations renommées.
     */
    private suspend fun createAlias(pattern: String, name: String, exceptId: Long? = null): Pair<JournalPayload, Int> {
        val aliasDao = database.aliasDao()
        val key = normalizeLabel(pattern)
        val replaced = aliasDao.all().filter { normalizeLabel(it.pattern) == key }
        aliasDao.delete(replaced.map { it.id })
        val created = aliasDao.insert(LabelAliasEntity(pattern = pattern, alias = name))
        val single = listOf(LabelAlias(0, pattern, name))
        val renamed = transactionDao.withoutAlias()
            .filter { it.id != exceptId && single.aliasFor(it.cleanLabel) != null }
            .onEach { transactionDao.setAlias(it.id, name) }
        return JournalPayload(
            aliasRowsCreated = listOf(created),
            aliasRowsRemoved = replaced,
            aliasesBefore = renamed.map { AliasState(it.id, null) },
        ) to renamed.size
    }

    /**
     * Déclasse les opérations bancaires du mois [month] : toutes, celles d'une enveloppe, ou celles
     * dont le libellé (ou l'alias) contient un texte. Elles repassent « à trier » ; les saisies
     * manuelles gardent leur enveloppe. Rend le nombre d'opérations déclassées.
     */
    suspend fun unsortMonth(month: YearMonth, scope: UnsortScope): Int = database.withTransaction {
        val rows = transactionDao.sortedBankBetween(month.atDay(1), month.atEndOfMonth())
        val selected = when (scope) {
            UnsortScope.All -> rows
            is UnsortScope.Envelope -> rows.filter { it.envelopeId == scope.envelopeId }
            is UnsortScope.Containing -> {
                val text = normalizeLabel(scope.text)
                if (text.isEmpty()) emptyList()
                else rows.filter { normalizeLabel(it.label).contains(text) || normalizeLabel(it.alias.orEmpty()).contains(text) }
            }
        }.map { it.id }.distinct()
        val before = snapshot(selected)
        selected.forEach { imputationDao.deleteForTransaction(it) }
        record(if (selected.size == 1) "Une opération remise à trier" else "${selected.size} opérations remises à trier", JournalPayload(operations = before))
        selected.size
    }

    /** Range les opérations à trier avec les règles existantes. Rend le nombre d'opérations rangées. */
    suspend fun applyRules(): Int = database.withTransaction {
        val envelopes = activeEnvelopes().associateBy { it.id }
        val rules = database.ruleDao().all().map { it.toDomain() }
        val transferRules = database.transferRuleDao().all().map { TransferRule(it.id, it.pattern, it.accountId) }
        transactionDao.unsorted()
            .filter { it.source != TransactionSource.MANUAL }
            .mapNotNull { transaction ->
                transferRules.transferFor(transaction)?.let { transfer ->
                    transactionDao.setIgnored(transaction.id, true)
                    transactionDao.setTransferAccount(transaction.id, transfer.accountId)
                    return@mapNotNull transaction.id
                }
                val envelope = rules.matchingFor(transaction)?.let { envelopes[it.envelopeId] } ?: return@mapNotNull null
                imputationDao.insert(transaction.imputationIn(envelope, ImputationMode.RULE))
            }
            .size
    }

    /**
     * Applique le tri validé par l'utilisateur après la proposition de Claude, en une transaction :
     * crée les nouvelles enveloppes (à 0 €, à budgéter), range chaque opération, la renomme et crée
     * les règles demandées. Rend le nombre d'opérations traitées et les enveloppes créées.
     */
    suspend fun applyAssistedSort(choices: List<AssistedSortChoice>): AssistedSortResult = batched("Tri avec Claude (${choices.size})") { applyAssistedSortUnbatched(choices) }

    private suspend fun applyAssistedSortUnbatched(choices: List<AssistedSortChoice>): AssistedSortResult = database.withTransaction {
        val active = activeEnvelopes()
        val byKey = active.associateBy { envelopeNameKey(it.name) }.toMutableMap()
        val created = mutableListOf<String>()
        for (choice in choices) {
            if (choice.alias != null) renameOperation(choice.transactionId, choice.alias, pattern = choice.keyword)
            when (val target = choice.target) {
                ProposalTarget.OutOfBudget -> ignoreOperation(choice.transactionId)
                is ProposalTarget.Account -> {
                    val account = accountDao.active().firstOrNull { envelopeNameKey(it.name) == envelopeNameKey(target.name) }
                    if (account != null) {
                        markTransferToAccount(choice.transactionId, account.id, choice.keyword)
                    } else {
                        ignoreOperation(choice.transactionId)
                    }
                }
                is ProposalTarget.Existing, is ProposalTarget.New -> {
                    val name = if (target is ProposalTarget.Existing) target.name else (target as ProposalTarget.New).name
                    val envelope = byKey[envelopeNameKey(name)] ?: run {
                        val type = when (target) {
                            is ProposalTarget.New -> typeForFamily(target.family)
                            else -> EnvelopeType.DAILY
                        }
                        val draft = Envelope(
                            id = 0,
                            name = name,
                            type = type,
                            frequency = type.defaultFrequency,
                            amount = Money.ZERO,
                            status = type.defaultStatus,
                            anchor = if (type.defaultFrequency.isCalendar) null else LocalDate.now(),
                            group = (target as? ProposalTarget.New)?.category,
                            // Une provision créée maintenant met de côté à partir de ce mois.
                            fundingStart = if (type == EnvelopeType.ANNUAL || type == EnvelopeType.RECURRING) YearMonth.now() else null,
                        )
                        val id = saveEnvelope(draft)
                        created += name
                        draft.copy(id = id).also { byKey[envelopeNameKey(name)] = it }
                    }
                    sortOperation(choice.transactionId, envelope, choice.keyword)
                }
            }
        }
        AssistedSortResult(sorted = choices.size, createdEnvelopes = created)
    }

    /** Le total d'une opération (positif) et ses parts actuelles (enveloppe, montant positif). */
    suspend fun operationSplit(transactionId: Long): OperationSplit? {
        val transaction = transactionDao.byId(transactionId) ?: return null
        val sign = if (transaction.amountCents < 0) 1 else -1
        val parts = imputationDao.forTransaction(transactionId).map { it.envelopeId to Money(it.amountCents * sign) }
        return OperationSplit(Money(kotlin.math.abs(transaction.amountCents)), parts)
    }

    /**
     * Répartit une opération entre plusieurs enveloppes ([parts] : montants positifs dont la somme
     * fait le total). Remplace ses parts précédentes ; une saisie répartie garde sa répartition quand
     * la ligne de la banque la remplace. Faux si la répartition ne colle pas au total.
     */
    suspend fun splitOperation(transactionId: Long, parts: List<Pair<Envelope, Money>>): Boolean = database.withTransaction {
        val transaction = transactionDao.byId(transactionId) ?: return@withTransaction false
        val total = Money(kotlin.math.abs(transaction.amountCents))
        if (!fr.baptiste.bank.domain.bank.isValidSplit(total, parts.map { it.second })) return@withTransaction false
        val sign = if (transaction.amountCents < 0) 1 else -1
        val before = snapshot(listOf(transactionId))
        imputationDao.deleteForTransaction(transactionId)
        transactionDao.setIgnored(transactionId, false)
        transactionDao.setTransferAccount(transactionId, null)
        record("${transaction.alias ?: transaction.cleanLabel} répartie en ${parts.size}", JournalPayload(operations = before))
        parts.forEach { (envelope, amount) ->
            imputationDao.insert(
                ImputationEntity(
                    transactionId = transactionId,
                    envelopeId = envelope.id,
                    periodKey = envelope.periodContaining(transaction.date).key,
                    amountCents = amount.cents * sign,
                    mode = ImputationMode.MANUAL,
                ),
            )
        }
        true
    }

    /** Le pont avec le skill Claude : export anonymisé, lecture et application d'un plan. */
    val claude: ClaudeBridge by lazy { ClaudeBridge(database, settings, this) }

    /**
     * Crée une règle « tout libellé qui contient [pattern] va dans [envelopeName] » (sans opération de
     * départ : un plan de Claude). Un même motif ne mène qu'à une enveloppe. Faux si l'enveloppe n'existe pas.
     */
    suspend fun addRule(pattern: String, envelopeName: String): Boolean = database.withTransaction {
        val text = pattern.trim().takeIf { it.isNotEmpty() } ?: return@withTransaction false
        val envelope = activeEnvelopes().firstOrNull { envelopeNameKey(it.name) == envelopeNameKey(envelopeName) }
            ?: return@withTransaction false
        val ruleDao = database.ruleDao()
        val key = normalizeLabel(text)
        ruleDao.delete(ruleDao.all().filter { normalizeLabel(it.pattern) == key }.map { it.id })
        ruleDao.insert(RuleEntity(matchType = RuleMatchType.CONTAINS, pattern = text, envelopeId = envelope.id))
        true
    }

    /** Crée un nom affiché (alias) pour tout libellé qui contient [pattern], et l'applique aux opérations sans nom. */
    suspend fun addAlias(pattern: String, name: String): Int = database.withTransaction {
        val text = pattern.trim()
        val alias = name.trim()
        if (text.isEmpty() || alias.isEmpty()) return@withTransaction 0
        val (part, renamed) = createAlias(text, alias)
        record("Nom « $alias » pour « $text »", part)
        renamed
    }

    /** Met une opération hors budget : elle reste visible mais n'est comptée dans aucune enveloppe. */
    /** Opérations de la banque à trier, toutes. */
    val unsortedOperations: Flow<List<OperationRow>> = combine(
        transactionDao.observeUnsortedOperations(),
        transactionDao.observeUnsortedInflowLabels(),
        settings.salary,
    ) { operations, inflows, salary ->
        // Le salaire reconnu n'est pas une dépense : il se range seul dans le revenu, il n'est pas à trier.
        val key = salary?.first ?: return@combine operations
        val salaryIds = inflows.filter { fr.baptiste.bank.domain.budget.salaryKey(it.label) == key }.map { it.id }.toSet()
        operations.filter { it.id !in salaryIds }
    }

    /**
     * Valide plusieurs propositions d'un coup : chaque opération va dans son enveloppe, ou hors budget
     * (enveloppe null). Renvoie le nombre d'opérations rangées.
     */
    suspend fun acceptProposals(choices: List<Pair<Long, Envelope?>>): Int = database.withTransaction {
        record(
            if (choices.size == 1) "${operationLabel(choices.first().first)} → ${choices.first().second?.name ?: "hors budget"}" else "${choices.size} opérations rangées",
            JournalPayload(operations = snapshot(choices.map { it.first })),
        )
        var done = 0
        for ((transactionId, envelope) in choices) {
            val transaction = transactionDao.byId(transactionId) ?: continue
            imputationDao.deleteForTransaction(transactionId)
            transactionDao.setTransferAccount(transactionId, null)
            if (envelope == null) {
                transactionDao.setIgnored(transactionId, true)
            } else {
                transactionDao.setIgnored(transactionId, false)
                imputationDao.insert(transaction.imputationIn(envelope, ImputationMode.MANUAL))
            }
            done++
        }
        done
    }

    /** Remet des opérations « à trier » (annulation d'une validation). */
    suspend fun unsortOperations(ids: List<Long>) = database.withTransaction {
        record(
            if (ids.size == 1) "${operationLabel(ids.first())} remise à trier" else "${ids.size} opérations remises à trier",
            JournalPayload(operations = snapshot(ids)),
        )
        for (id in ids) {
            imputationDao.deleteForTransaction(id)
            transactionDao.setIgnored(id, false)
            transactionDao.setTransferAccount(id, null)
        }
    }

    suspend fun ignoreOperation(transactionId: Long) {
        database.withTransaction {
            record("${operationLabel(transactionId)} hors budget", JournalPayload(operations = snapshot(listOf(transactionId))))
            imputationDao.deleteForTransaction(transactionId)
            transactionDao.setIgnored(transactionId, true)
            transactionDao.setTransferAccount(transactionId, null)
        }
    }

    /**
     * Virement vers un de mes comptes (livret, investissement) : hors budget, mais compté comme
     * versé sur ce compte. L'argent ne quitte pas mes poches, ce n'est pas une dépense.
     */
    suspend fun markTransferToAccount(transactionId: Long, accountId: Long, keyword: String? = null): Int =
        database.withTransaction {
            val before = snapshot(listOf(transactionId))
            val label = "Virement vers ${accountDao.byId(accountId)?.name.orEmpty()}"
            imputationDao.deleteForTransaction(transactionId)
            transactionDao.setIgnored(transactionId, true)
            transactionDao.setTransferAccount(transactionId, accountId)
            val text = keyword?.trim().orEmpty()
            if (text.isEmpty()) {
                record(label, JournalPayload(operations = before))
                return@withTransaction 0
            }
            // Les suivants seront reconnus seuls ; un même motif ne mène qu'à un compte.
            val dao = database.transferRuleDao()
            val key = normalizeLabel(text)
            dao.delete(dao.all().filter { normalizeLabel(it.pattern) == key }.map { it.id })
            val ruleId = dao.insert(TransferRuleEntity(pattern = text, accountId = accountId))
            val single = listOf(TransferRule(0, text, accountId))
            val others = transactionDao.unsorted()
                .filter { it.source != TransactionSource.MANUAL && it.id != transactionId && single.transferFor(it) != null }
                .onEach {
                    transactionDao.setIgnored(it.id, true)
                    transactionDao.setTransferAccount(it.id, accountId)
                }
            record(label, JournalPayload(operations = before + others.map { unsortedState(it.id) }, transferRulesCreated = listOf(ruleId)))
            others.size
        }

    /** Règles de virement, pour l'écran Règles et alias. */
    val transferRules: Flow<List<RuleRow>> = database.transferRuleDao().observeAll()

    suspend fun deleteTransferRule(id: Long) = database.transferRuleDao().delete(listOf(id))

    // --- Autres comptes : livrets, investissement ---

    private val accountDao = database.accountDao()
    private val balanceDao = database.balanceDao()

    /** Comptes d'épargne et d'investissement (pas le compte courant). */
    val holdingAccounts: Flow<List<HoldingAccount>> = accountDao.observeActive().map { rows ->
        rows.filter { it.type != AccountType.CURRENT }
            .map {
                HoldingAccount(
                    it.id,
                    it.name,
                    it.type,
                    linked = it.externalUid != null,
                    invested = it.investedCents?.let(::Money),
                    taxWrapper = it.taxWrapper,
                    monthlyTarget = it.monthlyTargetCents?.let(::Money),
                )
            }
    }

    /** Ce qui est de côté sur ces comptes, six mois d'historique, et les virements du mois vers eux. */
    fun holdings(today: LocalDate = LocalDate.now()): Flow<Holdings> {
        val month = YearMonth.from(today)
        return combine(
            holdingAccounts,
            balanceDao.observeAll(),
            transactionDao.observeTransfersByAccount(month.atDay(1), month.atEndOfMonth()),
        ) { accounts, balances, transfers ->
            computeHoldings(
                accounts = accounts,
                snapshots = balances.map { BalanceSnapshot(it.accountId, it.date, Money(it.balanceCents)) },
                today = today,
                months = 12,
                transfersThisMonth = transfers.map { Money(it.amountCents) },
                transfersByAccount = transfers.map { fr.baptiste.bank.domain.budget.AccountTransfer(it.accountId, it.date, Money(it.amountCents)) },
            )
        }
    }

    /**
     * Début du suivi : le mois choisi par l'utilisateur, sinon le premier mois avec une opération
     * (sinon le mois de [today]). Avant lui, un mois est « non renseigné » : ni bilan, ni moyenne.
     */
    fun trackingStart(today: LocalDate = LocalDate.now()): Flow<YearMonth> =
        combine(settings.trackingStart, transactionDao.observeFirstDate()) { chosen, first ->
            chosen ?: YearMonth.from(first ?: today)
        }

    /** Mois écartés des moyennes : l'option de chaque mois, ou par défaut un mois incomplet (voir excludedMonths). */
    val excludedMonths: Flow<Set<YearMonth>> = combine(settings.monthCounts, transactionDao.observeFirstDate()) { choices, first ->
        fr.baptiste.bank.domain.budget.excludedMonths(choices, first)
    }

    /**
     * Rappel du bilan du mois précédent sur le Résumé, la première semaine du mois (doc UX, « Fin
     * de période ») : l'épargne de ce mois, tant que le rappel n'a pas été fermé. Null sinon.
     */
    fun previousMonthRecap(today: LocalDate = LocalDate.now()): Flow<MonthSavings?> =
        combine(savingsHistory(today, 2), settings.recapDismissed) { months, dismissed ->
            // La date est relue à chaque mise à jour : l'encart disparaît le 8 même si l'app reste ouverte.
            val month = fr.baptiste.bank.domain.budget.recapMonth(LocalDate.now(), dismissed)
            months.firstOrNull { it.month == month }
        }

    suspend fun dismissRecap(today: LocalDate = LocalDate.now()) = settings.setRecapDismissed(YearMonth.from(today).minusMonths(1))

    suspend fun setMonthCounted(month: YearMonth, counted: Boolean) {
        val before = settings.monthCounts.first()[month]
        settings.setMonthCounted(month, counted)
        val name = month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRANCE)
        record(if (counted) "$name : mois de référence" else "$name : hors référence", JournalPayload(monthCountsBefore = listOf(MonthCountState(month, before))))
    }

    /** Date de la première opération (null sans opération) : dit si le premier mois est incomplet. */
    fun firstOperationDate(): Flow<LocalDate?> = transactionDao.observeFirstDate()

    suspend fun setTrackingStart(month: YearMonth?) = settings.setTrackingStart(month)

    fun monthReview(month: YearMonth): Flow<String?> = settings.monthReview(month)

    /** Dépenses qui reviennent chaque mois, sur les six derniers mois. */
    fun recurringExpenses(today: LocalDate = LocalDate.now()): Flow<List<fr.baptiste.bank.domain.budget.RecurringExpense>> =
        transactionDao.observeOutflows(YearMonth.from(today).minusMonths(5).atDay(1), today).map { rows ->
            fr.baptiste.bank.domain.budget.detectRecurring(
                rows.map { fr.baptiste.bank.domain.budget.Outflow(it.label, it.date, Money(it.amountCents)) },
            )
        }

    suspend fun setMonthReview(month: YearMonth, text: String?) = settings.setMonthReview(month, text)

    /** Sorties d'argent comptées de [month], avec leur nom (habitudes de l'analyse complète). */
    fun monthOutflows(month: YearMonth): Flow<List<fr.baptiste.bank.domain.budget.Outflow>> =
        transactionDao.observeOutflows(month.atDay(1), month.atEndOfMonth()).map { rows ->
            rows.map { fr.baptiste.bank.domain.budget.Outflow(it.label, it.date, Money(it.amountCents)) }
        }

    /**
     * Dépenses réelles de [year], mois par mois, par catégorie et par enveloppe (archivées comprises :
     * leurs dépenses restent), avec ce qui reste à trier chaque mois. Voir [yearSpending].
     */
    fun spendingYear(year: Int, today: LocalDate = LocalDate.now()): Flow<YearSpending> {
        val from = LocalDate.of(year, 1, 1)
        val to = LocalDate.of(year, 12, 31)
        val all = combine(envelopes, archivedEnvelopes) { active, archived -> active + archived }
        val spendings = imputationDao.observeSpendings(from, to)
            .map { rows -> rows.map { Spending(it.envelopeId, it.date, Money(it.amountCents), it.wholePeriod) } }
        val unsorted = transactionDao.observeUnsortedOutRows(from, to).map { rows ->
            rows.groupBy { YearMonth.from(it.date) }.mapValues { (_, list) -> Money(list.sumOf { it.amountCents }) }
        }
        val excluded = transactionDao.observeExcludedOutRows(from, to)
        // Les ajouts et retraits changent le budget de chaque enveloppe dans son mois, comme dans Budget.
        val transfers = transferDao.observeActive(from, to).map { rows -> rows.map { it.toDomain() } }
        val context = combine(trackingStart(today), excludedMonths, categoryParents, transfers) { start, months, parents, moves ->
            SpendingContext(start, months, parents, moves)
        }
        return combine(all, spendings, unsorted, excluded, context) { envelopes, spent, toSort, out, ctx ->
            fun byMonth(rows: List<fr.baptiste.bank.data.local.ExcludedAmount>) =
                rows.groupBy { YearMonth.from(it.date) }.mapValues { (_, list) -> Money(list.sumOf { it.amountCents }) }
            val (toAccounts, outOfBudget) = out.partition { it.toAccount }
            yearSpending(year, today, ctx.start, envelopes, spent, toSort, byMonth(outOfBudget), byMonth(toAccounts), ctx.excluded, ctx.parents, ctx.transfers)
        }
    }

    private data class SpendingContext(
        val start: YearMonth,
        val excluded: Set<YearMonth>,
        val parents: Map<String, String>,
        val transfers: List<fr.baptiste.bank.domain.budget.Transfer>,
    )

    /** Rattrapage d'une provision : elle met de côté tout son montant à partir de [from]. */
    suspend fun startFunding(envelopeId: Long, from: YearMonth?) {
        val envelope = activeEnvelopes().firstOrNull { it.id == envelopeId } ?: return
        // Les mois d'avant gardent leur part ; ce qui manque se répartit à partir de [from].
        database.withTransaction {
            val before = envelopeState(envelopeId)
            saveEnvelopeUnrecorded(envelope.copy(catchUpFrom = from))
            val month = from?.month?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRANCE)
            record("${envelope.name} : rattrapage à partir de ${month.orEmpty()}", before)
        }
    }

    /** Dépense du quotidien des douze derniers mois terminés et renseignés au plus (référence du simulateur). */
    fun dailySpendingHistory(today: LocalDate = LocalDate.now()): Flow<List<fr.baptiste.bank.domain.budget.MonthDailySpending>> {
        val current = YearMonth.from(today)
        val from = current.minusMonths(12).atDay(1)
        val to = current.minusMonths(1).atEndOfMonth()
        val all = combine(envelopes, archivedEnvelopes) { active, archived -> active + archived }
        val spendings = imputationDao.observeSpendings(from, to)
            .map { rows -> rows.map { Spending(it.envelopeId, it.date, Money(it.amountCents), it.wholePeriod) } }
        return combine(all, spendings, trackingStart(today), excludedMonths) { envelopes, spent, start, excluded ->
            fr.baptiste.bank.domain.budget.dailySpendingHistory(envelopes, spent, current, start, excluded = excluded)
        }
    }

    /**
     * Applique un plan essayé : le projet devient un achat prévu (s'il y en a un), et l'épargne
     * mensuelle change si elle tient dans une seule enveloppe d'épargne.
     */
    suspend fun applyPlan(
        name: String,
        project: Money,
        months: Int,
        monthlySavings: Money?,
        reductions: Map<Long, Money> = emptyMap(),
    ) = batched("Plan appliqué : $name") { database.withTransaction {
        val today = LocalDate.now()
        // Réductions : un montant plus bas pour chacune des périodes du projet, pas pour toujours.
        if (reductions.isNotEmpty()) {
            // Les périodes réduites : l'historique garde celles d'avant.
            reductions.keys.forEach { record("", envelopeState(it)) }
            val byId = activeEnvelopes().associateBy { it.id }
            val allocationDao = database.allocationDao()
            for ((id, cut) in reductions) {
                val envelope = byId[id] ?: continue
                val periods = (0 until months.coerceAtLeast(1)).map { offset ->
                    envelope.periodContaining(YearMonth.from(today).plusMonths(offset.toLong()).atDay(15))
                }.distinctBy { it.key }
                for (period in periods) {
                    allocationDao.delete(id, period.key)
                    val amount = (envelope.amount - cut).let { if (it.isNegative) Money.ZERO else it }
                    allocationDao.insert(
                        AllocationEntity(
                            envelopeId = id,
                            periodKey = period.key,
                            amountCents = amount.cents,
                            startDate = period.start,
                            endDate = period.endInclusive,
                        ),
                    )
                }
            }
        }
        if (project.isPositive && name.isNotBlank()) {
            saveEnvelope(fr.baptiste.bank.domain.budget.plannedPurchase(name.trim(), project, YearMonth.from(today).plusMonths(months.coerceAtLeast(1) - 1L), today))
        }
        if (monthlySavings != null) {
            val savings = activeEnvelopes().filter { it.type == EnvelopeType.SAVINGS }
            savings.singleOrNull()?.let { envelope ->
                saveEnvelope(envelope.copy(amount = monthlySavings, frequency = fr.baptiste.bank.domain.period.Frequency.MONTHLY))
            }
        }
    } }

    /** Opérations d'une enveloppe sur un mois, avec la part qui lui revient (analyse des dépenses). */
    fun envelopeMonthOperations(envelopeId: Long, month: YearMonth): Flow<List<OperationRow>> =
        transactionDao.observeEnvelopeOperationsBetween(envelopeId, month.atDay(1), month.atEndOfMonth())

    /** Dernier solde du compte courant : lu à la banque ou saisi à la main. */
    val currentAccountBalance: Flow<BalanceSnapshot?> = balanceDao.observeCurrentAccountBalance()
        .map { it?.let { row -> BalanceSnapshot(row.accountId, row.date, Money(row.balanceCents)) } }

    /** Saisit le solde du compte courant (sans banque connectée, ou pour corriger). */
    suspend fun setCurrentAccountBalance(balance: Money) {
        balanceDao.upsert(BalanceEntity(accountId = currentAccountId(), date = LocalDate.now(), balanceCents = balance.cents))
    }

    /** Virements vers mes livrets et placements sur un mois (total positif). */
    fun transfersToAccounts(month: YearMonth): Flow<Money> =
        transactionDao.observeTransfersToAccounts(month.atDay(1), month.atEndOfMonth())
            .map { amounts -> Money(amounts.sumOf { kotlin.math.abs(it) }) }

    /**
     * Le solde du compte courant décomposé : reste du mois, charges à payer, épargne à virer,
     * provisions mises de côté, et ce qui reste libre. Null tant qu'aucun solde n'est connu.
     */
    fun currentAccountExplained(today: LocalDate = LocalDate.now()): Flow<CurrentAccountExplained?> {
        val month = YearMonth.from(today)
        val startAndSalary = combine(trackingStart(today), settings.salary, recentInflows(today)) { start, salary, inflows -> Triple(start, salary, inflows) }
        return combine(
            dashboard(today),
            currentAccountBalance,
            transfersToAccounts(month),
            transactionDao.observeUnsortedOut(month.atDay(1), month.atEndOfMonth()),
            startAndSalary,
        ) { board, balance, transferred, unsorted, (start, salary, inflows) ->
            balance?.let {
                // Le salaire de fin de mois déjà sur le compte au jour du solde finance le mois prochain.
                val nextSalary = salary?.let { (key, next) -> fr.baptiste.bank.domain.budget.nextMonthSalaryReceived(it.date, inflows, key, next) } ?: Money.ZERO
                board.explainCurrentAccount(it.balance, it.date, transferred, Money(unsorted), since = start.atDay(1), nextMonthSalary = nextSalary)
            }
        }
    }

    val savingsGoals: Flow<SavingsGoals> = combine(settings.savingsGoals, settings.savingsGoalBy) { (precaution, total), by ->
        SavingsGoals(precaution, total, by)
    }

    suspend fun setSavingsGoals(goals: SavingsGoals) = settings.setSavingsGoals(goals.precaution, goals.total, goals.totalBy)

    /** Ajoute un compte saisi à la main, avec son solde du jour. */
    suspend fun addManualAccount(name: String, type: AccountType, balance: Money): Long = database.withTransaction {
        val id = accountDao.insert(AccountEntity(name = name.trim(), type = type))
        balanceDao.upsert(BalanceEntity(accountId = id, date = LocalDate.now(), balanceCents = balance.cents))
        id
    }

    /** Nouveau solde d'un compte saisi à la main, à la date du jour. */
    suspend fun updateBalance(accountId: Long, balance: Money, date: LocalDate = LocalDate.now()) {
        balanceDao.upsert(BalanceEntity(accountId = accountId, date = date, balanceCents = balance.cents))
    }

    suspend fun renameAccount(accountId: Long, name: String, type: AccountType) =
        accountDao.rename(accountId, name.trim(), type)

    suspend fun archiveAccount(accountId: Long) = accountDao.archive(accountId)

    /** Placement : montant versé et enveloppe fiscale, pour le net estimé. */
    suspend fun setInvestment(accountId: Long, invested: Money?, wrapper: fr.baptiste.bank.domain.budget.TaxWrapper?) =
        accountDao.setInvestment(accountId, invested?.cents, wrapper)

    suspend fun setMonthlyTarget(accountId: Long, target: Money?) =
        accountDao.setMonthlyTarget(accountId, target?.takeIf { it.isPositive }?.cents)

    /** « Cumul du mois » de la liste des opérations : voir cumulativeByDay. */
    fun monthCumulative(month: YearMonth): Flow<Map<LocalDate, Money>> {
        val known = combine(envelopes, archivedEnvelopes) { active, archived -> active + archived }
        val spent = imputationDao.observeSpendings(month.atDay(1), month.atEndOfMonth())
            .map { rows -> rows.map { Spending(it.envelopeId, it.date, Money(it.amountCents), it.wholePeriod) } }
        return combine(known, spent) { all, spendings -> fr.baptiste.bank.domain.budget.cumulativeByDay(month, all, spendings) }
    }

    /** Le mois en cours : dépensé par jour (hors épargne, comme le total du mois) et budget du mois. */
    fun monthPace(today: LocalDate = LocalDate.now()): Flow<fr.baptiste.bank.domain.budget.MonthPace?> {
        val month = YearMonth.from(today)
        val known = combine(envelopes, archivedEnvelopes) { active, archived -> active + archived }
        // Le mois précédent aussi : en début de mois, on se compare à la même date du mois d'avant.
        val spent = imputationDao.observeSpendings(month.minusMonths(1).atDay(1), month.atEndOfMonth())
        val year = combine(spendingYear(today.year, today), trackingStart(today)) { year, start -> year to start }
        return combine(year, spent, known) { (year, start), rows, all ->
            val previous = month.minusMonths(1)
            val report = year.months.firstOrNull { it.month == month } ?: return@combine null
            // Même règle que le total du mois de l'analyse (countedSpendings), pour ce mois et le précédent.
            val spendings = rows.map { Spending(it.envelopeId, it.date, Money(it.amountCents), it.wholePeriod) }
            val counted = (fr.baptiste.bank.domain.budget.countedSpendings(month.minusMonths(1), all, spendings) +
                fr.baptiste.bank.domain.budget.countedSpendings(month, all, spendings))
                .map { it.date to it.amount }
            fr.baptiste.bank.domain.budget.monthPace(month, today, counted, report.budget, previousTracked = previous >= start && previous !in year.excluded)
        }
    }

    /** Solde lu à la banque pour un compte suivi (livret relié) : crée le compte au premier relevé. */
    suspend fun recordLinkedBalance(uid: String, name: String, ibanSuffix: String?, type: AccountType, balance: Money) {
        database.withTransaction {
            val id = accountDao.findByExternalUid(uid)?.id
                ?: accountDao.insert(AccountEntity(name = name, type = type, ibanSuffix = ibanSuffix, externalUid = uid))
            balanceDao.upsert(BalanceEntity(accountId = id, date = LocalDate.now(), balanceCents = balance.cents))
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
/** Épargne d'un mois ; [counted] faux pour un mois écarté des moyennes (il ne compte pas dans l'année). */
data class MonthSavings(val month: YearMonth, val outlook: SavingsOutlook, val counted: Boolean = true)

/**
 * Bilan d'une année.
 * @property monthsCounted mois pris en compte : écoulés (le mois en cours compris) et postérieurs à la
 *   première opération enregistrée.
 */
data class YearOverview(
    val year: Int,
    val months: List<MonthSavings>,
    val spentByEnvelope: Map<Long, Money>,
    val dashboard: Dashboard,
    val monthsCounted: Int,
) {
    /** Épargne de l'année : réelle pour les mois passés, prévue pour le mois en cours. */
    val savings: Money get() = months.filter { it.counted }.fold(Money.ZERO) { total, month -> total + month.outlook.forecast }
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

private fun LabelAliasEntity.toDomain() = LabelAlias(id, pattern, alias)

/** Règle de virement pour une opération : sur le libellé de la banque, ou à défaut sur son nom. */
private fun List<TransferRule>.transferFor(transaction: TransactionEntity): TransferRule? =
    transferFor(transaction.cleanLabel) ?: transaction.alias?.let { transferFor(it) }

/** Une règle s'applique au libellé de la banque, ou à défaut au nom choisi par l'utilisateur. */
private fun List<LabelRule>.matchingFor(transaction: TransactionEntity): LabelRule? =
    matching(transaction.cleanLabel) ?: transaction.alias?.let { matching(it) }

/** Un choix validé du tri assisté : où ranger l'opération, et facultativement son nom et sa règle. */
data class AssistedSortChoice(
    val transactionId: Long,
    val target: ProposalTarget,
    val alias: String?,
    val keyword: String?,
)

data class AssistedSortResult(val sorted: Int, val createdEnvelopes: List<String>)

/** Famille proposée par Claude (vocabulaire de l'app) vers le type du calcul. */
internal fun typeForFamily(family: String): EnvelopeType {
    val key = envelopeNameKey(family)
    return when {
        key.startsWith(envelopeNameKey("charge")) -> EnvelopeType.FIXED
        // « budgets rares et ponctuels » (ancien vocabulaire : « provisions »).
        key.contains(envelopeNameKey("rare")) || key.contains(envelopeNameKey("ponctuel")) || key.startsWith(envelopeNameKey("provision")) -> EnvelopeType.ANNUAL
        key.startsWith(envelopeNameKey("épargne")) || key.startsWith(envelopeNameKey("epargne")) -> EnvelopeType.SAVINGS
        else -> EnvelopeType.DAILY
    }
}

/** Ce qu'on déclasse dans un mois. */
sealed interface UnsortScope {
    data object All : UnsortScope
    data class Envelope(val envelopeId: Long) : UnsortScope
    data class Containing(val text: String) : UnsortScope
}

private fun TransferEntity.toDomain() = Transfer(
    id = id,
    date = effectiveDate,
    sourceEnvelopeId = sourceEnvelopeId,
    targetEnvelopeId = targetEnvelopeId,
    amount = Money(amountCents),
    reason = reason,
    fromReserve = fromReserve,
)

private fun EnvelopeEntity.toDomain(allocations: List<AllocationEntity> = emptyList()) = Envelope(
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
    fundingStart = fundingStart?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
    catchUpFrom = catchUpFrom?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
    start = startDate,
    firstAmount = firstAmountCents?.let(::Money),
    firstReset = firstReset,
    repeats = repeats,
    retiredOn = retiredOn,
    periods = allocations
        .filter { it.envelopeId == id && it.startDate != null && it.endDate != null }
        .map { BudgetPeriod(it.periodKey, it.startDate!!, it.endDate!!, Money(it.amountCents)) }
        .sortedBy { it.start },
)

/** Enveloppes avec leurs périodes enregistrées. */
private fun List<EnvelopeEntity>.withPeriods(allocations: List<AllocationEntity>): List<Envelope> {
    val byEnvelope = allocations.groupBy { it.envelopeId }
    return map { it.toDomain(byEnvelope[it.id].orEmpty()) }
}

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
    fundingStart = fundingStart?.toString(),
    catchUpFrom = catchUpFrom?.toString(),
    startDate = start,
    firstAmountCents = firstAmount?.cents,
    firstReset = firstReset,
    repeats = repeats,
    retiredOn = retiredOn,
)

/** Ce qui est rangé dans une enveloppe : annoncé avant une suppression définitive. */
data class EnvelopeUsage(val bankCount: Int, val bankAmount: Money, val manualCount: Int, val manualAmount: Money)

/** Une opération et ses parts : le total (positif) et, par enveloppe, le montant (positif). */
data class OperationSplit(val total: Money, val parts: List<Pair<Long, Money>>)
