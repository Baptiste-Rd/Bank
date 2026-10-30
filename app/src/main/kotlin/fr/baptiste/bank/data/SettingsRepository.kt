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

    private val precautionGoalKey = longPreferencesKey("goal_precaution_cents")
    private val totalGoalKey = longPreferencesKey("goal_total_cents")
    private val totalGoalByKey = stringPreferencesKey("goal_total_by")

    /** Échéance de l'objectif total (« 2026-12 »), ou null. */
    val savingsGoalBy: Flow<java.time.YearMonth?> = settingsStore.data.map { prefs ->
        prefs[totalGoalByKey]?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() }
    }

    /** Objectifs d'épargne : précaution (livrets) puis total (tous comptes). 10 000 € et 20 000 € par défaut. */
    val savingsGoals: Flow<Pair<Money, Money>> = settingsStore.data.map {
        Money(it[precautionGoalKey] ?: 1_000_000L) to Money(it[totalGoalKey] ?: 2_000_000L)
    }

    suspend fun setSavingsGoals(precaution: Money, total: Money, totalBy: java.time.YearMonth?) {
        settingsStore.edit {
            it[precautionGoalKey] = precaution.cents
            it[totalGoalKey] = total.cents
            if (totalBy == null) it.remove(totalGoalByKey) else it[totalGoalByKey] = totalBy.toString()
        }
    }

    /** Avis de Claude sur un mois, collé par l'utilisateur et gardé avec le mois. */
    fun monthReview(month: java.time.YearMonth): Flow<String?> =
        settingsStore.data.map { it[stringPreferencesKey("review_$month")] }

    suspend fun setMonthReview(month: java.time.YearMonth, text: String?) {
        val key = stringPreferencesKey("review_$month")
        settingsStore.edit { if (text.isNullOrBlank()) it.remove(key) else it[key] = text.trim() }
    }

    private val trackingStartKey = stringPreferencesKey("tracking_start")

    /** Début du suivi choisi par l'utilisateur (« 2026-09 »), ou null : le premier mois avec des opérations. */
    val trackingStart: Flow<java.time.YearMonth?> = settingsStore.data.map { prefs ->
        prefs[trackingStartKey]?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() }
    }

    suspend fun setTrackingStart(month: java.time.YearMonth?) {
        settingsStore.edit { if (month == null) it.remove(trackingStartKey) else it[trackingStartKey] = month.toString() }
    }
}
