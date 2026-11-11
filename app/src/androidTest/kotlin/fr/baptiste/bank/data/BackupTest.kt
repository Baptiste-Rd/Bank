package fr.baptiste.bank.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.baptiste.bank.banking.BankSyncRepository
import fr.baptiste.bank.banking.KeyVault
import fr.baptiste.bank.data.local.BankDatabase
import fr.baptiste.bank.data.local.createSchemaDatabase
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * Sauvegarde et restauration, sur l'appareil (F1) : ce qui est sauvegardé revient ligne pour
 * ligne, une sauvegarde du schéma précédent se restaure, un échec ne change rien, une restauration
 * s'annule, et le fichier ne contient rien de la connexion à la banque.
 */
@RunWith(AndroidJUnit4::class)
class BackupTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val work = "backup_test_${System.nanoTime()}"
    private val password = "correct horse"

    // Peu de tours de dérivation : l'émulateur est lent, et le format est le même.
    private val fast = 1_000

    private lateinit var database: BankDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: BudgetRepository
    private lateinit var backup: BackupRepository

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        settings = SettingsRepository(context, work)
        repository = BudgetRepository(database, settings)
        backup = backupRepository()
    }

    @After
    fun close() {
        database.close()
        context.deleteDatabase("$work-in.db")
        context.deleteDatabase("$work-out.db")
        context.deleteDatabase("$work-old.db")
        File(context.filesDir, work).deleteRecursively()
    }

    private fun backupRepository(demo: Boolean = false, afterRestore: suspend (Long) -> Unit = {}) =
        BackupRepository(context, database, settings, demo = demo, build = 1, afterRestore = afterRestore, workName = work)

    /** Toutes les lignes de toutes les tables de l'app, et les compteurs d'identifiants. */
    private fun rows(): Map<String, List<String>> {
        val db = database.openHelper.writableDatabase
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (!name.startsWith("sqlite_") && !name.startsWith("android_") && !name.startsWith("room_")) add(name)
                }
            }
        }
        val content = tables.associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY id").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add((0 until cursor.columnCount).joinToString("|") { "${cursor.getColumnName(it)}=${if (cursor.isNull(it)) "null" else cursor.getString(it)}" })
                    }
                }
            }
        }
        val counters = db.query("SELECT name, seq FROM sqlite_sequence ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add("${cursor.getString(0)}=${cursor.getLong(1)}") }
        }
        return content + ("sqlite_sequence" to counters)
    }

    /** Les chiffres du Résumé et de chaque enveloppe : ce que l'utilisateur doit retrouver. */
    private suspend fun figures(): List<Any?> {
        val board = repository.dashboard().first()
        return listOf(
            board.income,
            board.dailyBudget,
            board.dailySpent,
            board.dailyBudget - board.dailySpent,
            board.dailyAllowance,
            board.unallocated,
            board.envelopes.map { Triple(it.envelope.name, it.monthlyShare, it.envelope.amount) },
        )
    }

    /** La démo, puis deux gestes inscrits à l'Historique : il doit revenir avec le reste. */
    private suspend fun fill() {
        DemoData.seedIfEmpty(database, repository)
        val envelopes = repository.envelopes.first()
        repository.archiveEnvelope(envelopes.first { it.name == "Cinéma" }.id)
        repository.saveEnvelope(envelopes.first { it.name == "Courses" }.let { it.copy(amount = it.amount + Money.euros(15)) })
        settings.setMonthReview(java.time.YearMonth.now(), "Mois correct.\nUne ligne de plus : « é ».")
    }

    /** Ce qu'on ferait après une sauvegarde : tout ce qui suit doit disparaître à la restauration. */
    private suspend fun change() {
        val envelopes = repository.envelopes.first()
        repository.deleteEnvelope(envelopes.first { it.name == "Courses" }.id)
        repository.archiveEnvelope(envelopes.first { it.name == "Sorties" }.id)
        repository.setMonthlyIncome(Money.euros(1234))
        settings.setMonthReview(java.time.YearMonth.now(), null)
        settings.setTrackingStart(java.time.YearMonth.now().minusMonths(1))
    }

    @Test
    fun savingThenRestoringGivesBackEveryRowAndTheSameFigures() = runBlocking<Unit> {
        fill()
        val rowsBefore = rows()
        val figuresBefore = figures()
        val settingsBefore = settings.exportAll()
        val history = repository.journal.first().map { it.label }
        assertTrue(rowsBefore.getValue("transaction_record").size > 20)
        assertTrue(history.size >= 2)

        val sealed = backup.sealedBackup(password.toCharArray(), fast)
        change()
        assertFalse(rowsBefore == rows())
        assertFalse(figuresBefore == figures())

        val loaded = backup.open(sealed, password.toCharArray())
        assertEquals(rowsBefore.getValue("transaction_record").size, loaded.manifest.operations)
        backup.restore(loaded)

        assertEquals(rowsBefore, rows())
        assertEquals(settingsBefore, settings.exportAll())
        assertEquals(figuresBefore, figures())
        assertEquals(history, repository.journal.first().map { it.label })
    }

    @Test
    fun aRestoreReachesTheScreensAlreadyOpen() = runBlocking<Unit> {
        fill()
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            // Comme un écran ouvert : un flux déjà collecté, qui ne se relit que si Room le prévient.
            val names = repository.envelopes.stateIn(scope, SharingStarted.Eagerly, emptyList())
            withTimeout(10_000) { names.first { list -> list.any { it.name == "Courses" } } }
            val sealed = backup.sealedBackup(password.toCharArray(), fast)
            repository.deleteEnvelope(names.value.first { it.name == "Courses" }.id)
            withTimeout(10_000) { names.first { list -> list.none { it.name == "Courses" } } }
            backup.restore(backup.open(sealed, password.toCharArray()))
            withTimeout(10_000) { names.first { list -> list.any { it.name == "Courses" } } }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aBackupOfThePreviousSchemaIsRestored() = runBlocking<Unit> {
        fill()
        // Une base du schéma 19 (avant les objectifs d'épargne), comme l'app d'alors l'aurait sauvegardée.
        val old = "$work-old.db"
        createSchemaDatabase(context, old, version = 19) { db ->
            db.execSQL("INSERT INTO account (id, name, type, archived) VALUES (1, 'Compte courant', 'CURRENT', 0)")
            db.execSQL(
                "INSERT INTO envelope (id, name, type, scale, amount_cents, status, priority, carry_over, archived, every, repeats) " +
                    "VALUES (7, 'Marché', 'DAILY', 'MONTH', 12300, 'FLEXIBLE', 0, 0, 0, 1, 1)",
            )
            db.execSQL("INSERT INTO allocation (envelope_id, period_key, amount_cents) VALUES (7, '2026-10', 12300)")
            db.execSQL(
                "INSERT INTO transaction_record (id, account_id, date, amount_cents, raw_label, clean_label, import_hash, source, ignored) " +
                    "VALUES (41, 1, 20750, -2340, 'CB PRIMEUR', 'Primeur', 'h-41', 'API', 0)",
            )
            db.execSQL(
                "INSERT INTO transaction_record (id, account_id, date, amount_cents, raw_label, clean_label, import_hash, source, ignored) " +
                    "VALUES (42, 1, 20751, -1000, 'CB FROMAGER', 'Fromager', 'h-42', 'API', 0)",
            )
            db.execSQL("INSERT INTO imputation (transaction_id, envelope_id, period_key, amount_cents, mode) VALUES (41, 7, '2026-10', 2340, 'RULE')")
            db.execSQL("INSERT INTO rule (match_type, pattern, envelope_id, priority) VALUES ('CONTAINS', 'PRIMEUR', 7, 0)")
            db.execSQL("INSERT INTO journal_entry (at, label, payload, undone) VALUES (1, 'Marché créée', '{}', 0)")
        }
        val file = context.getDatabasePath(old)
        val archive = BackupFile.pack(
            mapOf(
                BackupFile.MANIFEST to BackupManifest(BackupFile.FORMAT, schema = 19, build = 1, savedAt = 1_000, demo = false, envelopes = 1, operations = 2).encode(),
                BackupFile.DATABASE to file.readBytes(),
                BackupFile.SETTINGS to BackupFile.encodeSettings(mapOf("monthly_income_cents" to 210_000L)),
            ),
        )
        val sealed = BackupFile.seal(archive, password.toCharArray(), fast)

        backup.restore(backup.open(sealed, password.toCharArray()))

        val all = rows()
        assertEquals(1, all.getValue("envelope").size)
        assertEquals(listOf(41L, 42L), database.openHelper.writableDatabase.query("SELECT id FROM transaction_record ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        })
        assertEquals(1, all.getValue("imputation").size)
        assertEquals(1, all.getValue("rule").size)
        assertEquals(1, all.getValue("journal_entry").size)
        assertEquals(0, all.getValue("savings_placement").size)
        // Les colonnes du schéma 20 existent, vides : la migration a eu lieu avant la copie.
        assertTrue(all.getValue("envelope").single().contains("goal_cents=null"))
        // L'app lit ces données avec son code d'aujourd'hui.
        val envelope = repository.envelopes.first().single()
        assertEquals("Marché", envelope.name)
        assertEquals(Money(12_300), envelope.amount)
        assertEquals(Money(210_000), settings.monthlyIncome.first())
    }

    @Test
    fun aWrongPasswordOrADamagedFileChangesNothing() = runBlocking<Unit> {
        fill()
        val sealed = backup.sealedBackup(password.toCharArray(), fast)
        change()
        val rowsBefore = rows()
        val settingsBefore = settings.exportAll()

        assertTrue(runCatching { backup.open(sealed, "correct horsf".toCharArray()) }.exceptionOrNull() is BackupError.WrongPassword)
        val damaged = sealed.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertTrue(runCatching { backup.open(damaged, password.toCharArray()) }.exceptionOrNull() is BackupError.WrongPassword)
        assertTrue(runCatching { backup.open("-----BEGIN PRIVATE KEY-----".toByteArray() + ByteArray(64), password.toCharArray()) }.exceptionOrNull() is BackupError.NotABackup)

        // Une archive bien chiffrée dont la base est illisible : la restauration échoue en entier.
        val entries = BackupFile.unpack(BackupFile.open(sealed, password.toCharArray()))
        val broken = entries + (BackupFile.DATABASE to (entries.getValue(BackupFile.DATABASE).copyOf(16) + ByteArray(4096)))
        val loaded = backup.open(BackupFile.seal(BackupFile.pack(broken), password.toCharArray(), fast), password.toCharArray())
        assertTrue(runCatching { backup.restore(loaded) }.exceptionOrNull() is BackupError)

        assertEquals(rowsBefore, rows())
        assertEquals(settingsBefore, settings.exportAll())
        assertFalse(backup.canUndoRestore)
    }

    @Test
    fun undoingARestoreGivesBackTheStateBefore() = runBlocking<Unit> {
        fill()
        val sealed = backup.sealedBackup(password.toCharArray(), fast)
        change()
        val rowsBefore = rows()
        val settingsBefore = settings.exportAll()
        val figuresBefore = figures()

        backup.restore(backup.open(sealed, password.toCharArray()))
        assertFalse(rowsBefore == rows())
        assertTrue(backup.canUndoRestore)

        assertTrue(backup.undoRestore())
        assertEquals(rowsBefore, rows())
        assertEquals(settingsBefore, settings.exportAll())
        assertEquals(figuresBefore, figures())
        // Une seule fois : il n'y a plus d'état d'avant à remettre.
        assertFalse(backup.canUndoRestore)
        assertFalse(backup.undoRestore())
    }

    @Test
    fun theStateKeptByARestoreIsForgottenAtTheNextLaunch() = runBlocking<Unit> {
        fill()
        val kept = BackupRepository(context, database, settings, demo = false, build = 1)
        try {
            kept.restore(kept.current())
            assertTrue(kept.canUndoRestore)
            BackupRepository.forgetPrevious(context)
            assertFalse(kept.canUndoRestore)
            assertFalse(kept.undoRestore())
        } finally {
            BackupRepository.forgetPrevious(context)
        }
    }

    @Test
    fun aDemoFileNeverReplacesRealDataAndTheReverse() = runBlocking<Unit> {
        fill()
        val demo = backupRepository(demo = true)
        val fromDemo = demo.sealedBackup(password.toCharArray(), fast)
        val fromReal = backup.sealedBackup(password.toCharArray(), fast)
        assertTrue(runCatching { backup.open(fromDemo, password.toCharArray()) }.exceptionOrNull() is BackupError.FromDemo)
        assertTrue(runCatching { demo.open(fromReal, password.toCharArray()) }.exceptionOrNull() is BackupError.FromRealData)
        assertTrue(demo.open(fromDemo, password.toCharArray()).manifest.demo)
    }

    @Test
    fun aFileFromALaterSchemaAsksForAnUpdate() = runBlocking<Unit> {
        fill()
        val entries = BackupFile.unpack(BackupFile.open(backup.sealedBackup(password.toCharArray(), fast), password.toCharArray()))
        val manifest = BackupManifest.decode(entries.getValue(BackupFile.MANIFEST))
        val later = entries + (BackupFile.MANIFEST to manifest.copy(schema = manifest.schema + 1).encode())
        val sealed = BackupFile.seal(BackupFile.pack(later), password.toCharArray(), fast)
        assertTrue(runCatching { backup.open(sealed, password.toCharArray()) }.exceptionOrNull() is BackupError.Newer)
    }

    @Test
    fun theFileHoldsNothingOfTheBankConnection() = runBlocking<Unit> {
        fill()
        // Une connexion bancaire complète à côté : identifiant d'application, et clé privée dans le coffre.
        val vault = KeyVault(context, "$work-key.bin")
        val bank = BankSyncRepository(context, repository, vault, storeName = "${work}_bank")
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val body = Base64.getEncoder().encodeToString(pair.private.encoded)
        try {
            vault.store("-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n")
            bank.saveApplication("app-id-a-ne-pas-sauvegarder", "")
            assertTrue(vault.hasKey)

            val entries = BackupFile.unpack(BackupFile.open(backup.sealedBackup(password.toCharArray(), fast), password.toCharArray()))
            assertEquals(setOf(BackupFile.MANIFEST, BackupFile.DATABASE, BackupFile.SETTINGS), entries.keys)
            val everything = entries.values.joinToString("") { String(it, Charsets.ISO_8859_1) }
            assertFalse(everything.contains("app-id-a-ne-pas-sauvegarder"))
            assertFalse(everything.contains(body.take(64)))
            assertFalse(everything.contains("PRIVATE KEY"))
            assertFalse(everything.contains(String(File(context.filesDir, "$work-key.bin").readBytes(), Charsets.ISO_8859_1)))
            val keys = BackupFile.decodeSettings(entries.getValue(BackupFile.SETTINGS)).keys
            assertFalse(keys.any { it in setOf("application_id", "session_id", "account_uid") })

            // Restaurer ne touche ni à la clé ni à la connexion du téléphone.
            backup.restore(backup.current())
            assertTrue(vault.hasKey)
            assertEquals("app-id-a-ne-pas-sauvegarder", bank.connection.first().applicationId)
        } finally {
            vault.clear()
        }
    }

    @Test
    fun whatFollowsARestoreKnowsWhenTheFileWasSaved() = runBlocking<Unit> {
        fill()
        var told: Long? = null
        val watched = backupRepository(afterRestore = { told = it })
        val loaded = watched.open(watched.sealedBackup(password.toCharArray(), fast), password.toCharArray())
        watched.restore(loaded)
        assertEquals(loaded.manifest.savedAt, told)
    }
}
