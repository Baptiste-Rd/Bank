package fr.baptiste.bank.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de migration de la base : une base remplie de la version précédente doit passer à la
 * suivante sans perte, et le schéma obtenu doit être exactement celui que Room attend.
 *
 * Règle pour la suite : chaque nouvelle migration (v8 → v9...) ajoute ici un test qui crée une
 * base de l'ancienne version (son schéma est dans app/schemas), y met des données, migre, vérifie.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BankDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate7To8KeepsAccountsAndBalances() {
        helper.createDatabase(DB, 7).apply {
            execSQL("INSERT INTO account (id, name, type, archived) VALUES (1, 'Compte courant', 'CURRENT', 0)")
            execSQL("INSERT INTO account (id, name, type, archived) VALUES (2, 'Livret A', 'SAVINGS', 0)")
            execSQL("INSERT INTO account_balance (account_id, date, balance_cents) VALUES (2, 20000, 560000)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 8, true, MIGRATION_7_8)
        db.query("SELECT balance_cents FROM account_balance WHERE account_id = 2").use { cursor ->
            cursor.moveToFirst()
            assertEquals(560000L, cursor.getLong(0))
        }
        // La nouvelle table accepte une règle de virement vers le livret.
        db.execSQL("INSERT INTO transfer_rule (pattern, account_id) VALUES ('LIVRET A', 2)")
        db.close()
    }

    @Test
    fun appOpensAMigratedDatabase() {
        helper.createDatabase(DB, 7).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, BankDatabase::class.java, DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        // Ouvrir la base déclenche les migrations et la vérification du schéma par Room.
        database.openHelper.writableDatabase
        database.close()
    }

    private companion object {
        const val DB = "migration-test.db"
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        )
    }
}
