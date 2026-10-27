package fr.baptiste.bank.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface EnvelopeDao {
    @Query("SELECT * FROM envelope WHERE archived = 0 ORDER BY type, priority, name")
    fun observeActive(): Flow<List<EnvelopeEntity>>

    @Upsert
    suspend fun upsert(envelope: EnvelopeEntity): Long
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transaction_record WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<TransactionEntity>>

    /** Les lignes dont le hash d'import existe déjà sont ignorées : c'est le dédoublonnage. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>
}
