package fr.baptiste.bank.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.baptiste.bank.data.local.AccountEntity
import fr.baptiste.bank.data.local.BankDatabase
import fr.baptiste.bank.data.local.TransactionEntity
import fr.baptiste.bank.domain.budget.salaryKey
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.model.TransactionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** « À trier » : la file et son nombre viennent du même endroit, sans le salaire reconnu. */
@RunWith(AndroidJUnit4::class)
class UnsortedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: BankDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: BudgetRepository
    private val today = LocalDate.now()

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        settings = SettingsRepository(context, "unsorted_test_${System.nanoTime()}")
        repository = BudgetRepository(database, settings)
    }

    @After
    fun close() = database.close()

    private suspend fun bankOperation(label: String, cents: Long): Long {
        val account = database.accountDao().currentAccountId()
            ?: database.accountDao().insert(AccountEntity(name = "Compte courant", type = AccountType.CURRENT))
        return database.transactionDao().insert(
            TransactionEntity(
                accountId = account,
                date = today,
                amountCents = cents,
                rawLabel = label,
                cleanLabel = label,
                source = TransactionSource.API,
                importHash = "$label-$cents-${System.nanoTime()}",
            ),
        )
    }

    @Test
    fun theRecognisedSalaryIsNeitherInTheQueueNorInTheCount() = runBlocking {
        val salaryLabel = "VIR SEPA RECU /DE SOCIETE EXEMPLE /MOTIF SALAIRE"
        bankOperation("CARREFOUR CITY", -2_340)
        bankOperation("FNAC PARIS", -4_500)
        val salary = bankOperation(salaryLabel, 245_000)
        assertEquals(3, repository.unsortedOperations.first().size)
        settings.setSalary(salaryKey(salaryLabel), true)
        val queue = repository.unsortedOperations.first()
        assertEquals(2, queue.size)
        assertEquals(false, queue.any { it.id == salary })
        assertEquals(queue.size, repository.unsortedCount.first())
    }
}
