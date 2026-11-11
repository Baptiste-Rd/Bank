package fr.baptiste.bank.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Ce qui empêche de lire un fichier de sauvegarde. Le message est celui que l'écran affiche. */
sealed class BackupError(message: String) : Exception(message) {
    class NotABackup : BackupError("Ce fichier n'est pas une sauvegarde de Bank.")
    class WrongPassword : BackupError("Mot de passe incorrect.")
    class Newer : BackupError("Ce fichier vient d'une version plus récente de Bank. Mets l'app à jour, puis recommence.")
    class FromDemo : BackupError("Ce fichier vient de la démo.")
    class FromRealData : BackupError("Ce fichier contient de vraies données : la démo ne l'ouvre pas.")
    class Unreadable : BackupError("Ce fichier ne peut pas être lu. Rien n'a changé.")
    class NotWritten : BackupError("Le fichier n'a pas pu être écrit.")
}

/** Ce qu'un fichier de sauvegarde dit de lui-même, lu avant de remplacer quoi que ce soit. */
data class BackupManifest(
    /** Version du format du fichier ([BackupFile.FORMAT]). */
    val format: Int,
    /** Version du schéma de la base au moment de la sauvegarde. */
    val schema: Int,
    /** Numéro de build de l'app qui a écrit le fichier. */
    val build: Int,
    /** Moment de la sauvegarde (millisecondes depuis 1970). */
    val savedAt: Long,
    /** Vrai pour un fichier écrit en mode démo : il ne se restaure jamais sur de vraies données. */
    val demo: Boolean,
    val envelopes: Int,
    val operations: Int,
) {
    fun encode(): ByteArray = Properties().apply {
        setProperty("format", format.toString())
        setProperty("schema", schema.toString())
        setProperty("build", build.toString())
        setProperty("savedAt", savedAt.toString())
        setProperty("demo", demo.toString())
        setProperty("envelopes", envelopes.toString())
        setProperty("operations", operations.toString())
    }.toBytes()

    companion object {
        fun decode(bytes: ByteArray): BackupManifest {
            val properties = propertiesOf(bytes)
            fun number(name: String): Long = properties.getProperty(name)?.toLongOrNull() ?: throw BackupError.NotABackup()
            return BackupManifest(
                format = number("format").toInt(),
                schema = number("schema").toInt(),
                build = number("build").toInt(),
                savedAt = number("savedAt"),
                demo = properties.getProperty("demo") == "true",
                envelopes = number("envelopes").toInt(),
                operations = number("operations").toInt(),
            )
        }
    }
}

/**
 * Le fichier de sauvegarde, sans rien d'Android : ce qu'il contient et comment il est chiffré.
 *
 * Contenu : une archive zip avec [MANIFEST], [DATABASE] (le fichier SQLite) et [SETTINGS].
 * Chiffrement : la clé AES-256 est dérivée du mot de passe par PBKDF2-HMAC-SHA256 (sel tiré au
 * hasard, nombre de tours écrit dans l'en-tête), l'archive est chiffrée en AES-GCM. L'en-tête est
 * authentifié avec le contenu : un fichier modifié, ou un mauvais mot de passe, ne s'ouvre pas.
 *
 * En-tête (41 octets) : « BANKSAVE », version du format (1 octet), tours (4), sel (16), IV (12).
 */
object BackupFile {
    const val FORMAT = 1
    const val EXTENSION = "banksave"
    const val MANIFEST = "manifest.properties"
    const val DATABASE = "bank.db"
    const val SETTINGS = "settings.properties"

    /** Recommandation de l'OWASP pour PBKDF2-HMAC-SHA256 (Password Storage Cheat Sheet). */
    const val ITERATIONS = 600_000

    private val MAGIC = "BANKSAVE".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private val HEADER_SIZE = MAGIC.size + 1 + 4 + SALT_SIZE + IV_SIZE

    /** Au-delà, ce n'est pas une sauvegarde de l'app : on ne décompresse pas sans fin. */
    private const val MAX_UNPACKED = 1L shl 30

    fun seal(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).put(FORMAT.toByte()).putInt(iterations).put(salt).put(iv).array()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    /** L'archive en clair, ou une [BackupError] : rien d'autre ne sort d'ici. */
    fun open(sealed: ByteArray, password: CharArray): ByteArray {
        if (sealed.size < HEADER_SIZE + TAG_BITS / 8 || !sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw BackupError.NotABackup()
        val header = sealed.copyOfRange(0, HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header, MAGIC.size, HEADER_SIZE - MAGIC.size)
        val format = buffer.get().toInt()
        if (format > FORMAT) throw BackupError.Newer()
        if (format < 1) throw BackupError.NotABackup()
        val iterations = buffer.getInt()
        if (iterations !in 1..MAX_ITERATIONS) throw BackupError.NotABackup()
        val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
        val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return try {
            cipher.doFinal(sealed, HEADER_SIZE, sealed.size - HEADER_SIZE)
        } catch (error: BadPaddingException) {
            // AES-GCM ne distingue pas un mauvais mot de passe d'un fichier modifié : le sceau ne correspond pas.
            throw BackupError.WrongPassword()
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun pack(entries: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    fun unpack(archive: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val content = ByteArrayOutputStream()
                    while (true) {
                        val read = zip.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_UNPACKED) throw BackupError.NotABackup()
                        content.write(chunk, 0, read)
                    }
                    entries[entry.name] = content.toByteArray()
                }
            }
        } catch (error: java.io.IOException) {
            throw BackupError.NotABackup()
        }
        if (MANIFEST !in entries || DATABASE !in entries || SETTINGS !in entries) throw BackupError.NotABackup()
        return entries
    }

    /**
     * Les réglages, une ligne par clé, avec leur type : B booléen, I entier, L entier long,
     * F et D décimaux, S texte, X ensemble de textes, Y octets. Un type inconnu est ignoré à la
     * lecture, pour qu'un fichier plus récent reste lisible.
     */
    fun encodeSettings(values: Map<String, Any>): ByteArray = Properties().apply {
        for ((name, value) in values) {
            val encoded = when (value) {
                is Boolean -> "B:$value"
                is Int -> "I:$value"
                is Long -> "L:$value"
                is Float -> "F:$value"
                is Double -> "D:$value"
                is String -> "S:$value"
                is Set<*> -> "X:" + value.filterIsInstance<String>().joinToString(",") { URLEncoder.encode(it, "UTF-8") }
                is ByteArray -> "Y:" + Base64.getEncoder().encodeToString(value)
                else -> continue
            }
            setProperty(name, encoded)
        }
    }.toBytes()

    fun decodeSettings(bytes: ByteArray): Map<String, Any> {
        val properties = propertiesOf(bytes)
        val values = LinkedHashMap<String, Any>()
        for (name in properties.stringPropertyNames().sorted()) {
            val encoded = properties.getProperty(name)
            if (encoded.length < 2 || encoded[1] != ':') continue
            val text = encoded.substring(2)
            val value: Any? = when (encoded[0]) {
                'B' -> text == "true"
                'I' -> text.toIntOrNull()
                'L' -> text.toLongOrNull()
                'F' -> text.toFloatOrNull()
                'D' -> text.toDoubleOrNull()
                'S' -> text
                'X' -> if (text.isEmpty()) emptySet<String>() else text.split(",").map { URLDecoder.decode(it, "UTF-8") }.toSet()
                'Y' -> runCatching { Base64.getDecoder().decode(text) }.getOrNull()
                else -> null
            }
            if (value != null) values[name] = value
        }
        return values
    }

    /**
     * L'ordre dans lequel remplir des tables liées par des clés étrangères : une table vient après
     * celles qu'elle cite ([references] : table → tables citées). Pour vider, l'ordre inverse. Des
     * tables qui se citent en rond sortent à la fin, dans l'ordre reçu.
     */
    fun insertionOrder(tables: List<String>, references: Map<String, Set<String>>): List<String> {
        val ordered = mutableListOf<String>()
        val remaining = tables.toMutableList()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { table -> references[table].orEmpty().all { it == table || it in ordered || it !in tables } }
            if (ready.isEmpty()) {
                ordered += remaining
                break
            }
            ordered += ready
            remaining -= ready.toSet()
        }
        return ordered
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val MAX_ITERATIONS = 10_000_000
}

private fun Properties.toBytes(): ByteArray = ByteArrayOutputStream().also { store(it, null) }.toByteArray()

private fun propertiesOf(bytes: ByteArray): Properties = Properties().apply {
    try {
        load(ByteArrayInputStream(bytes))
    } catch (error: Exception) {
        throw BackupError.NotABackup()
    }
}
