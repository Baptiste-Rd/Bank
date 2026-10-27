package fr.baptiste.bank

import android.app.Application
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
        container = AppContainer(this)
    }
}

/**
 * Injection de dépendances manuelle, suffisante pour une app de cette taille.
 * La base n'est ouverte qu'au premier accès.
 */
class AppContainer(application: Application) {
    val database: BankDatabase by lazy { BankDatabase.create(application) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(application) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database, settingsRepository) }
    val updateRepository: UpdateRepository by lazy { UpdateRepository(BuildConfig.UPDATE_REPOSITORY) }
    val apkInstaller: ApkInstaller by lazy { ApkInstaller(application) }
}
