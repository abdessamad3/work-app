package com.coffer.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.coffer.app.domain.Currency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class CurrencyPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val currencyCodeKey = stringPreferencesKey("currency_code")

    val currency = context.settingsDataStore.data.map { Currency.fromCode(it[currencyCodeKey]) }

    suspend fun setCurrency(currency: Currency) {
        context.settingsDataStore.edit { it[currencyCodeKey] = currency.code }
    }
}
