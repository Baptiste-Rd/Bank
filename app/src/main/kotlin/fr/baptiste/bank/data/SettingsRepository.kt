package fr.baptiste.bank.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


/** Style de l'interface, réglable depuis le menu ⋮. */
enum class UiStyle { GLASS, CLASSIC }

/**
 * Réglages simples (clé / valeur) stockés avec DataStore.
 * Ils n'ont pas leur place dans la base relationnelle : une seule valeur, pas de relation.
 */
class SettingsRepository(context: Context, storeName: String = "settings") {

    // Même fichier que l'ancien délégué preferencesDataStore("settings") : les réglages existants sont conservés.
    // Une seule instance par fichier et par processus : le conteneur de l'app garantit qu'il n'y en a qu'une.
    private val settingsStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(storeName) },
    )

    private val monthlyIncomeKey = longPreferencesKey("monthly_income_cents")

    /** Salaire mensuel net, ou null tant qu'il n'a pas été renseigné. */
    val monthlyIncome: Flow<Money?> = settingsStore.data.map { preferences ->
        preferences[monthlyIncomeKey]?.let(::Money)
    }

    suspend fun setMonthlyIncome(income: Money) {
        settingsStore.edit { it[monthlyIncomeKey] = income.cents }
    }

    private val uiStyleKey = stringPreferencesKey("ui_style")

    /** Liquid Glass par défaut ; le style classique reste disponible depuis le menu. */
    val uiStyle: Flow<UiStyle> = settingsStore.data.map { preferences ->
        preferences[uiStyleKey]?.let { saved -> UiStyle.entries.firstOrNull { it.name == saved } } ?: UiStyle.GLASS
    }

    suspend fun setUiStyle(style: UiStyle) {
        settingsStore.edit { it[uiStyleKey] = style.name }
    }

    private val glassTuningKey = stringPreferencesKey("glass_tuning")

    /** Réglages du verre choisis dans le menu développeur (« clé=valeur;... »), ou null. */
    val glassTuning: Flow<String?> = settingsStore.data.map { it[glassTuningKey] }

    suspend fun setGlassTuning(encoded: String?) {
        settingsStore.edit { if (encoded == null) it.remove(glassTuningKey) else it[glassTuningKey] = encoded }
    }

    private val homeDailyKey = stringPreferencesKey("home_period")

    /** Accueil : vrai pour afficher le reste du jour, faux (par défaut) pour le reste du mois. */
    val homeShowsDay: Flow<Boolean> = settingsStore.data.map { it[homeDailyKey] == "DAY" }

    suspend fun setHomeShowsDay(day: Boolean) {
        settingsStore.edit { it[homeDailyKey] = if (day) "DAY" else "MONTH" }
    }
}
