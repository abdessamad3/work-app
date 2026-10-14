package com.prayerwakeup.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.data.tracking.PrayerLogEntry
import com.prayerwakeup.app.data.tracking.PrayerLogRepository
import com.prayerwakeup.app.domain.Prayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class DayStat(val date: LocalDate, val prayedCount: Int, val totalCount: Int, val prayedPrayers: Set<Prayer>)

data class StatisticsUiState(
    val streakDays: Int = 0,
    val todayPrayed: Int = 0,
    val todayTotal: Int = 0,
    val lastSevenDays: List<DayStat> = emptyList(),
    val enabledPrayers: Set<Prayer> = emptySet()
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    prayerLogRepository: PrayerLogRepository
) : ViewModel() {

    val uiState: StateFlow<StatisticsUiState> = combine(
        settingsRepository.settingsFlow,
        prayerLogRepository.entriesFlow
    ) { settings, entries -> compute(settings.enabledPrayers, entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    private fun compute(enabledPrayers: Set<Prayer>, entries: List<PrayerLogEntry>): StatisticsUiState {
        val today = LocalDate.now()
        val byDate = entries.groupBy { it.date }

        fun dayStat(date: LocalDate): DayStat {
            val prayedSet = byDate[date].orEmpty().filter { it.prayed }.map { it.prayer }.toSet()
            val prayedEnabledCount = prayedSet.count { enabledPrayers.contains(it) }
            return DayStat(date, prayedEnabledCount, enabledPrayers.size, prayedSet)
        }

        val todayStat = dayStat(today)

        // Only counts full past days — today is still in progress, so it never breaks or
        // extends the streak until it's over. Bounded to the log's own retention window so a
        // gap in the data (or a future change to that window) can never turn this into an
        // unbounded loop.
        var streak = 0
        var cursor = today.minusDays(1)
        val oldestPossible = today.minusDays(90)
        while (enabledPrayers.isNotEmpty() && cursor >= oldestPossible && dayStat(cursor).prayedCount >= enabledPrayers.size) {
            streak++
            cursor = cursor.minusDays(1)
        }

        val lastSeven = (0..6).map { offset -> dayStat(today.minusDays(offset.toLong())) }

        return StatisticsUiState(
            streakDays = streak,
            todayPrayed = todayStat.prayedCount,
            todayTotal = enabledPrayers.size,
            lastSevenDays = lastSeven,
            enabledPrayers = enabledPrayers
        )
    }
}
