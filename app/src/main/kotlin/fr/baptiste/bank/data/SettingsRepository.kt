package fr.baptiste.bank.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.baptiste.bank.domain.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

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
}
