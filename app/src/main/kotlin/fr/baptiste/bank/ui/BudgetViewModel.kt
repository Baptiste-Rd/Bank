package fr.baptiste.bank.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.baptiste.bank.BankApplication
import fr.baptiste.bank.data.BudgetRepository
import fr.baptiste.bank.data.AssistedSortChoice
import fr.baptiste.bank.data.AssistedSortResult
import fr.baptiste.bank.data.MonthSavings
import fr.baptiste.bank.data.UnsortScope
import fr.baptiste.bank.data.YearOverview
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.local.RuleRow
import fr.baptiste.bank.domain.bank.LabelAlias
import fr.baptiste.bank.domain.bank.SortedLabel
import fr.baptiste.bank.domain.bank.suggestEnvelope
import fr.baptiste.bank.data.local.TransferRow
import fr.baptiste.bank.domain.budget.BalanceSnapshot
import fr.baptiste.bank.domain.budget.CurrentAccountExplained
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.SavingsGoals
import fr.baptiste.bank.domain.budget.HoldingAccount
import fr.baptiste.bank.domain.budget.Holdings
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import fr.baptiste.bank.domain.budget.YearSpending
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * ViewModel partagé par les trois onglets. Chaque flux est recalculé dès que la base change :
 * une dépense saisie met à jour l'accueil, la liste des enveloppes et les opérations en même temps.
 */
class BudgetViewModel(private val repository: BudgetRepository) : ViewModel() {

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** null pendant le premier chargement. */
    val dashboard: StateFlow<Dashboard?> = repository.dashboard().asState(null)

    val envelopes: StateFlow<List<Envelope>> = repository.envelopes.asState(emptyList())

    /** Épargne des 6 derniers mois, le mois en cours en dernier. */
    val savingsHistory: StateFlow<List<MonthSavings>> = repository.savingsHistory().asState(emptyList())

    /*
     * Mois consulté dans les onglets Budget et Épargne. Le mois en cours se calcule à aujourd'hui ;
     * un mois passé, à son dernier jour : ses chiffres sont alors définitifs. L'accueil, lui, reste
     * toujours sur aujourd'hui.
     */
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private fun YearMonth.referenceDate(): LocalDate =
        if (this == YearMonth.now()) LocalDate.now() else atEndOfMonth()

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthDashboard: StateFlow<Dashboard?> = _selectedMonth
        .flatMapLatest { repository.dashboard(it.referenceDate()) }
        .asState(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthSavingsHistory: StateFlow<List<MonthSavings>> = _selectedMonth
        .flatMapLatest { repository.savingsHistory(it.referenceDate()) }
        .asState(emptyList())

    /** Mois précédent ; au plus 36 mois en arrière. */
    fun previousMonth() {
        _selectedMonth.update { if (it > YearMonth.now().minusMonths(36)) it.minusMonths(1) else it }
    }

    /** Année consultée dans la vue à l'année de l'onglet Budget. */
    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val yearOverview: StateFlow<YearOverview?> = _selectedYear
        .flatMapLatest { repository.yearOverview(it) }
        .asState(null)

    fun previousYear() = _selectedYear.update { if (it > LocalDate.now().year - 3) it - 1 else it }

    fun nextYear() = _selectedYear.update { if (it < LocalDate.now().year) it + 1 else it }

    /** Choisit un mois (au plus le mois en cours), depuis l'histogramme de l'année. */
    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = minOf(month, YearMonth.now())
    }

    /** Mois suivant, sans dépasser le mois en cours. */
    fun nextMonth() {
        _selectedMonth.update { if (it < YearMonth.now()) it.plusMonths(1) else it }
    }

    val operations: StateFlow<List<OperationRow>> = repository.recentOperations().asState(emptyList())

    /** Dépenses de l'année du mois consulté (vue Mois de l'analyse). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthYearSpending: StateFlow<YearSpending?> = _selectedMonth
        .map { it.year }
        .distinctUntilChanged()
        .flatMapLatest { repository.spendingYear(it) }
        .asState(null)

    /** Dépenses de l'année consultée (vue Année de l'analyse). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val yearSpending: StateFlow<YearSpending?> = _selectedYear
        .flatMapLatest { repository.spendingYear(it) }
        .asState(null)

    /** Rattrapage d'une provision à partir de [from]. */
    fun startFunding(envelopeId: Long, from: YearMonth) {
        viewModelScope.launch { repository.startFunding(envelopeId, from) }
    }

    /** Opérations d'une enveloppe sur un mois, pour la descente de l'analyse. */
    fun envelopeMonthOperations(envelopeId: Long, month: YearMonth): Flow<List<OperationRow>> =
        repository.envelopeMonthOperations(envelopeId, month)

    /** Opérations d'une enveloppe sur une période (fiche d'enveloppe). */
    fun envelopeOperations(envelopeId: Long, periodKey: String): Flow<List<OperationRow>> =
        repository.envelopeOperations(envelopeId, periodKey)

    val monthlyIncome: StateFlow<Money?> = repository.monthlyIncome.asState(null)

    val transferJournal: StateFlow<List<TransferRow>> = repository.transferJournal().asState(emptyList())

    fun addTransfers(legs: List<TransferLeg>, envelopeId: Long, incoming: Boolean, reason: String?) =
        perform { repository.addTransfers(legs, envelopeId, incoming, reason) }

    fun cancelTransfer(id: Long) = perform { repository.cancelTransfer(id) }

    /** Enregistre l'enveloppe ; [onSaved] reçoit son identifiant (utile juste après une création). */
    fun saveEnvelope(envelope: Envelope, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch { onSaved(repository.saveEnvelope(envelope)) }
    }

    fun archiveEnvelope(id: Long) = perform { repository.archiveEnvelope(id) }

    val archivedEnvelopes: StateFlow<List<Envelope>> = repository.archivedEnvelopes.asState(emptyList())

    fun restoreEnvelope(id: Long) = perform { repository.restoreEnvelope(id) }

    fun setMonthlyIncome(income: Money) = perform { repository.setMonthlyIncome(income) }

    fun addExpense(amount: Money, date: LocalDate, label: String, envelope: Envelope, wholePeriod: Boolean) =
        perform { repository.addManualExpense(amount, date, label, envelope, wholePeriod) }

    fun deleteExpense(transactionId: Long) = perform { repository.deleteManualExpense(transactionId) }

    /** Opérations de la banque qui attendent d'être rangées. */
    val unsortedCount: StateFlow<Int> = repository.unsortedCount.asState(0)

    /** Range une opération ; [onDone] reçoit le nombre d'autres opérations rangées par la nouvelle règle. */
    fun sortOperation(transactionId: Long, envelope: Envelope, ruleKeyword: String?, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repository.sortOperation(transactionId, envelope, ruleKeyword)) }
    }

    fun ignoreOperation(transactionId: Long) = perform { repository.ignoreOperation(transactionId) }

    val sortedHistory: StateFlow<List<SortedLabel<Long>>> = repository.sortedHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()) // lu hors composition

    /** Enveloppe proposée pour une opération à trier, d'après les opérations déjà rangées. */
    fun suggestedEnvelopeId(operation: OperationRow, history: List<SortedLabel<Long>> = sortedHistory.value): Long? =
        suggestEnvelope(operation.bankLabel, history)

    /** Renomme une opération ; [onDone] reçoit le nombre d'autres opérations renommées par l'alias. */
    fun renameOperation(transactionId: Long, alias: String, pattern: String?, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repository.renameOperation(transactionId, alias, pattern)) }
    }

    /** Déclasse des opérations du mois en cours ; [onDone] reçoit leur nombre. */
    fun unsortMonth(scope: UnsortScope, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repository.unsortMonth(YearMonth.now(), scope)) }
    }

    /** Range les opérations à trier avec les règles ; [onDone] reçoit le nombre d'opérations rangées. */
    fun applyRules(onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repository.applyRules()) }
    }

    /** Réglages du verre enregistrés (texte « clé=valeur;... »), lus dès le démarrage. */
    val glassTuning: StateFlow<String?> = repository.glassTuning
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun saveGlassTuning(encoded: String?) = perform { repository.setGlassTuning(encoded) }

    /** Accueil : reste du mois (par défaut) ou reste du jour. */
    val homeShowsDay: StateFlow<Boolean> = repository.homeShowsDay.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setHomeShowsDay(day: Boolean) = perform { repository.setHomeShowsDay(day) }

    /** Applique le tri validé après la proposition de Claude. */
    fun applyAssistedSort(choices: List<AssistedSortChoice>, onDone: (AssistedSortResult) -> Unit) {
        viewModelScope.launch { onDone(repository.applyAssistedSort(choices)) }
    }

    /** Livrets et investissements : ce qui est de côté, et les virements du mois vers eux. */
    val holdings: StateFlow<Holdings?> = repository.holdings().asState(null)
    val holdingAccounts: StateFlow<List<HoldingAccount>> = repository.holdingAccounts.asState(emptyList())

    /** Début du suivi : avant, les mois sont « non renseignés ». */
    val trackingStart: StateFlow<YearMonth?> = repository.trackingStart().asState(null)

    fun setTrackingStart(month: YearMonth?) = perform { repository.setTrackingStart(month) }

    /** Compte courant décomposé (null tant qu'aucun solde n'est connu). */
    val currentAccount: StateFlow<CurrentAccountExplained?> = repository.currentAccountExplained().asState(null)
    val currentAccountBalance: StateFlow<BalanceSnapshot?> = repository.currentAccountBalance.asState(null)

    fun setCurrentAccountBalance(balance: Money) = perform { repository.setCurrentAccountBalance(balance) }

    val savingsGoals: StateFlow<SavingsGoals> =
        repository.savingsGoals.asState(SavingsGoals(Money.euros(10_000), Money.euros(20_000)))

    fun setSavingsGoals(goals: SavingsGoals) = perform { repository.setSavingsGoals(goals) }

    /** Virements vers mes comptes, mois par mois, pour le bilan. */
    fun transfersToAccounts(month: YearMonth): Flow<Money> = repository.transfersToAccounts(month)

    fun addManualAccount(name: String, type: AccountType, balance: Money) = perform { repository.addManualAccount(name, type, balance) }

    fun saveAccount(accountId: Long, name: String, type: AccountType, balance: Money?) = perform {
        repository.renameAccount(accountId, name, type)
        if (balance != null) repository.updateBalance(accountId, balance)
    }

    fun archiveAccount(accountId: Long) = perform { repository.archiveAccount(accountId) }

    /** Virement vers un de mes comptes ; avec [keyword], les suivants seront reconnus seuls. */
    fun markTransferToAccount(transactionId: Long, accountId: Long, keyword: String?, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch { onDone(repository.markTransferToAccount(transactionId, accountId, keyword)) }
    }

    val transferRules: StateFlow<List<RuleRow>> = repository.transferRules.asState(emptyList())

    fun deleteTransferRule(id: Long) = perform { repository.deleteTransferRule(id) }

    val rules: StateFlow<List<RuleRow>> = repository.rules.asState(emptyList())
    val aliases: StateFlow<List<LabelAlias>> = repository.aliases.asState(emptyList())

    fun deleteRule(id: Long) = perform { repository.deleteRule(id) }
    fun deleteAlias(id: Long, restoreLabels: Boolean) = perform { repository.deleteAlias(id, restoreLabels) }

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as BankApplication).container
                BudgetViewModel(container.budgetRepository)
            }
        }
    }
}
