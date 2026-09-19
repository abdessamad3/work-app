package com.prayerwakeup.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prayerwakeup.app.alarm.AlarmScheduler
import com.prayerwakeup.app.conversation.ElevenLabsClient
import com.prayerwakeup.app.conversation.ElevenLabsVoice
import com.prayerwakeup.app.data.location.LocationProvider
import com.prayerwakeup.app.data.remote.MawaqitClient
import com.prayerwakeup.app.data.remote.MawaqitTimes
import com.prayerwakeup.app.data.remote.MoroccoCity
import com.prayerwakeup.app.data.remote.MoroccoHabousClient
import com.prayerwakeup.app.data.settings.PrayerSettings
import com.prayerwakeup.app.data.settings.SecureKeyStore
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.CalculationMethod
import com.prayerwakeup.app.domain.CallerPersona
import com.prayerwakeup.app.domain.Madhab
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.PrayerTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: PrayerSettings = PrayerSettings(),
    val geminiApiKey: String = "",
    val elevenLabsApiKey: String = "",
    val canScheduleExactAlarms: Boolean = true
)

sealed interface MoroccoCitiesUiState {
    data object Idle : MoroccoCitiesUiState
    data object Loading : MoroccoCitiesUiState
    data class Loaded(val cities: List<MoroccoCity>) : MoroccoCitiesUiState
    data class Error(val message: String) : MoroccoCitiesUiState
}

sealed interface ElevenLabsVoicesUiState {
    data object Idle : ElevenLabsVoicesUiState
    data object Loading : ElevenLabsVoicesUiState
    data class Loaded(val voices: List<ElevenLabsVoice>) : ElevenLabsVoicesUiState
    data class Error(val message: String) : ElevenLabsVoicesUiState
}

sealed interface MawaqitLookupUiState {
    data object Idle : MawaqitLookupUiState
    data object Loading : MawaqitLookupUiState
    data class Loaded(val times: MawaqitTimes) : MawaqitLookupUiState
    data class Error(val message: String) : MawaqitLookupUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
    private val locationProvider: LocationProvider,
    private val alarmScheduler: AlarmScheduler,
    private val moroccoHabousClient: MoroccoHabousClient,
    private val elevenLabsClient: ElevenLabsClient,
    private val mawaqitClient: MawaqitClient
) : ViewModel() {

    private val _moroccoCities = MutableStateFlow<MoroccoCitiesUiState>(MoroccoCitiesUiState.Idle)
    val moroccoCities: StateFlow<MoroccoCitiesUiState> = _moroccoCities.asStateFlow()

    private val _elevenLabsVoices = MutableStateFlow<ElevenLabsVoicesUiState>(ElevenLabsVoicesUiState.Idle)
    val elevenLabsVoices: StateFlow<ElevenLabsVoicesUiState> = _elevenLabsVoices.asStateFlow()

    private val _mawaqitLookup = MutableStateFlow<MawaqitLookupUiState>(MawaqitLookupUiState.Idle)
    val mawaqitLookup: StateFlow<MawaqitLookupUiState> = _mawaqitLookup.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        secureKeyStore.geminiApiKey,
        secureKeyStore.elevenLabsApiKey
    ) { settings, geminiKey, elevenLabsKey ->
        SettingsUiState(
            settings = settings,
            geminiApiKey = geminiKey,
            elevenLabsApiKey = elevenLabsKey,
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

    fun saveGeminiApiKey(key: String) {
        secureKeyStore.setGeminiApiKey(key.trim())
    }

    fun setPrayerTimeSource(source: PrayerTimeSource) {
        viewModelScope.launch {
            settingsRepository.updatePrayerTimeSource(source)
            alarmScheduler.rescheduleAll()
        }
        if (source == PrayerTimeSource.MOROCCO_HABOUS && _moroccoCities.value is MoroccoCitiesUiState.Idle) {
            loadMoroccoCities()
        }
    }

    fun loadMoroccoCities() {
        _moroccoCities.value = MoroccoCitiesUiState.Loading
        viewModelScope.launch {
            moroccoHabousClient.fetchCities()
                .onSuccess { _moroccoCities.value = MoroccoCitiesUiState.Loaded(it) }
                .onFailure { _moroccoCities.value = MoroccoCitiesUiState.Error(it.message ?: "تعذر تحميل قائمة المدن") }
        }
    }

    fun setMoroccoCity(city: MoroccoCity) {
        viewModelScope.launch {
            settingsRepository.updateMoroccoCity(city.id, city.displayLabel)
            alarmScheduler.rescheduleAll()
        }
    }

    fun saveElevenLabsApiKey(key: String) {
        secureKeyStore.setElevenLabsApiKey(key.trim())
    }

    fun loadElevenLabsVoices() {
        _elevenLabsVoices.value = ElevenLabsVoicesUiState.Loading
        viewModelScope.launch {
            elevenLabsClient.fetchVoices()
                .onSuccess { _elevenLabsVoices.value = ElevenLabsVoicesUiState.Loaded(it) }
                .onFailure { _elevenLabsVoices.value = ElevenLabsVoicesUiState.Error(it.message ?: "تعذر تحميل قائمة الأصوات") }
        }
    }

    fun setElevenLabsVoice(voice: ElevenLabsVoice) {
        viewModelScope.launch { settingsRepository.updateElevenLabsVoice(voice.id, voice.name) }
    }

    fun clearElevenLabsVoice() {
        viewModelScope.launch { settingsRepository.updateElevenLabsVoice("", "") }
    }

    // Unlike Morocco Habous (a picked-from-a-list city), a mosque ID is free-typed, so it's
    // verified by fetching it once before being saved — this both confirms the ID is real and
    // shows the user which mosque they're about to select, since the ID alone means nothing to them.
    fun lookupMawaqitMosque(mosqueId: String) {
        val id = mosqueId.trim()
        if (id.isBlank()) return
        _mawaqitLookup.value = MawaqitLookupUiState.Loading
        viewModelScope.launch {
            mawaqitClient.fetchTodayTimes(id)
                .onSuccess { _mawaqitLookup.value = MawaqitLookupUiState.Loaded(it) }
                .onFailure { _mawaqitLookup.value = MawaqitLookupUiState.Error(it.message ?: "تعذر العثور على المسجد") }
        }
    }

    fun confirmMawaqitMosque(mosqueId: String, times: MawaqitTimes) {
        viewModelScope.launch {
            settingsRepository.updateMawaqitMosque(mosqueId.trim(), times.mosqueName)
            alarmScheduler.rescheduleAll()
        }
    }

    fun resetMawaqitLookup() {
        _mawaqitLookup.value = MawaqitLookupUiState.Idle
    }
}
