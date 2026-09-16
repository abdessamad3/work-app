package com.prayerwakeup.app.data

import com.prayerwakeup.app.data.remote.MoroccoHabousClient
import com.prayerwakeup.app.data.settings.PrayerSettings
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.PrayerTimeCalculator
import com.prayerwakeup.app.domain.PrayerTimeSource
import com.prayerwakeup.app.domain.PrayerTimesResult
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "what time is prayer X on date Y", shared by the alarm scheduler
 * and the home screen's display so they can never disagree. Always computes the offline
 * astronomical result first (fast, no network, cannot fail), then — only when the Moroccan
 * Habous source is selected, a city is configured, and the date is today (the community API
 * only exposes "today") — tries to overlay real fetched values on top of it, field by field.
 * Any network failure, timeout, or missing field just leaves the offline value in place.
 */
@Singleton
class PrayerTimesResolver @Inject constructor(
    private val calculator: PrayerTimeCalculator,
    private val moroccoHabousClient: MoroccoHabousClient
) {
    suspend fun resolveForDate(settings: PrayerSettings, date: LocalDate, zoneId: ZoneId): PrayerTimesResult {
        val offline = calculator.calculate(
            date, settings.latitude, settings.longitude, zoneId, settings.calculationMethod, settings.madhab
        )
        if (settings.prayerTimeSource != PrayerTimeSource.MOROCCO_HABOUS) return offline
        val cityId = settings.moroccoCityId ?: return offline
        if (date != LocalDate.now(zoneId)) return offline

        val remote = moroccoHabousClient.fetchTodayTimes(cityId, date, zoneId).getOrNull() ?: return offline
        return offline.copy(
            fajr = remote[Prayer.FAJR] ?: offline.fajr,
            dhuhr = remote[Prayer.DHUHR] ?: offline.dhuhr,
            asr = remote[Prayer.ASR] ?: offline.asr,
            maghrib = remote[Prayer.MAGHRIB] ?: offline.maghrib,
            isha = remote[Prayer.ISHA] ?: offline.isha
        )
    }
}
