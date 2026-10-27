package fr.baptiste.bank.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BankDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun envelopeDao(): EnvelopeDao
    abstract fun allocationDao(): AllocationDao
    abstract fun imputationDao(): ImputationDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        fun create(context: Context): BankDatabase =
            Room.databaseBuilder(context, BankDatabase::class.java, "bank.db").build()
    }
}
