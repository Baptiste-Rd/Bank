package fr.baptiste.bank.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Canal entre le récepteur système et l'écran, pour afficher un échec d'installation. */
object InstallEvents {
    private val mutableFailures = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failures: SharedFlow<String> = mutableFailures.asSharedFlow()

    internal fun reportFailure(message: String) {
        mutableFailures.tryEmit(message)
    }
}

/**
 * Reçoit le résultat de la session d'installation.
 *
 * Android demande toujours une confirmation pour installer une app hors Play Store :
 * le statut PENDING_USER_ACTION contient l'écran de confirmation à afficher.
 * En cas de succès, le système remplace l'app et ferme le processus en cours.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirmationIntent(intent)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> InstallEvents.reportFailure(failureMessage(status, intent))
        }
    }

    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun failureMessage(status: Int, intent: Intent): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation annulée."
        PackageInstaller.STATUS_FAILURE_CONFLICT,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        -> "La version installée est signée avec une autre clé. " +
            "Désinstalle l'app une fois, puis installe la dernière version."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Espace de stockage insuffisant."
        else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            ?: "L'installation a échoué (code $status)."
    }
}
