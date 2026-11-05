package fr.baptiste.bank.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.baptiste.bank.data.local.AccountEntity
import fr.baptiste.bank.data.local.BankDatabase
import fr.baptiste.bank.data.local.TransactionEntity
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.model.Envelope
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Historique des changements, sur l'appareil : chaque type de changement annulé revient exactement
 * à l'état d'avant, et seul ce changement est défait.
 */
@RunWith(AndroidJUnit4::class)
class JournalTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: BankDatabase
    private lateinit var repository: BudgetRepository
    private val today = LocalDate.now()

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        repository = BudgetRepository(database, SettingsRepository(context, "journal_test_${System.nanoTime()}"))
    }

    @After
    fun close() = database.close()

    private fun envelope(name: String, euros: Long) = Envelope(
        id = 0,
        name = name,
        type = EnvelopeType.DAILY,
        frequency = EnvelopeType.DAILY.defaultFrequency,
        amount = Money.euros(euros),
        status = EnvelopeType.DAILY.defaultStatus,
    )

    private suspend fun bankOperation(label: String, euros: Long): Long {
        val account = database.accountDao().currentAccountId()
            ?: database.accountDao().insert(AccountEntity(name = "Compte courant", type = AccountType.CURRENT))
        return database.transactionDao().insert(
            TransactionEntity(
                accountId = account,
                date = today,
                amountCents = -euros * 100,
                rawLabel = label,
                cleanLabel = label,
                source = TransactionSource.API,
                importHash = "$label-$euros-${System.nanoTime()}",
            ),
        )
    }

    private suspend fun parts(id: Long) = database.imputationDao().forTransaction(id).map { it.envelopeId to it.amountCents }

    private suspend fun lastEntry() = repository.journal.first().first()

    @Test
    fun undoingASortPutsTheOperationsBackAndRemovesTheRule() = runBlocking {
        val courses = repository.saveEnvelope(envelope("Courses", 300)).let { id -> envelope("Courses", 300).copy(id = id) }
        val first = bankOperation("CARREFOUR CITY", 20)
        val second = bankOperation("CARREFOUR CITY", 35)
        val others = repository.sortOperation(first, courses, "CARREFOUR")
        assertEquals(1, others)
        assertEquals(listOf(courses.id to 2000L), parts(first))
        repository.undo(lastEntry().id)
        assertTrue(parts(first).isEmpty())
        assertTrue(parts(second).isEmpty())
        assertTrue(database.ruleDao().all().isEmpty())
        assertTrue(lastEntry().undone)
        // Déjà annulé : rien ne se passe.
        assertNull(repository.undo(lastEntry().id))
    }

    @Test
    fun undoingASplitBringsBackThePreviousSort() = runBlocking {
        val courses = envelope("Courses", 300).let { it.copy(id = repository.saveEnvelope(it)) }
        val maison = envelope("Maison", 50).let { it.copy(id = repository.saveEnvelope(it)) }
        val id = bankOperation("CARREFOUR", 33)
        repository.sortOperation(id, courses, null)
        repository.splitOperation(id, listOf(courses to Money.euros(23), maison to Money.euros(10)))
        assertEquals(2, parts(id).size)
        repository.undo(lastEntry().id)
        assertEquals(listOf(courses.id to 3300L), parts(id))
    }

    @Test
    fun undoingAnEnvelopeChangeRestoresItsRowAndPeriods() = runBlocking {
        val id = repository.saveEnvelope(envelope("Courses", 300))
        val before = database.envelopeDao().byId(id)
        val periodsBefore = database.allocationDao().forEnvelope(id).map { Triple(it.periodKey, it.amountCents, it.endDate) }
        repository.saveEnvelope(envelope("Alimentation", 420).copy(id = id))
        assertEquals("Alimentation", database.envelopeDao().byId(id)?.name)
        repository.undo(lastEntry().id)
        assertEquals(before, database.envelopeDao().byId(id))
        assertEquals(periodsBefore, database.allocationDao().forEnvelope(id).map { Triple(it.periodKey, it.amountCents, it.endDate) })
    }

    @Test
    fun undoingACreationOrAnArchiveTakesItBack() = runBlocking {
        val id = repository.saveEnvelope(envelope("Cadeaux", 40))
        repository.undo(lastEntry().id)
        assertNull(database.envelopeDao().byId(id))
        val kept = repository.saveEnvelope(envelope("Sorties", 80))
        repository.archiveEnvelope(kept)
        repository.undo(lastEntry().id)
        assertEquals(false, database.envelopeDao().byId(kept)?.archived)
    }

    @Test
    fun undoingATransferOrAnExpenseCancelsIt() = runBlocking {
        val courses = envelope("Courses", 300).let { it.copy(id = repository.saveEnvelope(it)) }
        repository.addTransfers(listOf(fr.baptiste.bank.domain.budget.TransferLeg(null, Money.euros(30))), courses.id, incoming = true, reason = null)
        repository.undo(lastEntry().id)
        assertTrue(repository.transferJournal().first().all { it.cancelled })
        repository.addManualExpense(Money.euros(12), today, "Marché", courses)
        repository.undo(lastEntry().id)
        assertTrue(database.transactionDao().manualBetween(today.minusDays(1), today.plusDays(1)).isEmpty())
    }

    @Test
    fun aCoverTakenOnTheAccountIsAReserveTransferAndUndoes() = runBlocking {
        val courses = envelope("Courses", 300).let { it.copy(id = repository.saveEnvelope(it)) }
        repository.addTransfers(
            listOf(fr.baptiste.bank.domain.budget.TransferLeg(null, Money.euros(40), fromReserve = true)),
            courses.id,
            incoming = true,
            reason = "Couverture",
        )
        val row = repository.transferJournal().first().single()
        assertTrue(row.fromReserve)
        assertNull(row.sourceName)
        // Pris sur le compte : le mois n'est pas touché.
        val active = database.transferDao().observeActive(today.minusDays(1), today.plusDays(1)).first().single()
        assertTrue(active.fromReserve)
        repository.undo(lastEntry().id)
        assertTrue(repository.transferJournal().first().all { it.cancelled })
    }

    @Test
    fun movingIntoAnUnusedCategoryCreatesItAndUndoTakesItBack() = runBlocking {
        val id = repository.saveEnvelope(envelope("Courses", 300))
        val periodsBefore = database.allocationDao().forEnvelope(id).map { it.periodKey to it.amountCents }
        repository.moveEnvelope(id, "Nourriture")
        assertEquals("Nourriture", database.envelopeDao().byId(id)?.groupName)
        assertTrue(database.categoryDao().byName("Nourriture") != null)
        repository.undo(lastEntry().id)
        assertNull(database.envelopeDao().byId(id)?.groupName)
        assertNull(database.categoryDao().byName("Nourriture"))
        assertEquals(periodsBefore, database.allocationDao().forEnvelope(id).map { it.periodKey to it.amountCents })
    }

    @Test
    fun aCategoryGoesUnderAnotherButNeverIntoItsOwnChild() = runBlocking {
        val id = repository.saveEnvelope(envelope("Supermarché", 300))
        repository.moveEnvelope(id, "Courses")
        assertTrue(repository.moveCategory("Courses", "Nourriture"))
        assertEquals("Nourriture", database.categoryDao().byName("Courses")?.parentName)
        assertEquals(false, repository.moveCategory("Nourriture", "Courses"))
        repository.undo(lastEntry().id)
        assertNull(database.categoryDao().byName("Courses")?.parentName)
    }

    @Test
    fun renamingACategoryMovesItsEnvelopesAndUndoRestoresThem() = runBlocking {
        val id = repository.saveEnvelope(envelope("Supermarché", 300))
        repository.moveEnvelope(id, "Bouffe")
        assertTrue(repository.renameCategory("Bouffe", "Nourriture"))
        assertEquals("Nourriture", database.envelopeDao().byId(id)?.groupName)
        repository.undo(lastEntry().id)
        assertEquals("Bouffe", database.envelopeDao().byId(id)?.groupName)
        assertTrue(database.categoryDao().byName("Bouffe") != null)
        assertNull(database.categoryDao().byName("Nourriture"))
    }

    @Test
    fun theJournalKeepsTheTenLastChanges() = runBlocking {
        repeat(12) { repository.saveEnvelope(envelope("Enveloppe $it", 10)) }
        val entries = repository.journal.first()
        assertEquals(10, entries.size)
        assertEquals("Nouvelle enveloppe Enveloppe 11", entries.first().label)
    }

    @Test
    fun anOlderChangeCannotBeUndoneWhileANewerOneTouchesTheSameEnvelope() = runBlocking {
        val id = repository.saveEnvelope(envelope("Courses", 300))
        repository.saveEnvelope(envelope("Courses", 350).copy(id = id))
        val edit = lastEntry()
        repository.moveEnvelope(id, "Nourriture")
        val move = lastEntry()
        // La modification est bloquée par le déplacement, plus récent : « modifié depuis ».
        assertTrue(repository.journal.first().first { it.id == edit.id }.blocked)
        assertNull(repository.undo(edit.id))
        assertEquals("Nourriture", database.envelopeDao().byId(id)?.groupName)
        // Dans l'ordre inverse, les deux s'annulent.
        repository.undo(move.id)
        repository.undo(edit.id)
        assertEquals(30000L, database.envelopeDao().byId(id)?.amountCents)
    }

    @Test
    fun unsortingAMonthIsRecordedAndUndone() = runBlocking {
        val courses = envelope("Courses", 300).let { it.copy(id = repository.saveEnvelope(it)) }
        val id = bankOperation("CARREFOUR", 20)
        repository.sortOperation(id, courses, null)
        assertEquals(1, repository.unsortMonth(java.time.YearMonth.from(today), UnsortScope.All))
        assertTrue(parts(id).isEmpty())
        repository.undo(lastEntry().id)
        assertEquals(listOf(courses.id to 2000L), parts(id))
    }

    @Test
    fun aDeletedManualExpenseComesBackWithItsEnvelope() = runBlocking {
        val courses = envelope("Courses", 300).let { it.copy(id = repository.saveEnvelope(it)) }
        repository.addManualExpense(Money.euros(12), today, "Marché", courses)
        val id = database.transactionDao().manualBetween(today.minusDays(1), today.plusDays(1)).single().id
        repository.deleteManualExpense(id)
        assertNull(database.transactionDao().byId(id))
        repository.undo(lastEntry().id)
        assertEquals("Marché", database.transactionDao().byId(id)?.cleanLabel)
        assertEquals(listOf(courses.id to 1200L), parts(id))
    }

    @Test
    fun aCorrectedPeriodACancelledTransferAndAMonthChoiceAreUndone() = runBlocking {
        val id = repository.saveEnvelope(envelope("Courses", 300))
        val period = database.allocationDao().forEnvelope(id).first()
        repository.setPeriodAmount(id, period.periodKey, Money.euros(250))
        repository.undo(lastEntry().id)
        assertEquals(period.amountCents, database.allocationDao().forEnvelope(id).first { it.periodKey == period.periodKey }.amountCents)

        repository.addTransfers(listOf(fr.baptiste.bank.domain.budget.TransferLeg(null, Money.euros(30))), id, incoming = true, reason = null)
        val transfer = repository.transferJournal().first().single()
        repository.cancelTransfer(transfer.id)
        assertTrue(repository.transferJournal().first().single().cancelled)
        repository.undo(lastEntry().id)
        assertEquals(false, repository.transferJournal().first().single().cancelled)

        val month = java.time.YearMonth.from(today).minusMonths(2)
        repository.setMonthCounted(month, false)
        assertTrue(month in repository.excludedMonths.first())
        repository.undo(lastEntry().id)
        assertTrue(month !in repository.excludedMonths.first())
    }

    @Test
    fun renamingAnOperationIsUndone() = runBlocking {
        val id = bankOperation("CB CARREFOUR 1234", 20)
        repository.renameOperation(id, "Carrefour", null)
        assertEquals("Carrefour", database.transactionDao().byId(id)?.alias)
        repository.undo(lastEntry().id)
        assertNull(database.transactionDao().byId(id)?.alias)
    }
}
