package fr.baptiste.bank.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.baptiste.bank.BankApplication
import fr.baptiste.bank.data.BudgetRepository
import fr.baptiste.bank.data.MonthSavings
import fr.baptiste.bank.data.YearOverview
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.local.TransferRow
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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

    /** Mois suivant, sans dépasser le mois en cours. */
    fun nextMonth() {
        _selectedMonth.update { if (it < YearMonth.now()) it.plusMonths(1) else it }
    }

    val operations: StateFlow<List<OperationRow>> = repository.recentOperations().asState(emptyList())

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
