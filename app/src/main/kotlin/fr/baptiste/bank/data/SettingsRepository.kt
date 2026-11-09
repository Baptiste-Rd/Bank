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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


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

    private val salaryKeyKey = stringPreferencesKey("salary_key")
    private val salaryNextMonthKey = androidx.datastore.preferences.core.booleanPreferencesKey("salary_next_month")

    /** Salaire confirmé : la clé de ses versements, et s'il finance le mois suivant (null : pas confirmé). */
    val salary: Flow<Pair<String, Boolean>?> = settingsStore.data.map { prefs ->
        prefs[salaryKeyKey]?.let { it to (prefs[salaryNextMonthKey] ?: true) }
    }

    /** Confirme le salaire lu à la banque ; le montant tapé à la main n'a plus lieu d'être. */
    suspend fun setSalary(key: String?, nextMonth: Boolean) {
        settingsStore.edit {
            if (key == null) {
                it.remove(salaryKeyKey)
                it.remove(salaryNextMonthKey)
            } else {
                it[salaryKeyKey] = key
                it[salaryNextMonthKey] = nextMonth
                it.remove(monthlyIncomeKey)
            }
        }
    }

    private val exportSaltKey = stringPreferencesKey("export_salt")

    /** Sel des identifiants opaques de l'export Claude : tiré au hasard une fois, gardé sur le téléphone. */
    suspend fun exportSalt(): String {
        settingsStore.data.first()[exportSaltKey]?.let { return it }
        val salt = java.util.UUID.randomUUID().toString()
        settingsStore.edit { it[exportSaltKey] = salt }
        return salt
    }

    private val glassTuningKey = stringPreferencesKey("glass_tuning_v2")

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

    /** L'objectif de précaution d'avant les objectifs d'épargne, une seule fois : les anciens réglages sont effacés. */
    suspend fun takeLegacyPrecaution(): Money? {
        var found: Money? = null
        settingsStore.edit {
            found = it[precautionGoalKey]?.let(::Money)
            it.remove(precautionGoalKey)
            it.remove(totalGoalKey)
            it.remove(totalGoalByKey)
        }
        return found
    }

    /** L'ancien réglage, gardé pour vérifier sa reprise (voir takeLegacyPrecaution). */
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

    private val recapDismissedKey = stringPreferencesKey("recap_dismissed")

    /** Dernier mois dont le rappel du bilan a été fermé sur le Résumé (« 2026-10 »). */
    val recapDismissed: Flow<java.time.YearMonth?> = settingsStore.data.map { prefs ->
        prefs[recapDismissedKey]?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() }
    }

    suspend fun setRecapDismissed(month: java.time.YearMonth) {
        settingsStore.edit { it[recapDismissedKey] = month.toString() }
    }

    private val monthCountsKey = stringPreferencesKey("month_counts")

    /** Option « compter dans les moyennes » choisie mois par mois (« 2026-07=0;2026-09=1 »). */
    val monthCounts: Flow<Map<java.time.YearMonth, Boolean>> = settingsStore.data.map { prefs -> parseMonthCounts(prefs[monthCountsKey]) }

    suspend fun setMonthCounted(month: java.time.YearMonth, counted: Boolean) {
        settingsStore.edit { prefs ->
            val choices = parseMonthCounts(prefs[monthCountsKey]) + (month to counted)
            prefs[monthCountsKey] = choices.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${if (it.value) 1 else 0}" }
        }
    }

    /** Remet le choix d'un mois tel qu'il était : [counted] null retire le choix (règle par défaut). */
    suspend fun restoreMonthCount(month: java.time.YearMonth, counted: Boolean?) {
        settingsStore.edit { prefs ->
            val choices = parseMonthCounts(prefs[monthCountsKey]).let { if (counted == null) it - month else it + (month to counted) }
            prefs[monthCountsKey] = choices.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${if (it.value) 1 else 0}" }
        }
    }

    private fun parseMonthCounts(encoded: String?): Map<java.time.YearMonth, Boolean> =
        encoded.orEmpty().split(";").mapNotNull { part ->
            val (month, value) = part.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            runCatching { java.time.YearMonth.parse(month) }.getOrNull()?.let { it to (value == "1") }
        }.toMap()
}
