package com.prayerwakeup.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prayerwakeup.app.alarm.AlarmScheduler
import com.prayerwakeup.app.call.CallForegroundService
import com.prayerwakeup.app.data.settings.PrayerSettings
import com.prayerwakeup.app.data.settings.SecureKeyStore
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.PrayerTimeCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val hasLocation: Boolean = false,
    val locationLabel: String = "",
    val hasApiKey: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val nextPrayer: Prayer? = null,
    val nextPrayerTime: ZonedDateTime? = null,
    val todayTimes: List<Pair<Prayer, ZonedDateTime>> = emptyList(),
    val enabledPrayers: Set<Prayer> = emptySet()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val calculator: PrayerTimeCalculator,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settingsRepository.settingsFlow, secureKeyStore.apiKey) { settings, apiKey -> settings to apiKey }
                .collect { (settings, apiKey) -> applySettings(settings, apiKey.isNotBlank()) }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            applySettings(settings, secureKeyStore.hasApiKey())
        }
    }

    private fun applySettings(settings: PrayerSettings, hasApiKey: Boolean) {
        if (!settings.hasLocation) {
            _uiState.value = HomeUiState(
                loading = false,
                hasLocation = false,
                hasApiKey = hasApiKey,
                canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms(),
                enabledPrayers = settings.enabledPrayers
            )
            return
        }
        val zoneId = runCatching { ZoneId.of(settings.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zoneId)
        val times = calculator.calculate(
            now.toLocalDate(), settings.latitude, settings.longitude, zoneId, settings.calculationMethod, settings.madhab
        )
        val ordered = listOf(
            Prayer.FAJR to times.fajr,
            Prayer.DHUHR to times.dhuhr,
            Prayer.ASR to times.asr,
            Prayer.MAGHRIB to times.maghrib,
            Prayer.ISHA to times.isha
        )
        val next = ordered.firstOrNull { it.second.isAfter(now) }
        _uiState.value = HomeUiState(
            loading = false,
            hasLocation = true,
            locationLabel = settings.locationLabel,
            hasApiKey = hasApiKey,
            canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms(),
            nextPrayer = next?.first,
            nextPrayerTime = next?.second,
            todayTimes = ordered,
            enabledPrayers = settings.enabledPrayers
        )
    }

    fun rescheduleAlarms() {
        viewModelScope.launch { alarmScheduler.rescheduleAll() }
    }

    fun testCallNow(prayer: Prayer) {
        val intent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_START_CALL
            putExtra(CallForegroundService.EXTRA_PRAYER, prayer.name)
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
