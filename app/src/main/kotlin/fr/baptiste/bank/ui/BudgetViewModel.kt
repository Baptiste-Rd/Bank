package fr.baptiste.bank.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.baptiste.bank.BankApplication
import fr.baptiste.bank.data.BudgetRepository
import fr.baptiste.bank.data.local.OperationRow
import fr.baptiste.bank.data.local.TransferRow
import fr.baptiste.bank.domain.budget.Dashboard
import fr.baptiste.bank.domain.budget.TransferLeg
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    val operations: StateFlow<List<OperationRow>> = repository.recentOperations().asState(emptyList())

    val monthlyIncome: StateFlow<Money?> = repository.monthlyIncome.asState(null)

    val transferJournal: StateFlow<List<TransferRow>> = repository.transferJournal().asState(emptyList())

    fun addTransfers(legs: List<TransferLeg>, envelopeId: Long, incoming: Boolean, reason: String?) =
        perform { repository.addTransfers(legs, envelopeId, incoming, reason) }

    fun cancelTransfer(id: Long) = perform { repository.cancelTransfer(id) }

    fun saveEnvelope(envelope: Envelope) = perform { repository.saveEnvelope(envelope) }

    fun archiveEnvelope(id: Long) = perform { repository.archiveEnvelope(id) }

    val archivedEnvelopes: StateFlow<List<Envelope>> = repository.archivedEnvelopes.asState(emptyList())

    fun restoreEnvelope(id: Long) = perform { repository.restoreEnvelope(id) }

    fun setMonthlyIncome(income: Money) = perform { repository.setMonthlyIncome(income) }

    fun addExpense(amount: Money, date: LocalDate, label: String, envelope: Envelope, wholePeriod: Boolean) =
        perform { repository.addManualExpense(amount, date, label, envelope, wholePeriod) }

    fun deleteExpense(transactionId: Long) = perform { repository.deleteManualExpense(transactionId) }

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
