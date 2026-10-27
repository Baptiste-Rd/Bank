package fr.baptiste.bank.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Télécharge un APK et le confie à l'installateur système (API PackageInstaller).
 *
 * Le fichier est écrit directement dans la session d'installation, sans passer par
 * un fichier temporaire. Android vérifie ensuite que l'APK est signé avec la même clé
 * que l'app installée, puis demande une confirmation à l'utilisateur.
 */
class ApkInstaller(private val context: Context) {

    /** Vrai si l'utilisateur a autorisé cette app à installer des applications. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Écran des paramètres où l'utilisateur autorise l'installation depuis cette app. */
    fun installPermissionSettings(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Télécharge l'APK puis lance l'installation. [onProgress] reçoit une valeur entre 0 et 1,
     * ou null si la taille du fichier est inconnue. Le résultat de l'installation arrive
     * ensuite dans [InstallResultReceiver].
     */
    suspend fun downloadAndInstall(update: AvailableUpdate, onProgress: (Float?) -> Unit) =
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName(context.packageName) }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    writeApk(update, session, onProgress)
                    session.commit(resultIntent(sessionId).intentSender)
                }
            } catch (error: Exception) {
                installer.abandonSession(sessionId)
                throw error
            }
        }

    private fun writeApk(
        update: AvailableUpdate,
        session: PackageInstaller.Session,
        onProgress: (Float?) -> Unit,
    ) {
        // L'URL de téléchargement GitHub redirige vers un autre hôte HTTPS :
        // HttpURLConnection suit cette redirection car le protocole reste le même.
        val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Téléchargement refusé (${connection.responseCode})")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.apkSizeBytes
            connection.inputStream.use { input ->
                session.openWrite("bank.apk", 0, total).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(if (total > 0) written.toFloat() / total else null)
                    }
                    session.fsync(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun resultIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
        // Le système ajoute le statut dans les extras : le PendingIntent doit rester modifiable.
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }
}
