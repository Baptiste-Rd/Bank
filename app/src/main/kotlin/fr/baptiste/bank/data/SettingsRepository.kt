package fr.baptiste.bank.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Style de l'interface, réglable depuis le menu ⋮. */
enum class UiStyle { GLASS, CLASSIC }

/**
 * Réglages simples (clé / valeur) stockés avec DataStore.
 * Ils n'ont pas leur place dans la base relationnelle : une seule valeur, pas de relation.
 */
class SettingsRepository(private val context: Context) {

    private val monthlyIncomeKey = longPreferencesKey("monthly_income_cents")

    /** Salaire mensuel net, ou null tant qu'il n'a pas été renseigné. */
    val monthlyIncome: Flow<Money?> = context.settingsStore.data.map { preferences ->
        preferences[monthlyIncomeKey]?.let(::Money)
    }

    suspend fun setMonthlyIncome(income: Money) {
        context.settingsStore.edit { it[monthlyIncomeKey] = income.cents }
    }

    private val uiStyleKey = stringPreferencesKey("ui_style")

    /** Liquid Glass par défaut ; le style classique reste disponible depuis le menu. */
    val uiStyle: Flow<UiStyle> = context.settingsStore.data.map { preferences ->
        preferences[uiStyleKey]?.let { saved -> UiStyle.entries.firstOrNull { it.name == saved } } ?: UiStyle.GLASS
    }

    suspend fun setUiStyle(style: UiStyle) {
        context.settingsStore.edit { it[uiStyleKey] = style.name }
    }
}
