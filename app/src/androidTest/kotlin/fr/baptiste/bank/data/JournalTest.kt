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
        assertEquals("Nourriture", categoryOf(id))
        assertTrue(database.categoryDao().byName("Nourriture") != null)
        repository.undo(lastEntry().id)
        assertNull(categoryOf(id))
        assertNull(database.categoryDao().byName("Nourriture"))
        assertEquals(periodsBefore, database.allocationDao().forEnvelope(id).map { it.periodKey to it.amountCents })
    }

    @Test
    fun aCategoryGoesUnderAnotherButNeverIntoItsOwnChild() = runBlocking {
        val id = repository.saveEnvelope(envelope("Supermarché", 300))
        repository.moveEnvelope(id, "Courses")
        assertTrue(repository.moveCategory("Courses", "Nourriture"))
        assertEquals("Nourriture", parentOf("Courses"))
        assertEquals(false, repository.moveCategory("Nourriture", "Courses"))
        repository.undo(lastEntry().id)
        assertNull(parentOf("Courses"))
    }

    @Test
    fun renamingACategoryMovesItsEnvelopesAndUndoRestoresThem() = runBlocking {
        val id = repository.saveEnvelope(envelope("Supermarché", 300))
        repository.moveEnvelope(id, "Bouffe")
        assertTrue(repository.renameCategory("Bouffe", "Nourriture"))
        assertEquals("Nourriture", categoryOf(id))
        repository.undo(lastEntry().id)
        assertEquals("Bouffe", categoryOf(id))
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
        assertEquals("Nourriture", categoryOf(id))
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

    @Test
    fun creatingAnEnvelopeCannotBeUndoneOnceSomethingIsSortedIntoIt() = runBlocking {
        val created = repository.saveEnvelope(envelope("Cadeaux", 40))
        val creation = lastEntry()
        val id = bankOperation("FNAC", 30)
        repository.sortOperation(id, envelope("Cadeaux", 40).copy(id = created), null)
        assertTrue(repository.journal.first().first { it.id == creation.id }.blocked)
        assertNull(repository.undo(creation.id))
        assertEquals(listOf(created to 3000L), parts(id))
        // Annuler le tri d'abord libère la création.
        repository.undo(lastEntry().id)
        repository.undo(creation.id)
        assertNull(database.envelopeDao().byId(created))
    }

    @Test
    fun restoringAnEnvelopeAndCorrectingDatesAreUndone() = runBlocking {
        val id = repository.saveEnvelope(envelope("Sorties", 80))
        repository.archiveEnvelope(id)
        repository.restoreEnvelope(id)
        assertEquals(false, database.envelopeDao().byId(id)?.archived)
        repository.undo(lastEntry().id)
        assertEquals(true, database.envelopeDao().byId(id)?.archived)

        val operation = bankOperation("CB FNAC", 20)
        val before = database.transactionDao().byId(operation)!!.date
        val dated = fr.baptiste.bank.domain.bank.DatedOperation(operation, "CB FNAC", -2000, before, before, before.minusDays(3))
        repository.applyDateCorrections(listOf(fr.baptiste.bank.domain.bank.DateCorrection(dated, before.minusDays(3))))
        assertEquals(before.minusDays(3), database.transactionDao().byId(operation)?.date)
        repository.undo(lastEntry().id)
        assertEquals(before, database.transactionDao().byId(operation)?.date)
    }

    @Test
    fun namesAndRulesAreUndoneWithTheirOperations() = runBlocking {
        val first = bankOperation("PRLV FREE MOBILE 123", 20)
        val second = bankOperation("PRLV FREE MOBILE 456", 20)
        // Un nom pour tout libellé qui contient « FREE MOBILE » : les deux opérations le prennent.
        repository.renameOperation(first, "Forfait", "FREE MOBILE")
        assertEquals("Forfait", database.transactionDao().byId(second)?.alias)
        repository.undo(lastEntry().id)
        assertNull(database.transactionDao().byId(first)?.alias)
        assertNull(database.transactionDao().byId(second)?.alias)
        assertTrue(database.aliasDao().all().isEmpty())

        repository.addAlias("FREE MOBILE", "Forfait")
        val alias = database.aliasDao().all().single()
        repository.deleteAlias(alias.id, restoreLabels = true)
        assertNull(database.transactionDao().byId(first)?.alias)
        repository.undo(lastEntry().id)
        assertEquals("Forfait", database.transactionDao().byId(first)?.alias)
        assertEquals(1, database.aliasDao().all().size)

        val courses = envelope("Courses", 300).let { it.copy(id = repository.saveEnvelope(it)) }
        val shop = bankOperation("CARREFOUR", 15)
        repository.sortOperation(shop, courses, "CARREFOUR")
        val rule = database.ruleDao().all().single()
        repository.deleteRule(rule.id)
        assertTrue(database.ruleDao().all().isEmpty())
        repository.undo(lastEntry().id)
        assertEquals("CARREFOUR", database.ruleDao().all().single().pattern)
    }

    @Test
    fun anAliasOrARuleIsUndoneInTheRightOrderOnly() = runBlocking {
        bankOperation("PRLV FREE MOBILE 123", 20)
        repository.addAlias("FREE MOBILE", "Forfait")
        val creation = lastEntry()
        repository.deleteAlias(database.aliasDao().all().single().id, restoreLabels = false)
        // La création est bloquée par la suppression, plus récente.
        assertNull(repository.undo(creation.id))
        repository.undo(lastEntry().id)
        repository.undo(creation.id)
        assertTrue(database.aliasDao().all().isEmpty())

        val courses = envelope("Courses", 300).let { it.copy(id = repository.saveEnvelope(it)) }
        val maison = envelope("Maison", 50).let { it.copy(id = repository.saveEnvelope(it)) }
        val first = bankOperation("CARREFOUR", 15)
        val second = bankOperation("CARREFOUR", 25)
        repository.sortOperation(first, courses, "CARREFOUR")
        val firstSort = lastEntry()
        // Un second tri au même mot-clé remplace la règle : le premier tri ne s'annule qu'après lui.
        repository.unsortOperations(listOf(second))
        repository.sortOperation(second, maison, "CARREFOUR")
        assertNull(repository.undo(firstSort.id))
        assertTrue(repository.journal.first().first { it.id == firstSort.id }.blocked)
    }

    @Test
    fun anEntryFromBeforeTheTreeFindsItsCategoryByName() = runBlocking {
        val id = repository.saveEnvelope(envelope("Courses", 300))
        repository.moveEnvelope(id, "Nourriture")
        val before = database.envelopeDao().byId(id)!!.copy(parentId = null, groupName = "Bouffe")
        val entry = fr.baptiste.bank.data.local.JournalEntity(
            at = System.currentTimeMillis() + 60_000,
            label = "Ancien changement",
            payload = JournalPayload(envelopesBefore = listOf(before)).encode(),
        )
        val entryId = database.journalDao().insert(entry)
        repository.undo(entryId)
        assertEquals("Bouffe", categoryOf(id))
    }

    @Test
    fun undoingACreatedCategoryKeepsWhatWasPutThereSince() = runBlocking {
        val first = repository.saveEnvelope(envelope("Courses", 300))
        val second = repository.saveEnvelope(envelope("Bar", 60))
        repository.moveEnvelope(first, "Bouffe")
        val created = lastEntry()
        repository.moveEnvelope(second, "Bouffe")
        repository.undo(created.id)
        assertNull(categoryOf(first))
        assertEquals("Bouffe", categoryOf(second))
    }

    @Test
    fun undoingARenameKeepsWhatWasPutThereSince() = runBlocking {
        val first = repository.saveEnvelope(envelope("Courses", 300))
        val second = repository.saveEnvelope(envelope("Bar", 60))
        repository.moveEnvelope(first, "Bouffe")
        repository.renameCategory("Bouffe", "Repas")
        val rename = lastEntry()
        repository.moveEnvelope(second, "Repas")
        repository.undo(rename.id)
        assertEquals("Bouffe", categoryOf(first))
        assertEquals("Bouffe", categoryOf(second))
    }

    @Test
    fun undoingAMergeRestoresTheCategoryItsEnvelopesAndChildren() = runBlocking {
        val inside = repository.saveEnvelope(envelope("Courses", 300))
        val stopped = repository.saveEnvelope(envelope("Ancienne", 30))
        repository.moveEnvelope(inside, "Bouffe")
        repository.moveEnvelope(stopped, "Bouffe")
        repository.moveCategory("Fruits", "Bouffe")
        repository.moveCategory("Repas", null)
        repository.archiveEnvelope(stopped)
        val idBefore = database.categoryDao().byName("Bouffe")?.id
        assertTrue(repository.renameCategory("Bouffe", "Repas"))
        assertEquals("Repas", categoryOf(inside))
        assertEquals("Repas", parentOf("Fruits"))
        repository.undo(lastEntry().id)
        assertEquals(idBefore, database.categoryDao().byName("Bouffe")?.id)
        assertEquals("Bouffe", categoryOf(inside))
        assertEquals("Bouffe", categoryOf(stopped))
        assertEquals("Bouffe", parentOf("Fruits"))
    }

    @Test
    fun aParentThatDisappearedIsFoundAgainByName() = runBlocking {
        val id = repository.saveEnvelope(envelope("Courses", 300))
        repository.moveEnvelope(id, "Bouffe")
        repository.moveEnvelope(id, "Autres")
        val leaving = lastEntry()
        repository.moveCategory("Repas", null)
        assertTrue(repository.renameCategory("Bouffe", "Repas"))
        repository.undo(leaving.id)
        assertEquals("Bouffe", categoryOf(id))
    }

    @Test
    fun undoingARenameNeverLeavesTwoCategoriesWithTheSameName() = runBlocking {
        val first = repository.saveEnvelope(envelope("Cinéma", 30))
        repository.moveEnvelope(first, "Loisirs")
        repository.renameCategory("Loisirs", "Sorties")
        val rename = lastEntry()
        val second = repository.saveEnvelope(envelope("Sport", 20).copy(group = "Loisirs"))
        repository.undo(rename.id)
        assertEquals(1, database.categoryDao().all().count { it.name == "Loisirs" })
        assertEquals("Loisirs", categoryOf(first))
        assertEquals("Loisirs", categoryOf(second))
    }

    @Test
    fun undoingACaseChangeKeepsAnEmptyCategory() = runBlocking {
        repository.moveCategory("bouffe", null)
        repository.renameCategory("bouffe", "Bouffe")
        repository.undo(lastEntry().id)
        assertEquals("bouffe", database.categoryDao().byName("bouffe")?.name)
    }

    @Test
    fun mergingIntoItsOwnChildMakesNoLoop() = runBlocking {
        repository.moveCategory("Fruits", "Nourriture")
        assertTrue(repository.renameCategory("Nourriture", "Fruits"))
        assertNull(parentOf("Fruits"))
        repository.undo(lastEntry().id)
        assertEquals("Nourriture", parentOf("Fruits"))
    }

    @Test
    fun groupsStayOutOfTheEnvelopeLists() = runBlocking {
        val id = repository.saveEnvelope(envelope("Courses", 300))
        repository.moveEnvelope(id, "Nourriture")
        assertEquals(listOf("Courses"), repository.envelopes.first().map { it.name })
        assertEquals("Nourriture", repository.envelopes.first().single().group)
        assertTrue(repository.archivedEnvelopes.first().isEmpty())
    }

    @Test
    fun aParentCoversItsChildAndComesBackOnUndo() = runBlocking {
        val parent = repository.saveEnvelope(envelope("Transport", 300))
        val child = repository.saveEnvelope(envelope("Voiture", 400))
        val result = repository.moveInTree(child, fr.baptiste.bank.domain.model.TreeDrop.Into(parent))!!
        assertEquals(Money.euros(-300), result.planDelta)
        assertEquals(30000L, database.envelopeDao().byId(parent)?.amountCents)
        val board = repository.dashboard().first()
        assertEquals(Money.ZERO, board.envelopes.first { it.envelope.id == parent }.monthlyShare)
        assertEquals(Money.euros(400), board.envelopes.first { it.envelope.id == child }.monthlyShare)
        repository.undo(lastEntry().id)
        assertEquals(Money.euros(300), repository.dashboard().first().envelopes.first { it.envelope.id == parent }.monthlyShare)
    }

    @Test
    fun anEnvelopeNeverGoesIntoWhatItContains() = runBlocking {
        val parent = repository.saveEnvelope(envelope("A", 300))
        val child = repository.saveEnvelope(envelope("B", 100))
        repository.moveInTree(child, fr.baptiste.bank.domain.model.TreeDrop.Into(parent))
        assertNull(repository.moveInTree(parent, fr.baptiste.bank.domain.model.TreeDrop.Into(child)))
    }

    private suspend fun share(id: Long) = repository.dashboard().first().envelopes.first { it.envelope.id == id }.monthlyShare

    @Test
    fun leavingAParentGivesItsBudgetBackWhateverThePath() = runBlocking {
        val parent = repository.saveEnvelope(envelope("Transport", 300))
        val child = repository.saveEnvelope(envelope("Voiture", 400))
        repository.moveInTree(child, fr.baptiste.bank.domain.model.TreeDrop.Into(parent))
        repository.moveEnvelope(child, "Autres")
        assertEquals(Money.euros(300), share(parent))
        repository.moveInTree(child, fr.baptiste.bank.domain.model.TreeDrop.Into(parent))
        assertEquals(Money.ZERO, share(parent))
        repository.deleteEnvelope(child)
        assertEquals(Money.euros(300), share(parent))
    }

    @Test
    fun aStoppedChildStaysCoveredThisMonthAndUndoPutsItBack() = runBlocking {
        val parent = repository.saveEnvelope(envelope("Transport", 300))
        val child = repository.saveEnvelope(envelope("Voiture", 400))
        repository.moveInTree(child, fr.baptiste.bank.domain.model.TreeDrop.Into(parent))
        repository.archiveEnvelope(child)
        assertEquals(Money.ZERO, share(parent))
        repository.undo(lastEntry().id)
        assertEquals(Money.euros(400), share(child))
        assertEquals(Money.ZERO, share(parent))
    }

    @Test
    fun reorderingWithEveryPositionAtZeroStillMoves() = runBlocking {
        val first = repository.saveEnvelope(envelope("A", 10))
        val second = repository.saveEnvelope(envelope("B", 20))
        val third = repository.saveEnvelope(envelope("C", 30))
        val result = repository.moveInTree(third, fr.baptiste.bank.domain.model.TreeDrop.Before(first))!!
        assertTrue(result.label.isNotEmpty())
        assertEquals(0, database.envelopeDao().byId(third)!!.position)
        assertEquals(1, database.envelopeDao().byId(first)!!.position)
        assertEquals(2, database.envelopeDao().byId(second)!!.position)
    }

    @Test
    fun aParentKeepsItsTypedAmountAndPeriodAmount() = runBlocking {
        val parent = repository.saveEnvelope(envelope("Transport", 300))
        val child = repository.saveEnvelope(envelope("Voiture", 400))
        repository.moveInTree(child, fr.baptiste.bank.domain.model.TreeDrop.Into(parent))
        assertEquals(listOf(30000L), database.allocationDao().forEnvelope(parent).map { it.amountCents })
        assertEquals(listOf(30000L), database.allocationDao().forEnvelope(parent).map { it.coveredCents })
    }

    /** Nom du groupe qui contient l'enveloppe [id]. */
    private suspend fun categoryOf(id: Long): String? =
        database.envelopeDao().byId(id)?.parentId?.let { database.envelopeDao().byId(it)?.name }

    /** Nom de la mère de la catégorie [name]. */
    private suspend fun parentOf(name: String): String? =
        database.categoryDao().byName(name)?.parentId?.let { database.envelopeDao().byId(it)?.name }
}
