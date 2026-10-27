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
    ],
    version = 3,
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

    companion object {
        fun create(context: Context): BankDatabase =
            Room.databaseBuilder(context, BankDatabase::class.java, "bank.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
