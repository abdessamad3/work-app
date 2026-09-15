package com.prayerwakeup.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prayerwakeup.app.alarm.AlarmScheduler
import com.prayerwakeup.app.data.location.LocationProvider
import com.prayerwakeup.app.data.settings.PrayerSettings
import com.prayerwakeup.app.data.settings.SecureKeyStore
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.CalculationMethod
import com.prayerwakeup.app.domain.CallerPersona
import com.prayerwakeup.app.domain.Madhab
import com.prayerwakeup.app.domain.Prayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: PrayerSettings = PrayerSettings(),
    val apiKey: String = "",
    val canScheduleExactAlarms: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
    private val locationProvider: LocationProvider,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        secureKeyStore.apiKey
    ) { settings, apiKey ->
        SettingsUiState(
            settings = settings,
            apiKey = apiKey,
            canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private var locating = false

    fun detectLocation(onResult: (success: Boolean) -> Unit) {
        if (locating) return
        locating = true
        viewModelScope.launch {
            val location = locationProvider.getCurrentLocation()
            locating = false
            if (location != null) {
                settingsRepository.updateLocation(
                    location.latitude, location.longitude, locationProvider.deviceTimeZoneId(), "الموقع الحالي (GPS)"
                )
                alarmScheduler.rescheduleAll()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun setManualLocation(latitude: Double, longitude: Double, timeZoneId: String, label: String) {
        viewModelScope.launch {
            settingsRepository.updateLocation(latitude, longitude, timeZoneId, label)
            alarmScheduler.rescheduleAll()
        }
    }

    fun setCalculationMethod(method: CalculationMethod) {
        viewModelScope.launch {
            settingsRepository.updateCalculationMethod(method)
            alarmScheduler.rescheduleAll()
        }
    }

    fun setMadhab(madhab: Madhab) {
        viewModelScope.launch {
            settingsRepository.updateMadhab(madhab)
            alarmScheduler.rescheduleAll()
        }
    }

    fun togglePrayer(prayer: Prayer, currentSet: Set<Prayer>) {
        val updated = if (currentSet.contains(prayer)) currentSet - prayer else currentSet + prayer
        viewModelScope.launch {
            settingsRepository.updateEnabledPrayers(updated)
            alarmScheduler.rescheduleAll()
        }
    }

    fun setSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateSnoozeMinutes(minutes) }
    }

    fun setMaxCallMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateMaxCallMinutes(minutes) }
    }

    fun setPersona(persona: CallerPersona) {
        viewModelScope.launch { settingsRepository.updatePersona(persona) }
    }

    fun saveApiKey(key: String) {
        secureKeyStore.setApiKey(key.trim())
    }
}
