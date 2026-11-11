package fr.baptiste.bank.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le fichier de sauvegarde : ce qui est écrit se relit, et rien ne s'ouvre sans le mot de passe. */
class BackupFileTest {

    // Peu de tours : le test vérifie le format, pas la lenteur voulue de la dérivation.
    private val fast = 1_000
    private val content = BackupFile.pack(
        mapOf(
            BackupFile.MANIFEST to BackupManifest(1, 20, 250, 1_794_000_000_000, demo = false, envelopes = 12, operations = 340).encode(),
            BackupFile.DATABASE to ByteArray(5_000) { (it % 251).toByte() },
            BackupFile.SETTINGS to BackupFile.encodeSettings(mapOf("monthly_income_cents" to 240_000L)),
        ),
    )

    private inline fun <reified E : BackupError> failsWith(block: () -> Unit): Boolean =
        try {
            block()
            false
        } catch (error: BackupError) {
            error is E
        }

    @Test
    fun `what is sealed opens with the same password`() {
        val sealed = BackupFile.seal(content, "correct horse".toCharArray(), fast)
        assertTrue(BackupFile.open(sealed, "correct horse".toCharArray()).contentEquals(content))
    }

    @Test
    fun `the default derivation follows the recommended number of rounds`() {
        val sealed = BackupFile.seal(ByteArray(8), "a".toCharArray())
        assertEquals(600_000, java.nio.ByteBuffer.wrap(sealed, 9, 4).getInt())
        assertTrue(BackupFile.open(sealed, "a".toCharArray()).contentEquals(ByteArray(8)))
    }

    @Test
    fun `a wrong password opens nothing`() {
        val sealed = BackupFile.seal(content, "correct horse".toCharArray(), fast)
        assertTrue(failsWith<BackupError.WrongPassword> { BackupFile.open(sealed, "correct horsf".toCharArray()) })
        assertTrue(failsWith<BackupError.WrongPassword> { BackupFile.open(sealed, CharArray(0)) })
    }

    @Test
    fun `a changed file opens nothing`() {
        val sealed = BackupFile.seal(content, "p".toCharArray(), fast)
        // Un octet du contenu, puis un octet de l'en-tête (le sel) : le sceau couvre les deux.
        val body = sealed.copyOf().also { it[sealed.size - 20] = (it[sealed.size - 20] + 1).toByte() }
        val header = sealed.copyOf().also { it[15] = (it[15] + 1).toByte() }
        assertTrue(failsWith<BackupError.WrongPassword> { BackupFile.open(body, "p".toCharArray()) })
        assertTrue(failsWith<BackupError.WrongPassword> { BackupFile.open(header, "p".toCharArray()) })
    }

    @Test
    fun `the file shows none of its content`() {
        val marker = "CARREFOUR CITY 23,40"
        val plain = BackupFile.pack(
            mapOf(
                BackupFile.MANIFEST to ByteArray(0),
                BackupFile.DATABASE to marker.repeat(50).toByteArray(),
                BackupFile.SETTINGS to ByteArray(0),
            ),
        )
        val sealed = BackupFile.seal(plain, "p".toCharArray(), fast)
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("CARREFOUR"))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("bank.db"))
    }

    @Test
    fun `two saves of the same data differ`() {
        val first = BackupFile.seal(content, "p".toCharArray(), fast)
        val second = BackupFile.seal(content, "p".toCharArray(), fast)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `another file is not a backup`() {
        assertTrue(failsWith<BackupError.NotABackup> { BackupFile.open("-----BEGIN PRIVATE KEY-----".toByteArray() + ByteArray(100), "p".toCharArray()) })
        assertTrue(failsWith<BackupError.NotABackup> { BackupFile.open(ByteArray(0), "p".toCharArray()) })
        assertTrue(failsWith<BackupError.NotABackup> { BackupFile.unpack("pas une archive".toByteArray()) })
        assertTrue(failsWith<BackupError.NotABackup> { BackupFile.unpack(BackupFile.pack(mapOf(BackupFile.DATABASE to ByteArray(3)))) })
    }

    @Test
    fun `a file of a later format asks for an update`() {
        val sealed = BackupFile.seal(content, "p".toCharArray(), fast).also { it[8] = 2 }
        assertTrue(failsWith<BackupError.Newer> { BackupFile.open(sealed, "p".toCharArray()) })
    }

    @Test
    fun `the archive gives back its three entries`() {
        val entries = BackupFile.unpack(content)
        assertEquals(listOf(BackupFile.MANIFEST, BackupFile.DATABASE, BackupFile.SETTINGS), entries.keys.toList())
        assertEquals(5_000, entries.getValue(BackupFile.DATABASE).size)
        assertEquals(
            BackupManifest(1, 20, 250, 1_794_000_000_000, demo = false, envelopes = 12, operations = 340),
            BackupManifest.decode(entries.getValue(BackupFile.MANIFEST)),
        )
    }

    @Test
    fun `settings keep their type and their text`() {
        val review = "Mois correct.\nLigne 2 : « é » = 12 % \\ fin\n"
        val values = mapOf<String, Any>(
            "monthly_income_cents" to 240_000L,
            "salary_next_month" to true,
            "salary_key" to "VIR SEPA /DE SOCIETE EXEMPLE",
            "review_2026-10" to review,
            "count" to 3,
            "ratio" to 0.5f,
            "precise" to 0.25,
            "tags" to setOf("a,b", "c d", "é"),
            "empty" to emptySet<String>(),
        )
        val decoded = BackupFile.decodeSettings(BackupFile.encodeSettings(values))
        assertEquals(values, decoded)
        assertEquals(java.lang.Long::class.java, decoded.getValue("monthly_income_cents").javaClass)
        assertEquals(java.lang.Integer::class.java, decoded.getValue("count").javaClass)
    }

    @Test
    fun `bytes in settings come back`() {
        val decoded = BackupFile.decodeSettings(BackupFile.encodeSettings(mapOf("blob" to byteArrayOf(0, 1, -1, 127))))
        assertTrue((decoded.getValue("blob") as ByteArray).contentEquals(byteArrayOf(0, 1, -1, 127)))
    }

    @Test
    fun `a table is filled after the tables it refers to`() {
        val tables = listOf("imputation", "transaction_record", "envelope", "account", "allocation", "journal_entry")
        val references = mapOf(
            "imputation" to setOf("transaction_record", "envelope"),
            "transaction_record" to setOf("account"),
            "allocation" to setOf("envelope"),
        )
        val order = BackupFile.insertionOrder(tables, references)
        assertEquals(tables.toSet(), order.toSet())
        assertEquals(tables.size, order.size)
        for ((table, cited) in references) for (other in cited) assertTrue(order.indexOf(other) < order.indexOf(table))
    }

    @Test
    fun `tables that refer to each other or to themselves are all kept`() {
        val order = BackupFile.insertionOrder(listOf("a", "b", "c"), mapOf("a" to setOf("b"), "b" to setOf("a"), "c" to setOf("c", "absent")))
        assertEquals(listOf("c", "a", "b"), order)
    }
}
