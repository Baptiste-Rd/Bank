package fr.baptiste.bank.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.baptiste.bank.data.local.BankDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Une sauvegarde lue et vérifiée, prête à remplacer les données. */
class LoadedBackup internal constructor(val manifest: BackupManifest, internal val entries: Map<String, ByteArray>)

/**
 * Sauvegarder et restaurer toutes les données de l'app : la base et les réglages, jamais la
 * connexion à la banque ni sa clé (elles restent sur le téléphone, dans leurs propres fichiers).
 *
 * Un seul mécanisme sert dans les deux sens, [copyTables] : il recopie toutes les tables d'une
 * base vers une autre, dans une transaction.
 * - Sauvegarder : la base ouverte est recopiée dans un fichier SQLite neuf, mis dans l'archive.
 * - Restaurer : le fichier de l'archive est ouvert par Room, qui lui applique les migrations
 *   jusqu'au schéma de l'app, puis il est recopié dans la base ouverte. Les écrans se mettent à
 *   jour comme après n'importe quelle écriture ; si quoi que ce soit échoue, rien n'a changé.
 *
 * Avant de remplacer, l'état d'avant est gardé dans le stockage privé ([undoRestore]), jusqu'au
 * prochain lancement ([forgetPrevious]).
 */
class BackupRepository(
    private val context: Context,
    private val database: BankDatabase,
    private val settings: SettingsRepository,
    /** Mode démo : ses fichiers ne se restaurent que dans la démo, et inversement. */
    private val demo: Boolean,
    private val build: Int,
    /** Ce qui suit une restauration (périodes à jour, synchronisation à reprendre), avec le moment de la sauvegarde restaurée. */
    private val afterRestore: suspend (savedAt: Long) -> Unit = {},
    private val workName: String = workName(demo),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val previous = File(File(context.filesDir, workName), PREVIOUS)

    /** Vrai tant que l'état d'avant la dernière restauration est gardé. */
    val canUndoRestore: Boolean get() = previous.exists()

    /** Écrit la sauvegarde chiffrée dans le fichier choisi par l'utilisateur. */
    suspend fun save(uri: Uri, password: CharArray): BackupManifest = mutex.withLock {
        withContext(Dispatchers.IO) {
            val (manifest, archive) = archive()
            val sealed = BackupFile.seal(archive, password)
            try {
                // « wt » vide un fichier remplacé ; certains fournisseurs ne connaissent que « w ».
                val stream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                    ?: context.contentResolver.openOutputStream(uri, "w")
                    ?: throw IOException("Fichier inaccessible")
                stream.use { it.write(sealed) }
            } catch (error: IOException) {
                throw BackupError.NotWritten()
            } catch (error: SecurityException) {
                throw BackupError.NotWritten()
            }
            manifest
        }
    }

    /** La sauvegarde chiffrée, en mémoire ([iterations] : moins de tours pour les tests). */
    suspend fun sealedBackup(password: CharArray, iterations: Int = BackupFile.ITERATIONS): ByteArray = mutex.withLock {
        withContext(Dispatchers.IO) { BackupFile.seal(archive().second, password, iterations) }
    }

    /** Lit et vérifie le fichier choisi, sans rien changer. */
    suspend fun load(uri: Uri, password: CharArray): LoadedBackup = withContext(Dispatchers.IO) {
        val sealed = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw BackupError.Unreadable()
        } catch (error: IOException) {
            throw BackupError.Unreadable()
        } catch (error: SecurityException) {
            throw BackupError.Unreadable()
        }
        open(sealed, password)
    }

    /** Déchiffre et vérifie une sauvegarde, sans rien changer. */
    suspend fun open(sealed: ByteArray, password: CharArray): LoadedBackup = withContext(Dispatchers.IO) {
        verified(BackupFile.unpack(BackupFile.open(sealed, password)))
    }

    /** Les données actuelles, comme si elles venaient d'être lues dans un fichier. */
    suspend fun current(): LoadedBackup = mutex.withLock {
        withContext(Dispatchers.IO) { verified(BackupFile.unpack(archive().second)) }
    }

    /** Remplace toutes les données par celles de [loaded] ; l'état d'avant est gardé pour [undoRestore]. */
    suspend fun restore(loaded: LoadedBackup) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val before = archive().second
            previous.parentFile?.mkdirs()
            previous.writeBytes(before)
            try {
                replace(loaded, undo = verified(BackupFile.unpack(before)))
            } catch (error: CancellationException) {
                previous.delete()
                throw error
            } catch (error: Exception) {
                previous.delete()
                throw error as? BackupError ?: BackupError.Unreadable()
            }
        }
    }

    /** Remet les données d'avant la dernière restauration. Faux s'il n'y a plus rien à remettre. */
    suspend fun undoRestore(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!previous.exists()) return@withContext false
            val kept = verified(BackupFile.unpack(previous.readBytes()))
            val now = verified(BackupFile.unpack(archive().second))
            try {
                replace(kept, undo = now)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw error as? BackupError ?: BackupError.Unreadable()
            }
            previous.delete()
            true
        }
    }

    /** Supprime la copie d'avant la dernière restauration, quand « Annuler » n'est plus proposé. */
    suspend fun forgetUndo() = mutex.withLock {
        withContext(Dispatchers.IO) { previous.delete() }
    }

    private fun verified(entries: Map<String, ByteArray>): LoadedBackup {
        val manifest = BackupManifest.decode(entries.getValue(BackupFile.MANIFEST))
        if (manifest.format > BackupFile.FORMAT || manifest.schema > database.openHelper.readableDatabase.version) throw BackupError.Newer()
        if (manifest.demo != demo) throw if (manifest.demo) BackupError.FromDemo() else BackupError.FromRealData()
        if (!entries.getValue(BackupFile.DATABASE).startsWith(SQLITE_HEADER)) throw BackupError.NotABackup()
        return LoadedBackup(manifest, entries)
    }

    /** L'archive en clair des données actuelles : la base recopiée dans un fichier neuf, et les réglages. */
    private suspend fun archive(): Pair<BackupManifest, ByteArray> {
        val values = settings.exportAll()
        val name = "$workName-out.db"
        context.deleteDatabase(name)
        val out = BankDatabase.scratch(context, name)
        var schema = 0
        var envelopes = 0
        var operations = 0
        try {
            val target = out.openHelper.writableDatabase
            // Une transaction sur la base ouverte : la copie voit un état cohérent, sans écriture au milieu.
            database.runInTransaction(
                Runnable {
                    val source = database.openHelper.writableDatabase
                    schema = source.version
                    out.runInTransaction(Runnable { copyTables(source, target) })
                    envelopes = count(source, ENVELOPES)
                    operations = count(source, OPERATIONS)
                },
            )
        } finally {
            out.close()
        }
        val file = context.getDatabasePath(name)
        val bytes = file.readBytes()
        context.deleteDatabase(name)
        val manifest = BackupManifest(BackupFile.FORMAT, schema, build, now(), demo, envelopes, operations)
        val archive = BackupFile.pack(
            mapOf(
                BackupFile.MANIFEST to manifest.encode(),
                BackupFile.DATABASE to bytes,
                BackupFile.SETTINGS to BackupFile.encodeSettings(values),
            ),
        )
        return manifest to archive
    }

    /**
     * Remplace la base, puis les réglages. Tout ce qui peut échouer à la lecture se fait avant la
     * première écriture. Une fois la base remplacée, plus rien ne s'interrompt : les réglages
     * suivent, et s'ils échouent la base revient à [undo]. Une restauration réussit donc en entier,
     * ou ne change rien.
     */
    private suspend fun replace(loaded: LoadedBackup, undo: LoadedBackup) {
        val values = BackupFile.decodeSettings(loaded.entries.getValue(BackupFile.SETTINGS))
        copyIn(loaded)
        withContext(NonCancellable) {
            try {
                settings.replaceAll(values)
            } catch (error: Exception) {
                copyIn(undo)
                database.invalidationTracker.refreshVersionsAsync()
                throw BackupError.Unreadable()
            }
            // Les écritures sont passées hors des DAO : on demande à Room de prévenir les écrans.
            database.invalidationTracker.refreshVersionsAsync()
            // La suite (périodes, synchronisation) se refait à chaque ouverture de l'app : son échec
            // n'annule pas une restauration dont les données sont déjà en place.
            runCatching { afterRestore(loaded.manifest.savedAt) }
        }
    }

    /** La base de [loaded] à la place de la base ouverte, en une transaction. */
    private fun copyIn(loaded: LoadedBackup) {
        val name = "$workName-in.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.writeBytes(loaded.entries.getValue(BackupFile.DATABASE))
        val candidate = BankDatabase.scratch(context, name)
        try {
            // La première ouverture applique les migrations et vérifie le schéma obtenu : une
            // sauvegarde d'un schéma plus ancien arrive ici au schéma de l'app, ou échoue sans rien changer.
            val source = candidate.openHelper.writableDatabase
            // Un fichier illisible aurait été remplacé par une base vide : on ne restaure pas du vide à la place des données.
            if (count(source, OPERATIONS) != loaded.manifest.operations || count(source, ENVELOPES) != loaded.manifest.envelopes) {
                throw BackupError.Unreadable()
            }
            database.runInTransaction(Runnable { copyTables(source, database.openHelper.writableDatabase) })
        } finally {
            candidate.close()
            context.deleteDatabase(name)
        }
    }

    companion object {
        private const val PREVIOUS = "previous.zip"
        private const val OPERATIONS = "SELECT COUNT(*) FROM transaction_record"

        // Colonnes présentes depuis le schéma 1 : le compte vaut aussi pour un fichier d'un schéma plus ancien.
        private const val ENVELOPES = "SELECT COUNT(*) FROM envelope WHERE archived = 0"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        private fun workName(demo: Boolean) = if (demo) "backup_demo" else "backup"

        /** Au lancement : l'état gardé par la dernière restauration n'a plus lieu d'être. */
        fun forgetPrevious(context: Context) {
            for (demo in listOf(false, true)) File(File(context.filesDir, workName(demo)), PREVIOUS).delete()
        }
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun count(database: SupportSQLiteDatabase, sql: String): Int =
    database.query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

/** Les tables de l'app, sans celles de SQLite, d'Android ni de Room. */
private fun tablesOf(database: SupportSQLiteDatabase): List<String> =
    database.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (!name.startsWith("sqlite_") && !name.startsWith("android_") && !name.startsWith("room_")) add(name)
            }
        }
    }

private fun columnsOf(database: SupportSQLiteDatabase, table: String): List<String> =
    database.query("PRAGMA table_info(`$table`)").use { cursor ->
        val name = cursor.getColumnIndexOrThrow("name")
        buildList { while (cursor.moveToNext()) add(cursor.getString(name)) }
    }

private fun referencesOf(database: SupportSQLiteDatabase, table: String): Set<String> =
    database.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
        val cited = cursor.getColumnIndexOrThrow("table")
        buildSet { while (cursor.moveToNext()) add(cursor.getString(cited)) }
    }

/**
 * Remplace le contenu de toutes les tables de [to] par celui de [from], dans la transaction en
 * cours de [to]. Les tables et les colonnes sont lues dans les bases : une table ajoutée plus tard
 * est recopiée sans toucher à ce code. Les tables se vident des plus dépendantes aux moins
 * dépendantes et se remplissent dans l'ordre inverse (clés étrangères) ; les compteurs
 * d'identifiants suivent, pour qu'un identifiant supprimé ne soit jamais redonné.
 */
internal fun copyTables(from: SupportSQLiteDatabase, to: SupportSQLiteDatabase) {
    val tables = tablesOf(to)
    val source = tablesOf(from).toSet()
    val order = BackupFile.insertionOrder(tables, tables.associateWith { referencesOf(to, it) })
    to.execSQL("PRAGMA defer_foreign_keys = ON")
    for (table in order.asReversed()) to.execSQL("DELETE FROM `$table`")
    for (table in order) {
        if (table !in source) continue
        val available = columnsOf(from, table).toSet()
        val columns = columnsOf(to, table).filter { it in available }
        if (columns.isEmpty()) continue
        val list = columns.joinToString(", ") { "`$it`" }
        val insert = to.compileStatement("INSERT INTO `$table` ($list) VALUES (${columns.joinToString(", ") { "?" }})")
        try {
            from.query("SELECT $list FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    insert.clearBindings()
                    for (index in columns.indices) {
                        when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> insert.bindNull(index + 1)
                            Cursor.FIELD_TYPE_INTEGER -> insert.bindLong(index + 1, cursor.getLong(index))
                            Cursor.FIELD_TYPE_FLOAT -> insert.bindDouble(index + 1, cursor.getDouble(index))
                            Cursor.FIELD_TYPE_BLOB -> insert.bindBlob(index + 1, cursor.getBlob(index))
                            else -> insert.bindString(index + 1, cursor.getString(index))
                        }
                    }
                    insert.executeInsert()
                }
            }
        } finally {
            insert.close()
        }
    }
    val hasCounters = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'"
    if (count(from, hasCounters) > 0 && count(to, hasCounters) > 0) {
        to.execSQL("DELETE FROM sqlite_sequence")
        from.query("SELECT name, seq FROM sqlite_sequence").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0) in tables) {
                    to.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)", arrayOf<Any>(cursor.getString(0), cursor.getLong(1)))
                }
            }
        }
    }
}
