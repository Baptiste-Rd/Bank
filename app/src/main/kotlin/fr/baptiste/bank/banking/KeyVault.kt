package fr.baptiste.bank.banking

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Coffre de la clé privée Enable Banking (le fichier .pem téléchargé à la création de
 * l'application). Cette clé signe chaque requête : quiconque la possède peut lire les comptes
 * liés. Elle n'est donc jamais écrite en clair :
 * - une clé AES-256 est créée dans l'Android Keystore, matériel sécurisé du téléphone ; elle ne
 *   peut pas en sortir, même pour l'app ;
 * - le .pem est chiffré avec elle (AES-GCM) dans le stockage privé de l'app, exclu des
 *   sauvegardes (allowBackup = false dans le manifeste) ;
 * - il n'est déchiffré qu'en mémoire, le temps de signer.
 */
class KeyVault(context: Context, fileName: String = "enable_banking_key.bin") {

    private val file = File(context.filesDir, fileName)

    val hasKey: Boolean get() = file.exists()

    /** Vérifie que [pem] est une clé privée lisible, puis l'enregistre chiffrée. */
    fun store(pem: String) {
        parsePrivateKeyPem(pem)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val encrypted = cipher.doFinal(pem.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        file.writeBytes(byteArrayOf(iv.size.toByte()) + iv + encrypted)
    }

    /** Clé privée déchiffrée, ou null si aucune n'a été importée. */
    fun privateKey(): PrivateKey? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val ivLength = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        val pem = cipher.doFinal(bytes, 1 + ivLength, bytes.size - 1 - ivLength).toString(Charsets.UTF_8)
        return parsePrivateKeyPem(pem)
    }

    fun clear() {
        file.delete()
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "enable_banking_key_wrapper"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * Lit une clé privée RSA au format PEM PKCS#8 (« -----BEGIN PRIVATE KEY----- »), celui
 * qu'Enable Banking fait télécharger. Le format PKCS#1 (« BEGIN RSA PRIVATE KEY ») est refusé
 * avec un message qui explique comment le convertir.
 */
fun parsePrivateKeyPem(pem: String): PrivateKey {
    if (pem.contains("BEGIN RSA PRIVATE KEY")) {
        throw IllegalArgumentException(
            "Clé au format PKCS#1. Convertis-la avec : openssl pkcs8 -topk8 -nocrypt -in cle.pem -out cle-pkcs8.pem",
        )
    }
    val body = pem.substringAfter("-----BEGIN PRIVATE KEY-----", missingDelimiterValue = "")
        .substringBefore("-----END PRIVATE KEY-----")
        .filterNot { it.isWhitespace() }
    require(body.isNotEmpty()) { "Ce fichier ne contient pas de clé privée (« BEGIN PRIVATE KEY » introuvable)." }
    val bytes = try {
        Base64.getDecoder().decode(body)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Le contenu de la clé est illisible.")
    }
    return try {
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    } catch (error: Exception) {
        throw IllegalArgumentException("Ce n'est pas une clé privée RSA valide.", error)
    }
}
