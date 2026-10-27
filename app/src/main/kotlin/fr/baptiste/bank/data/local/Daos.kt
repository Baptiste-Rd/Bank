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

    @Query("SELECT * FROM account WHERE external_uid = :uid LIMIT 1")
    suspend fun findByExternalUid(uid: String): AccountEntity?

    /** Le compte courant créé pour les saisies manuelles, pas encore relié à la banque. */
    @Query("SELECT * FROM account WHERE type = 'CURRENT' AND external_uid IS NULL ORDER BY id LIMIT 1")
    suspend fun unlinkedCurrentAccount(): AccountEntity?

    @Query("UPDATE account SET external_uid = :uid, name = :name, iban_suffix = :ibanSuffix WHERE id = :id")
    suspend fun link(id: Long, uid: String, name: String, ibanSuffix: String?)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rule ORDER BY priority, id")
    suspend fun all(): List<RuleEntity>

    @Insert
    suspend fun insert(rule: RuleEntity): Long
}

@Dao
interface EnvelopeDao {
    @Query("SELECT * FROM envelope WHERE archived = 0 ORDER BY type, priority, name")
    fun observeActive(): Flow<List<EnvelopeEntity>>

    @Upsert
    suspend fun upsert(envelope: EnvelopeEntity): Long

    @Query("UPDATE envelope SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT * FROM envelope WHERE archived = 1 ORDER BY name")
    fun observeArchived(): Flow<List<EnvelopeEntity>>

    @Query("UPDATE envelope SET archived = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM envelope WHERE archived = 0")
    suspend fun active(): List<EnvelopeEntity>
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
    @ColumnInfo(name = "whole_period") val wholePeriod: Boolean,
)

/** Une ligne de la liste des opérations, avec le nom des enveloppes où elle est imputée. */
data class OperationRow(
    val id: Long,
    val date: LocalDate,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "clean_label") val label: String,
    val source: TransactionSource,
    /** Mise hors budget par l'utilisateur. */
    val ignored: Boolean,
    @ColumnInfo(name = "envelope_names") val envelopeNames: String?,
    /** Vrai pour un rattrapage : [periodKey] indique alors la période concernée. */
    @ColumnInfo(name = "whole_period") val wholePeriod: Boolean,
    @ColumnInfo(name = "period_key") val periodKey: String?,
)

@Dao
interface ImputationDao {
    @Insert
    suspend fun insert(imputation: ImputationEntity): Long

    @Query("DELETE FROM imputation WHERE transaction_id = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)

    /** Rapprochement : les imputations d'une saisie manuelle passent à l'opération bancaire. */
    @Query("UPDATE imputation SET transaction_id = :to WHERE transaction_id = :from")
    suspend fun moveToTransaction(from: Long, to: Long)

    @Query(
        """
        SELECT i.envelope_id AS envelope_id, t.date AS date, i.amount_cents AS amount_cents,
               i.whole_period AS whole_period
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

    @Query("SELECT * FROM transaction_record WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transaction_record WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<TransactionEntity>

    /** Saisies manuelles candidates au rapprochement, sur une plage de dates. */
    @Query("SELECT * FROM transaction_record WHERE source = 'MANUAL' AND date BETWEEN :from AND :to")
    suspend fun manualBetween(from: LocalDate, to: LocalDate): List<TransactionEntity>

    @Query("UPDATE transaction_record SET reconciled_with_id = :manualId WHERE id = :id")
    suspend fun markReconciled(id: Long, manualId: Long)

    @Query("UPDATE transaction_record SET ignored = :ignored WHERE id = :id")
    suspend fun setIgnored(id: Long, ignored: Boolean)

    /** Opérations à trier : ni rangées dans une enveloppe, ni mises hors budget. */
    @Query(
        """
        SELECT * FROM transaction_record t
        WHERE t.ignored = 0 AND NOT EXISTS (SELECT 1 FROM imputation i WHERE i.transaction_id = t.id)
        """,
    )
    suspend fun unsorted(): List<TransactionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM transaction_record t
        WHERE t.ignored = 0 AND NOT EXISTS (SELECT 1 FROM imputation i WHERE i.transaction_id = t.id)
        """,
    )
    fun observeUnsortedCount(): Flow<Int>

    @Query(
        """
        SELECT t.id AS id, t.date AS date, t.amount_cents AS amount_cents, t.clean_label AS clean_label,
               t.source AS source, t.ignored AS ignored, group_concat(e.name, ', ') AS envelope_names,
               COALESCE(MAX(i.whole_period), 0) AS whole_period, MAX(i.period_key) AS period_key
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

/** Une ligne du journal des transferts, avec le nom des enveloppes (NULL = portefeuille). */
data class TransferRow(
    val id: Long,
    @ColumnInfo(name = "effective_date") val date: LocalDate,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val reason: String?,
    val cancelled: Boolean,
    @ColumnInfo(name = "source_name") val sourceName: String?,
    @ColumnInfo(name = "target_name") val targetName: String?,
    @ColumnInfo(name = "source_envelope_id") val sourceEnvelopeId: Long?,
    @ColumnInfo(name = "target_envelope_id") val targetEnvelopeId: Long?,
)

@Dao
interface TransferDao {
    @Insert
    suspend fun insertAll(transfers: List<TransferEntity>)

    @Query("SELECT * FROM transfer WHERE cancelled = 0 AND effective_date BETWEEN :from AND :to")
    fun observeActive(from: LocalDate, to: LocalDate): Flow<List<TransferEntity>>

    @Query(
        """
        SELECT t.id AS id, t.effective_date AS effective_date, t.amount_cents AS amount_cents,
               t.reason AS reason, t.cancelled AS cancelled,
               s.name AS source_name, d.name AS target_name,
               t.source_envelope_id AS source_envelope_id, t.target_envelope_id AS target_envelope_id
        FROM transfer t
        LEFT JOIN envelope s ON s.id = t.source_envelope_id
        LEFT JOIN envelope d ON d.id = t.target_envelope_id
        ORDER BY t.effective_date DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun observeJournal(limit: Int): Flow<List<TransferRow>>

    /** Annuler ne supprime pas : le transfert reste visible dans le journal, barré. */
    @Query("UPDATE transfer SET cancelled = 1 WHERE id = :id")
    suspend fun cancel(id: Long)
}
