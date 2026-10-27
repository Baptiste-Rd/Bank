package fr.baptiste.bank.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import fr.baptiste.bank.domain.model.TransactionSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AccountDao {
    @Query("SELECT id FROM account WHERE type = 'CURRENT' ORDER BY id LIMIT 1")
    suspend fun currentAccountId(): Long?

    @Insert
    suspend fun insert(account: AccountEntity): Long
}

@Dao
interface EnvelopeDao {
    @Query("SELECT * FROM envelope WHERE archived = 0 ORDER BY type, priority, name")
    fun observeActive(): Flow<List<EnvelopeEntity>>

    @Upsert
    suspend fun upsert(envelope: EnvelopeEntity): Long

    @Query("UPDATE envelope SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}

@Dao
interface AllocationDao {
    @Query("SELECT * FROM allocation")
    fun observeAll(): Flow<List<AllocationEntity>>
}

/** Une dépense vue depuis une enveloppe : la date vient de la transaction, le montant de l'imputation. */
data class SpendingRow(
    @ColumnInfo(name = "envelope_id") val envelopeId: Long,
    val date: LocalDate,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)

/** Une ligne de la liste des opérations, avec le nom des enveloppes où elle est imputée. */
data class OperationRow(
    val id: Long,
    val date: LocalDate,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "clean_label") val label: String,
    val source: TransactionSource,
    @ColumnInfo(name = "envelope_names") val envelopeNames: String?,
)

@Dao
interface ImputationDao {
    @Insert
    suspend fun insert(imputation: ImputationEntity): Long

    @Query("DELETE FROM imputation WHERE transaction_id = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)

    @Query(
        """
        SELECT i.envelope_id AS envelope_id, t.date AS date, i.amount_cents AS amount_cents
        FROM imputation i
        INNER JOIN transaction_record t ON t.id = i.transaction_id
        WHERE t.date BETWEEN :from AND :to
        """,
    )
    fun observeSpendings(from: LocalDate, to: LocalDate): Flow<List<SpendingRow>>
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transaction_record WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<TransactionEntity>>

    /** Les lignes dont le hash d'import existe déjà sont ignorées : c'est le dédoublonnage. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("DELETE FROM transaction_record WHERE id = :id AND source = 'MANUAL'")
    suspend fun deleteManual(id: Long)

    @Query(
        """
        SELECT t.id AS id, t.date AS date, t.amount_cents AS amount_cents, t.clean_label AS clean_label,
               t.source AS source, group_concat(e.name, ', ') AS envelope_names
        FROM transaction_record t
        LEFT JOIN imputation i ON i.transaction_id = t.id
        LEFT JOIN envelope e ON e.id = i.envelope_id
        GROUP BY t.id
        ORDER BY t.date DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun observeRecentOperations(limit: Int): Flow<List<OperationRow>>
}
