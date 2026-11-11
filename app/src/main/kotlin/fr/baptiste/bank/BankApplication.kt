package fr.baptiste.bank

import android.app.Application
import fr.baptiste.bank.banking.BankSyncRepository
import fr.baptiste.bank.banking.KeyVault
import fr.baptiste.bank.data.BackupRepository
import fr.baptiste.bank.data.BudgetRepository
import fr.baptiste.bank.data.SettingsRepository
import fr.baptiste.bank.data.local.BankDatabase
import fr.baptiste.bank.update.ApkInstaller
import fr.baptiste.bank.update.UpdateRepository

/**
 * Point d'entrée de l'application. Il porte le conteneur de dépendances,
 * créé une seule fois et partagé par tous les écrans.
 */
class BankApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // L'état gardé par une restauration ne vaut que jusqu'au lancement suivant.
        BackupRepository.forgetPrevious(this)
        container = AppContainer(this)
    }
}

/**
 * Injection de dépendances manuelle, suffisante pour une app de cette taille.
 * La base n'est ouverte qu'au premier accès.
 */
class AppContainer(application: Application) {
    /**
     * Mode démo : une base et des réglages séparés, remplis de données d'exemple. Il sert aux
     * captures d'écran automatiques et ne touche jamais aux vraies données. Il doit être activé
     * avant le premier accès à la base.
     */
    var isDemo: Boolean = false
        private set

    fun enableDemo() {
        isDemo = true
    }

    val database: BankDatabase by lazy { BankDatabase.create(application, if (isDemo) "demo.db" else "bank.db") }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(application, if (isDemo) "settings_demo" else "settings")
    }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database, settingsRepository) }
    /**
     * Connexion bancaire. En mode démo, réglages et clé vont dans des fichiers à part : la démo
     * ne voit jamais la vraie connexion, et inversement.
     */
    val bankSyncRepository: BankSyncRepository? by lazy {
        BankSyncRepository(
            context = application,
            budget = budgetRepository,
            vault = KeyVault(application, if (isDemo) "enable_banking_key_demo.bin" else "enable_banking_key.bin"),
            storeName = if (isDemo) "bank_demo" else "bank",
        )
    }
    /** Sauvegarde et restauration : la base et les réglages du mode en cours, jamais la connexion bancaire. */
    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            context = application,
            database = database,
            settings = settingsRepository,
            demo = isDemo,
            build = BuildConfig.VERSION_CODE,
            afterRestore = { savedAt ->
                // Comme à l'ouverture de l'app : les périodes commencées depuis la sauvegarde s'enregistrent.
                budgetRepository.ensurePeriods()
                budgetRepository.realignImputationPeriods()
                bankSyncRepository?.rewindTo(savedAt)
            },
        )
    }
    val updateRepository: UpdateRepository by lazy { UpdateRepository(BuildConfig.UPDATE_REPOSITORY) }
    val apkInstaller: ApkInstaller by lazy { ApkInstaller(application) }
}
