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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .build()

    private companion object {
        const val DB = "migration-test.db"
    }
}
