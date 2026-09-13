package com.coffer.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.settings.CurrencyPreferences
import com.coffer.app.domain.Currency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val currencyPreferences: CurrencyPreferences
) : ViewModel() {

    val currency: StateFlow<Currency> = currencyPreferences.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Currency.DEFAULT)

    fun setCurrency(currency: Currency) {
        viewModelScope.launch { currencyPreferences.setCurrency(currency) }
    }
}
