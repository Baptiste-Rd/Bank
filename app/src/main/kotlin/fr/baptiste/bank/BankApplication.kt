package fr.baptiste.bank

import android.app.Application
import fr.baptiste.bank.data.local.BankDatabase

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
}
