package com.prayerwakeup.app.ui.home

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prayerwakeup.app.alarm.AlarmScheduler
import com.prayerwakeup.app.call.CallForegroundService
import com.prayerwakeup.app.data.PrayerTimesResolver
import com.prayerwakeup.app.data.settings.PrayerSettings
import com.prayerwakeup.app.data.settings.SchedulingStatus
import com.prayerwakeup.app.data.settings.SchedulingStatusStore
import com.prayerwakeup.app.data.settings.SecureKeyStore
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.data.tracking.PrayerLogEntry
import com.prayerwakeup.app.data.tracking.PrayerLogRepository
import com.prayerwakeup.app.domain.DailyAthkar
import com.prayerwakeup.app.domain.HijriDate
import com.prayerwakeup.app.domain.Prayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val hasLocation: Boolean = false,
    val locationLabel: String = "",
    val sourceLabel: String = "",
    val hasApiKey: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val batteryOptimizationExempt: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val schedulingStatus: SchedulingStatus = SchedulingStatus(),
    val nextPrayer: Prayer? = null,
    val nextPrayerTime: ZonedDateTime? = null,
    val nextPrayerIsTomorrow: Boolean = false,
    val todayTimes: List<Pair<Prayer, ZonedDateTime>> = emptyList(),
    val enabledPrayers: Set<Prayer> = emptySet(),
    val prayedToday: Set<Prayer> = emptySet(),
    val hijriLabel: String = "",
    val isRamadan: Boolean = false,
    val dailyAthkar: String = ""
) {
    val allDiagnosticsOk: Boolean
        get() = canScheduleExactAlarms && batteryOptimizationExempt && notificationsEnabled
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val timesResolver: PrayerTimesResolver,
    private val secureKeyStore: SecureKeyStore,
    private val schedulingStatusStore: SchedulingStatusStore,
    private val prayerLogRepository: PrayerLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settingsFlow,
                secureKeyStore.geminiApiKey,
                schedulingStatusStore.statusFlow,
                prayerLogRepository.entriesFlow
            ) { settings, apiKey, status, entries -> Quadruple(settings, apiKey, status, entries) }
                .collect { (settings, apiKey, status, entries) ->
                    applySettings(settings, apiKey.isNotBlank(), status, prayedTodaySet(entries))
                }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val status = schedulingStatusStore.statusFlow.first()
            val entries = prayerLogRepository.entriesFlow.first()
            applySettings(settings, secureKeyStore.hasGeminiApiKey(), status, prayedTodaySet(entries))
        }
    }

    private fun prayedTodaySet(entries: List<PrayerLogEntry>): Set<Prayer> {
        val today = LocalDate.now()
        return entries.filter { it.date == today && it.prayed }.map { it.prayer }.toSet()
    }

    fun togglePrayed(prayer: Prayer, currentlyPrayed: Boolean) {
        viewModelScope.launch {
            prayerLogRepository.setPrayed(LocalDate.now(), prayer, !currentlyPrayed)
        }
    }

    private fun isBatteryOptimizationExempt(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun areNotificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return manager.areNotificationsEnabled()
    }

    private suspend fun applySettings(settings: PrayerSettings, hasApiKey: Boolean, status: SchedulingStatus, prayedToday: Set<Prayer>) {
        val today = LocalDate.now()
        val hijri = HijriDate.forDate(today)
        val athkar = DailyAthkar.forDate(today)

        if (!settings.hasLocation) {
            _uiState.value = HomeUiState(
                loading = false,
                hasLocation = false,
                hasApiKey = hasApiKey,
                canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms(),
                batteryOptimizationExempt = isBatteryOptimizationExempt(),
                notificationsEnabled = areNotificationsEnabled(),
                schedulingStatus = status,
                enabledPrayers = settings.enabledPrayers,
                prayedToday = prayedToday,
                hijriLabel = hijri.displayLabel,
                isRamadan = hijri.isRamadan,
                dailyAthkar = athkar
            )
            return
        }
        val zoneId = runCatching { ZoneId.of(settings.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zoneId)
        val times = timesResolver.resolveForDate(settings, now.toLocalDate(), zoneId)
        val ordered = listOf(
            Prayer.FAJR to times.fajr,
            Prayer.DHUHR to times.dhuhr,
            Prayer.ASR to times.asr,
            Prayer.MAGHRIB to times.maghrib,
            Prayer.ISHA to times.isha
        )
        // Once today's Isha has passed, nothing in today's list is still ahead — without this
        // fallback the "next prayer" card would just vanish for the rest of the evening instead
        // of pointing at tomorrow's Fajr.
        val next = ordered.firstOrNull { it.second.isAfter(now) }
            ?: (Prayer.FAJR to timesResolver.resolveForDate(settings, now.toLocalDate().plusDays(1), zoneId).fajr)
        val nextIsTomorrow = ordered.none { it.first == next.first && it.second == next.second }
        _uiState.value = HomeUiState(
            loading = false,
            hasLocation = true,
            locationLabel = settings.locationLabel,
            sourceLabel = settings.prayerTimeSource.displayName,
            hasApiKey = hasApiKey,
            canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms(),
            batteryOptimizationExempt = isBatteryOptimizationExempt(),
            notificationsEnabled = areNotificationsEnabled(),
            schedulingStatus = status,
            nextPrayer = next.first,
            nextPrayerTime = next.second,
            nextPrayerIsTomorrow = nextIsTomorrow,
            todayTimes = ordered,
            enabledPrayers = settings.enabledPrayers,
            prayedToday = prayedToday,
            hijriLabel = hijri.displayLabel,
            isRamadan = hijri.isRamadan,
            dailyAthkar = athkar
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
