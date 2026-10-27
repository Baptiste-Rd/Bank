package fr.baptiste.bank.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.baptiste.bank.domain.model.AccountType
import fr.baptiste.bank.domain.model.EnvelopeStatus
import fr.baptiste.bank.domain.model.EnvelopeType
import fr.baptiste.bank.domain.model.ImputationMode
import fr.baptiste.bank.domain.model.RuleMatchType
import fr.baptiste.bank.domain.model.TransactionSource
import fr.baptiste.bank.domain.period.Scale
import java.time.LocalDate

// Tous les montants sont en centimes (Long). Les périodes sont stockées par leur clé :
// "2026-10" (mois), "2026" (année), "2026-W44" (semaine ISO).

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** Derniers chiffres de l'IBAN, pour reconnaître les virements internes. */
    @ColumnInfo(name = "iban_suffix") val ibanSuffix: String? = null,
    /** Identifiant du compte chez l'agrégateur bancaire (Enable Banking), s'il est synchronisé. */
    @ColumnInfo(name = "external_uid") val externalUid: String? = null,
)

@Entity(
    tableName = "transaction_record",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("account_id"),
        Index("date"),
        // Une même ligne bancaire ne peut être importée qu'une fois.
        // Les saisies manuelles n'ont pas de hash (NULL), et SQLite autorise plusieurs NULL.
        Index(value = ["import_hash"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    val date: LocalDate,
    /** Négatif pour une dépense, positif pour une rentrée. */
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "raw_label") val rawLabel: String,
    @ColumnInfo(name = "clean_label") val cleanLabel: String,
    @ColumnInfo(name = "import_hash") val importHash: String? = null,
    val source: TransactionSource,
    /** Saisie manuelle remplacée par cette opération bancaire lors d'un rapprochement. */
    @ColumnInfo(name = "reconciled_with_id") val reconciledWithId: Long? = null,
    /** Hors budget : l'opération est vue mais rangée nulle part (salaire, virement entre comptes...). */
    @ColumnInfo(defaultValue = "0") val ignored: Boolean = false,
)

@Entity(tableName = "envelope")
data class EnvelopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: EnvelopeType,
    val scale: Scale,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val status: EnvelopeStatus,
    val priority: Int = 0,
    @ColumnInfo(name = "carry_over") val carryOver: Boolean = false,
    val archived: Boolean = false,
    /** Nombre d'unités de [scale] par période : 6 avec WEEK pour « toutes les 6 semaines ». */
    @ColumnInfo(defaultValue = "1") val every: Int = 1,
    /** Début d'une des périodes pour une fréquence glissante ; NULL pour une période calendaire. */
    @ColumnInfo(name = "anchor_date") val anchorDate: LocalDate? = null,
)

@Entity(
    tableName = "allocation",
    foreignKeys = [
        ForeignKey(
            entity = EnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["envelope_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["envelope_id", "period_key"], unique = true)],
)
data class AllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "envelope_id") val envelopeId: Long,
    @ColumnInfo(name = "period_key") val periodKey: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)

@Entity(
    tableName = "imputation",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["envelope_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("transaction_id"), Index(value = ["envelope_id", "period_key"])],
)
data class ImputationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "envelope_id") val envelopeId: Long,
    @ColumnInfo(name = "period_key") val periodKey: String,
    /** Part de la transaction imputée ici : une transaction peut être répartie sur plusieurs lignes. */
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val mode: ImputationMode,
    /** Rattrapage : la dépense porte sur toute la période, pas sur la date de la transaction. */
    @ColumnInfo(name = "whole_period", defaultValue = "0") val wholePeriod: Boolean = false,
)

@Entity(
    tableName = "transfer",
    foreignKeys = [
        ForeignKey(
            entity = EnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_envelope_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_envelope_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("source_envelope_id"), Index("target_envelope_id"), Index("effective_date")],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "effective_date") val effectiveDate: LocalDate,
    /** Enveloppe d'origine ; NULL signifie le portefeuille. */
    @ColumnInfo(name = "source_envelope_id") val sourceEnvelopeId: Long?,
    /** Enveloppe de destination ; NULL signifie le portefeuille. */
    @ColumnInfo(name = "target_envelope_id") val targetEnvelopeId: Long?,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val reason: String? = null,
    val cancelled: Boolean = false,
)

@Entity(
    tableName = "rule",
    foreignKeys = [
        ForeignKey(
            entity = EnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["envelope_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("envelope_id")],
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "match_type") val matchType: RuleMatchType,
    val pattern: String,
    @ColumnInfo(name = "envelope_id") val envelopeId: Long,
    val priority: Int = 0,
)
