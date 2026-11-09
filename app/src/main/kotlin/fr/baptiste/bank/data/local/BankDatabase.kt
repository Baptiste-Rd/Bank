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
        PlacementEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        EnvelopeEntity::class,
        AllocationEntity::class,
        ImputationEntity::class,
        TransferEntity::class,
        RuleEntity::class,
        LabelAliasEntity::class,
        BalanceEntity::class,
        TransferRuleEntity::class,
        JournalEntity::class,
    ],
    version = 20,
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
    abstract fun balanceDao(): BalanceDao
    abstract fun transferRuleDao(): TransferRuleDao
    abstract fun journalDao(): JournalDao

    abstract fun placementDao(): PlacementDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        /** [name] permet au mode démo d'utiliser une base séparée, sans toucher aux vraies données. */
        fun create(context: Context, name: String = "bank.db"): BankDatabase =
            Room.databaseBuilder(context, BankDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
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

/** Version 7 : autres comptes (livrets, investissement), relevés de solde, virements vers ces comptes. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transaction_record ADD COLUMN transfer_account_id INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS account_balance (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, account_id INTEGER NOT NULL, " +
                "date INTEGER NOT NULL, balance_cents INTEGER NOT NULL, " +
                "FOREIGN KEY(account_id) REFERENCES account(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_account_balance_account_id_date ON account_balance (account_id, date)",
        )
    }
}

/** Version 8 : règles de virement vers mes comptes (« LIVRET A » va vers le Livret A). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS transfer_rule (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pattern TEXT NOT NULL, account_id INTEGER NOT NULL, " +
                "FOREIGN KEY(account_id) REFERENCES account(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transfer_rule_account_id ON transfer_rule (account_id)")
    }
}

/** Version 9 : début de mise de côté d'une provision (rattrapage sur les mois restants). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE envelope ADD COLUMN funding_start TEXT")
    }
}

/** Version 10 : placements, montant versé et enveloppe fiscale (net estimé). */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account ADD COLUMN invested_cents INTEGER")
        db.execSQL("ALTER TABLE account ADD COLUMN tax_wrapper TEXT")
    }
}

/** Version 11 : transferts pris sur la réserve (l'argent déjà sur le compte, hors budget du mois). */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transfer ADD COLUMN from_reserve INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Version 12 : dates fiables. Les opérations gardent le jour de comptabilisation et celui que la
 * banque donne pour le paiement ; les enveloppes, leur départ. Le jour de comptabilisation des
 * opérations déjà importées sans référence se lit dans leur clé (« eb:compte:2026-10-27|... »).
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transaction_record ADD COLUMN booking_date INTEGER")
        db.execSQL("ALTER TABLE transaction_record ADD COLUMN bank_date INTEGER")
        db.execSQL("ALTER TABLE envelope ADD COLUMN start_date INTEGER")
        val day = Regex(""":(\d{4}-\d{2}-\d{2})\|""")
        val found = mutableListOf<Pair<Long, Long>>()
        db.query("SELECT id, import_hash FROM transaction_record WHERE import_hash LIKE 'eb:%'").use { cursor ->
            while (cursor.moveToNext()) {
                val hash = cursor.getString(1) ?: continue
                val match = day.find(hash) ?: continue
                val epochDay = runCatching { java.time.LocalDate.parse(match.groupValues[1]).toEpochDay() }.getOrNull() ?: continue
                found += cursor.getLong(0) to epochDay
            }
        }
        for ((id, epochDay) in found) {
            db.execSQL("UPDATE transaction_record SET booking_date = ? WHERE id = ?", arrayOf<Any>(epochDay, id))
        }
    }
}

/**
 * Version 13 : budgets datés. Une enveloppe a un premier montant, une remise à zéro, une
 * répétition (faux : ponctuel) et un jour de retrait ; une allocation, les bornes de sa période
 * (le passé figé). Un achat prévu devient ponctuel ; un budget « tous les N » garde son reste.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE envelope ADD COLUMN first_amount_cents INTEGER")
        db.execSQL("ALTER TABLE envelope ADD COLUMN first_reset INTEGER")
        db.execSQL("ALTER TABLE envelope ADD COLUMN repeats INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE envelope ADD COLUMN retired_on INTEGER")
        db.execSQL("ALTER TABLE allocation ADD COLUMN start_date INTEGER")
        db.execSQL("ALTER TABLE allocation ADD COLUMN end_date INTEGER")
        db.execSQL("UPDATE envelope SET repeats = 0 WHERE type = 'PLANNED'")
        db.execSQL("UPDATE envelope SET carry_over = 1 WHERE type = 'RECURRING'")
        // Bornes des allocations existantes, lues dans leur clé (« 2026-10 », « 2026-10-12+6W »...).
        val bounds = mutableListOf<Triple<Long, Long, Long>>()
        db.query("SELECT id, period_key FROM allocation").use { cursor ->
            while (cursor.moveToNext()) {
                val period = runCatching { fr.baptiste.bank.domain.period.Period.parse(cursor.getString(1)) }.getOrNull() ?: continue
                bounds += Triple(cursor.getLong(0), period.start.toEpochDay(), period.endInclusive.toEpochDay())
            }
        }
        for ((id, start, end) in bounds) {
            db.execSQL("UPDATE allocation SET start_date = ?, end_date = ? WHERE id = ?", arrayOf<Any>(start, end, id))
        }
    }
}

/** Version 14 : versement prévu chaque mois vers un compte d'épargne (rappel sur l'accueil). */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account ADD COLUMN monthly_target_cents INTEGER")
    }
}

/** Version 15 : historique des changements, pour les annuler. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`at` INTEGER NOT NULL, `label` TEXT NOT NULL, `payload` TEXT NOT NULL, `undone` INTEGER NOT NULL DEFAULT 0)",
        )
    }
}

/**
 * Version 16 : catégories imbriquées. Une catégorie par nom déjà utilisé par une enveloppe, toutes
 * principales ; rien d'autre ne change.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`parent_name` TEXT, `position` INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name` ON `category` (`name`)")
        db.execSQL(
            "INSERT OR IGNORE INTO category (name) SELECT DISTINCT TRIM(group_name) FROM envelope " +
                "WHERE group_name IS NOT NULL AND TRIM(group_name) <> '' AND LOWER(TRIM(group_name)) <> 'autres'",
        )
    }
}

/** Rattrapage d'une provision qui garde la part habituelle des mois passés. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE envelope ADD COLUMN catch_up_from TEXT")
    }
}

/**
 * L'arbre des enveloppes : chaque catégorie devient un groupe (une ligne de envelope sans montant),
 * les parents passent du nom à l'identifiant. « Autres » disparaît : ses enveloppes sont en haut.
 * Les noms qui ne diffèrent que par la casse ne font qu'un groupe, comme ils ne faisaient qu'une
 * catégorie à l'écran. group_name reste vide : le retirer obligerait à reconstruire la table.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE envelope ADD COLUMN parent_id INTEGER")
        db.execSQL("ALTER TABLE envelope ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE envelope ADD COLUMN is_group INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "INSERT OR IGNORE INTO category (name) SELECT DISTINCT TRIM(e.group_name) FROM envelope e " +
                "WHERE e.group_name IS NOT NULL AND TRIM(e.group_name) <> '' AND NOT EXISTS " +
                "(SELECT 1 FROM category c WHERE TRIM(c.name) = TRIM(e.group_name) COLLATE NOCASE)",
        )
        db.execSQL(
            "INSERT INTO envelope (name, type, scale, amount_cents, status, priority, carry_over, archived, every, repeats, position, is_group) " +
                "SELECT TRIM(name), 'DAILY', 'MONTH', 0, 'FLEXIBLE', 0, 0, 0, 1, 1, MIN(position), 1 FROM category " +
                "WHERE TRIM(name) <> '' AND LOWER(TRIM(name)) <> 'autres' GROUP BY TRIM(name) COLLATE NOCASE",
        )
        db.execSQL(
            "UPDATE envelope SET parent_id = (SELECT g.id FROM envelope g WHERE g.is_group = 1 AND g.name = " +
                "(SELECT TRIM(c.parent_name) FROM category c WHERE TRIM(c.name) = envelope.name COLLATE NOCASE AND c.parent_name IS NOT NULL LIMIT 1) " +
                "COLLATE NOCASE LIMIT 1) WHERE is_group = 1",
        )
        db.execSQL(
            "UPDATE envelope SET parent_id = (SELECT g.id FROM envelope g WHERE g.is_group = 1 " +
                "AND g.name = TRIM(envelope.group_name) COLLATE NOCASE LIMIT 1), group_name = NULL " +
                "WHERE is_group = 0 AND group_name IS NOT NULL",
        )
        // Une ancienne fusion a pu laisser une catégorie dans sa propre descendance : elle revient en haut.
        db.execSQL(
            "UPDATE envelope SET parent_id = NULL WHERE is_group = 1 AND id IN (" +
                "WITH RECURSIVE walk(start, current) AS (" +
                "SELECT id, parent_id FROM envelope WHERE is_group = 1 AND parent_id IS NOT NULL " +
                "UNION SELECT w.start, e.parent_id FROM walk w INNER JOIN envelope e ON e.id = w.current WHERE e.parent_id IS NOT NULL" +
                ") SELECT start FROM walk WHERE start = current)",
        )
        db.execSQL("DROP TABLE category")
    }
}

/** La couverture des enfants, à part du montant de la période : rien de ce qui est saisi n'est réécrit. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE allocation ADD COLUMN covered_cents INTEGER NOT NULL DEFAULT 0")
    }
}

/** Les objectifs d'épargne : montant visé, date, compte, et les mouvements de chaque objectif. */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE envelope ADD COLUMN goal_cents INTEGER")
        db.execSQL("ALTER TABLE envelope ADD COLUMN goal_by TEXT")
        db.execSQL("ALTER TABLE envelope ADD COLUMN account_id INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS savings_placement (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "envelope_id INTEGER NOT NULL, date INTEGER NOT NULL, amount_cents INTEGER NOT NULL, is_start INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_placement_envelope_id ON savings_placement (envelope_id)")
    }
}
