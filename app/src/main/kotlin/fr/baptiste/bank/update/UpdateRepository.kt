package fr.baptiste.bank.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lit la dernière release du dépôt GitHub via l'API publique.
 * Le dépôt étant public, aucun jeton n'est nécessaire (limite de 60 requêtes par heure).
 */
class UpdateRepository(private val repository: String) {

    /** Retourne la dernière release publiée, ou null s'il n'y en a pas encore. */
    suspend fun latestRelease(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$repository/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(
                    connection.inputStream.bufferedReader().use { it.readText() },
                )
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub a répondu ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(json: String): AvailableUpdate? {
        val release = JSONObject(json)
        val tag = release.getString("tag_name")
        val versionCode = versionCodeFromTag(tag) ?: return null
        val assets = release.getJSONArray("assets")
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?: return null
        return AvailableUpdate(
            versionCode = versionCode,
            tag = tag,
            apkUrl = apk.getString("browser_download_url"),
            apkSizeBytes = apk.optLong("size", -1),
            notes = release.optString("body"),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
