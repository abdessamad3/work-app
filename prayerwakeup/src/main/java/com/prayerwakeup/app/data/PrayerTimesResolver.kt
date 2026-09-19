package com.prayerwakeup.app.data

import com.prayerwakeup.app.data.remote.MawaqitClient
import com.prayerwakeup.app.data.remote.MoroccoHabousClient
import com.prayerwakeup.app.data.settings.PrayerSettings
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.PrayerTimeCalculator
import com.prayerwakeup.app.domain.PrayerTimeSource
import com.prayerwakeup.app.domain.PrayerTimesResult
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "what time is prayer X on date Y", shared by the alarm scheduler
 * and the home screen's display so they can never disagree. Always computes the offline
 * astronomical result first (fast, no network, cannot fail), then — only when an online source
 * is selected and properly configured, and the date is today (every online source here only
 * exposes "today") — tries to overlay real fetched values on top of it, field by field.
 * Any network failure, timeout, or missing field just leaves the offline value in place.
 */
@Singleton
class PrayerTimesResolver @Inject constructor(
    private val calculator: PrayerTimeCalculator,
    private val moroccoHabousClient: MoroccoHabousClient,
    private val mawaqitClient: MawaqitClient
) {
    suspend fun resolveForDate(settings: PrayerSettings, date: LocalDate, zoneId: ZoneId): PrayerTimesResult {
        val offline = calculator.calculate(
            date, settings.latitude, settings.longitude, zoneId, settings.calculationMethod, settings.madhab
        )
        if (date != LocalDate.now(zoneId)) return offline

        return when (settings.prayerTimeSource) {
            PrayerTimeSource.MOROCCO_HABOUS -> resolveMoroccoHabous(settings, offline, date, zoneId)
            PrayerTimeSource.MAWAQIT_MOSQUE -> resolveMawaqit(settings, offline, date, zoneId)
            PrayerTimeSource.OFFLINE_CALCULATION -> offline
        }
    }

    private suspend fun resolveMoroccoHabous(
        settings: PrayerSettings, offline: PrayerTimesResult, date: LocalDate, zoneId: ZoneId
    ): PrayerTimesResult {
        val cityId = settings.moroccoCityId ?: return offline
        val remote = moroccoHabousClient.fetchTodayTimes(cityId, date, zoneId).getOrNull() ?: return offline
        return offline.copy(
            fajr = remote[Prayer.FAJR] ?: offline.fajr,
            dhuhr = remote[Prayer.DHUHR] ?: offline.dhuhr,
            asr = remote[Prayer.ASR] ?: offline.asr,
            maghrib = remote[Prayer.MAGHRIB] ?: offline.maghrib,
            isha = remote[Prayer.ISHA] ?: offline.isha
        )
    }

    private suspend fun resolveMawaqit(
        settings: PrayerSettings, offline: PrayerTimesResult, date: LocalDate, zoneId: ZoneId
    ): PrayerTimesResult {
        if (settings.mawaqitMosqueId.isBlank()) return offline
        val remote = mawaqitClient.fetchTodayTimes(settings.mawaqitMosqueId).getOrNull() ?: return offline

        val formatter = DateTimeFormatter.ofPattern("H:mm")
        fun toZoned(raw: String): ZonedDateTime? = runCatching {
            val time = LocalTime.parse(raw.trim(), formatter)
            date.atTime(time).atZone(zoneId)
        }.getOrNull()

        return offline.copy(
            fajr = toZoned(remote.fajr) ?: offline.fajr,
            dhuhr = toZoned(remote.dhuhr) ?: offline.dhuhr,
            asr = toZoned(remote.asr) ?: offline.asr,
            maghrib = toZoned(remote.maghrib) ?: offline.maghrib,
            isha = toZoned(remote.isha) ?: offline.isha
        )
    }
}
