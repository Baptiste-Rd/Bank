package fr.baptiste.bank.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de migration de la base, sur l'appareil.
 *
 * Une base de l'ancienne version est recréée telle quelle à partir de son schéma exporté par Room
 * (app/schemas, joint aux tests), remplie, puis ouverte par l'app : Room applique les migrations
 * et vérifie que le schéma obtenu est exactement celui qu'il attend (sinon, exception).
 *
 * Règle pour la suite : chaque nouvelle migration (v8 → v9...) ajoute ici un test sur ce modèle,
 * à partir du schéma de la version précédente.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun clean() {
        context.deleteDatabase(DB)
    }

    @Test
    fun migrate7To8KeepsAccountsAndBalances() {
        createDatabase(version = 7) { db ->
            db.execSQL("INSERT INTO account (id, name, type, archived) VALUES (1, 'Compte courant', 'CURRENT', 0)")
            db.execSQL("INSERT INTO account (id, name, type, archived) VALUES (2, 'Livret A', 'SAVINGS', 0)")
            db.execSQL("INSERT INTO account_balance (account_id, date, balance_cents) VALUES (2, 20000, 560000)")
        }
        val database = open()
        val db = database.openHelper.writableDatabase
        db.query("SELECT balance_cents FROM account_balance WHERE account_id = 2").use { cursor ->
            cursor.moveToFirst()
            assertEquals(560000L, cursor.getLong(0))
        }
        // La table de la v8 existe et accepte une règle de virement vers le livret.
        db.execSQL("INSERT INTO transfer_rule (pattern, account_id) VALUES ('LIVRET A', 2)")
        database.close()
    }

    @Test
    fun migrate8To9KeepsEnvelopesWithoutCatchUp() {
        createDatabase(version = 8) { db ->
            db.execSQL(
                "INSERT INTO envelope (id, name, type, scale, amount_cents, status, priority, carry_over, archived, every, group_name) " +
                    "VALUES (1, 'Vacances', 'ANNUAL', 'YEAR', 180000, 'FLEXIBLE', 0, 0, 0, 1, 'Voyages')",
            )
        }
        val database = open()
        val db = database.openHelper.writableDatabase
        db.query("SELECT name, amount_cents, group_name, funding_start FROM envelope WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Vacances", cursor.getString(0))
            assertEquals(180000L, cursor.getLong(1))
            assertEquals("Voyages", cursor.getString(2))
            // Une enveloppe existante garde sa part habituelle : pas de rattrapage imposé.
            assertEquals(true, cursor.isNull(3))
        }
        db.execSQL("UPDATE envelope SET funding_start = '2026-10' WHERE id = 1")
        database.close()
    }

    @Test
    fun migrate9To10KeepsAccountsWithoutInvestment() {
        createDatabase(version = 9) { db ->
            db.execSQL("INSERT INTO account (id, name, type, archived) VALUES (1, 'PEA', 'INVESTMENT', 0)")
        }
        val database = open()
        val db = database.openHelper.writableDatabase
        db.query("SELECT name, invested_cents, tax_wrapper FROM account WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("PEA", cursor.getString(0))
            assertEquals(true, cursor.isNull(1))
            assertEquals(true, cursor.isNull(2))
        }
        db.execSQL("UPDATE account SET invested_cents = 200000, tax_wrapper = 'PEA_OVER_5' WHERE id = 1")
        database.close()
    }

    @Test
    fun migrate10To11KeepsTransfersFromTheWallet() {
        createDatabase(version = 10) { db ->
            db.execSQL(
                "INSERT INTO envelope (id, name, type, scale, amount_cents, status, priority, carry_over, archived, every) " +
                    "VALUES (1, 'Épargne', 'SAVINGS', 'MONTH', 30000, 'PROTECTED', 0, 0, 0, 1)",
            )
            db.execSQL(
                "INSERT INTO transfer (id, effective_date, source_envelope_id, target_envelope_id, amount_cents, cancelled) " +
                    "VALUES (1, 20000, NULL, 1, 100000, 0)",
            )
        }
        val database = open()
        val db = database.openHelper.writableDatabase
        db.query("SELECT amount_cents, from_reserve FROM transfer WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(100000L, cursor.getLong(0))
            // Un transfert existant venait du portefeuille : il le reste.
            assertEquals(0L, cursor.getLong(1))
        }
        database.close()
    }

    @Test
    fun migrate11To12ReadsTheBookingDayFromTheImportKey() {
        createDatabase(version = 11) { db ->
            db.execSQL("INSERT INTO account (id, name, type, archived) VALUES (1, 'Compte courant', 'CURRENT', 0)")
            db.execSQL(
                "INSERT INTO envelope (id, name, type, scale, amount_cents, status, priority, carry_over, archived, every) " +
                    "VALUES (1, 'Vacances', 'ANNUAL', 'YEAR', 100000, 'FLEXIBLE', 0, 0, 0, 1)",
            )
            // 20756 = 30 octobre 2026 ; la clé porte le jour de comptabilisation.
            db.execSQL(
                "INSERT INTO transaction_record (id, account_id, date, amount_cents, raw_label, clean_label, import_hash, source, ignored) " +
                    "VALUES (1, 1, 20756, -4500, 'PAIEMENT CB 2209 FNAC', 'Fnac', 'eb:uid-1:2026-10-30|-4500|PAIEMENT CB 2209 FNAC', 'API', 0)",
            )
            db.execSQL(
                "INSERT INTO transaction_record (id, account_id, date, amount_cents, raw_label, clean_label, import_hash, source, ignored) " +
                    "VALUES (2, 1, 20756, -900, 'CB 2810 CAFE', 'Cafe', 'eb:uid-1:ref:ABC', 'API', 0)",
            )
        }
        val database = open()
        val db = database.openHelper.writableDatabase
        db.query("SELECT id, date, booking_date, bank_date FROM transaction_record ORDER BY id").use { cursor ->
            cursor.moveToFirst()
            assertEquals(20756L, cursor.getLong(1))
            assertEquals(java.time.LocalDate.of(2026, 10, 30).toEpochDay(), cursor.getLong(2))
            assertEquals(true, cursor.isNull(3))
            cursor.moveToNext()
            // Référence de la banque : le jour de comptabilisation n'est pas dans la clé.
            assertEquals(true, cursor.isNull(2))
        }
        db.query("SELECT start_date FROM envelope WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
        }
        database.close()
    }

    /** Recrée une base de [version] à partir de son schéma exporté, puis y écrit [fill]. */
    private fun createDatabase(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open("fr.baptiste.bank.data.local.BankDatabase/$version.json")
            .bufferedReader().use { JSONObject(it.readText()) }
            .getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        fill(db)
        db.close()
    }

    private fun open(): BankDatabase = Room.databaseBuilder(context, BankDatabase::class.java, DB)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
        .build()

    private companion object {
        const val DB = "migration-test.db"
    }
}
