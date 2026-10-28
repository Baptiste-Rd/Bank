package fr.baptiste.bank.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        EnvelopeEntity::class,
        AllocationEntity::class,
        ImputationEntity::class,
        TransferEntity::class,
        RuleEntity::class,
        LabelAliasEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BankDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun envelopeDao(): EnvelopeDao
    abstract fun allocationDao(): AllocationDao
    abstract fun imputationDao(): ImputationDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao
    abstract fun ruleDao(): RuleDao
    abstract fun aliasDao(): AliasDao

    companion object {
        /** [name] permet au mode démo d'utiliser une base séparée, sans toucher aux vraies données. */
        fun create(context: Context, name: String = "bank.db"): BankDatabase =
            Room.databaseBuilder(context, BankDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}

/**
 * Version 2 : ajout du rattrapage sur les imputations.
 * Les données existantes sont conservées ; les dépenses déjà saisies restent datées (0).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE imputation ADD COLUMN whole_period INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 3 : fréquence personnalisée des enveloppes (tous les N jours, semaines, mois, ans). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE envelope ADD COLUMN every INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE envelope ADD COLUMN anchor_date INTEGER")
    }
}

/** Version 4 : synchronisation bancaire (compte lié à l'agrégateur, opérations hors budget). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account ADD COLUMN external_uid TEXT")
        db.execSQL("ALTER TABLE transaction_record ADD COLUMN ignored INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 5 : groupes d'enveloppes (un niveau, sans budget propre). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE envelope ADD COLUMN group_name TEXT")
    }
}

/** Version 6 : alias des libellés (« PRLV SEPA FREE MOBILE » affiché « Forfait mobile »). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transaction_record ADD COLUMN alias TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS label_alias (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pattern TEXT NOT NULL, alias TEXT NOT NULL)",
        )
    }
}
