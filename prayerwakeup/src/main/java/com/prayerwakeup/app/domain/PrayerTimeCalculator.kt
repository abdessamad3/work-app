package com.prayerwakeup.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

data class PrayerTimesResult(
    val date: LocalDate,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime
) {
    fun forPrayer(prayer: Prayer): ZonedDateTime = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}

enum class Prayer(val arabicName: String) {
    FAJR("الفجر"),
    DHUHR("الظهر"),
    ASR("العصر"),
    MAGHRIB("المغرب"),
    ISHA("العشاء")
}

/**
 * Fully offline prayer-time calculator. Pure astronomical computation (sun
 * declination + equation of time), no network access required. Ported from the
 * widely used praytimes.org reference algorithm.
 */
class PrayerTimeCalculator @Inject constructor() {

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        method: CalculationMethod,
        madhab: Madhab,
        elevationMeters: Double = 0.0
    ): PrayerTimesResult {
        val timeZoneHours = zoneId.rules
            .getOffset(date.atStartOfDay(zoneId).toInstant())
            .totalSeconds / 3600.0

        val julianDateBase = PrayerMath.julianDay(date.year, date.monthValue, date.dayOfMonth)
        // Baking longitude into the Julian date is the praytimes.org trick that lets
        // the (otherwise Greenwich-relative) solar position approximate local time.
        val julianDate = julianDateBase - longitude / (15.0 * 24.0)

        fun sunPosition(jd: Double): Pair<Double, Double> {
            val d = jd - 2451545.0
            val g = PrayerMath.fixAngle(357.529 + 0.98560028 * d)
            val q = PrayerMath.fixAngle(280.459 + 0.98564736 * d)
            val l = PrayerMath.fixAngle(q + 1.915 * PrayerMath.dsin(g) + 0.020 * PrayerMath.dsin(2 * g))
            val e = 23.439 - 0.00000036 * d
            val declination = PrayerMath.darcsin(PrayerMath.dsin(e) * PrayerMath.dsin(l))
            val rightAscension = PrayerMath.fixHour(PrayerMath.darctan2(PrayerMath.dcos(e) * PrayerMath.dsin(l), PrayerMath.dcos(l)) / 15.0)
            val equationOfTime = q / 15.0 - rightAscension
            return declination to equationOfTime
        }

        fun midDay(timeFraction: Double): Double {
            val eqt = sunPosition(julianDate + timeFraction).second
            return PrayerMath.fixHour(12.0 - eqt)
        }

        fun sunAngleTime(angle: Double, timeFraction: Double, counterClockwise: Boolean): Double {
            val declination = sunPosition(julianDate + timeFraction).first
            val noon = midDay(timeFraction)
            val cosArg = (-PrayerMath.dsin(angle) - PrayerMath.dsin(declination) * PrayerMath.dsin(latitude)) /
                (PrayerMath.dcos(declination) * PrayerMath.dcos(latitude))
            val t = (1.0 / 15.0) * PrayerMath.darccos(cosArg)
            return noon + if (counterClockwise) -t else t
        }

        fun asrTime(shadowFactor: Double, timeFraction: Double): Double {
            val declination = sunPosition(julianDate + timeFraction).first
            val angle = -PrayerMath.darccot(shadowFactor + PrayerMath.dtan(abs(latitude - declination)))
            return sunAngleTime(angle, timeFraction, counterClockwise = false)
        }

        val riseSetAngle = 0.833 + 0.0347 * sqrt(elevationMeters.coerceAtLeast(0.0))

        var fajrRaw = sunAngleTime(method.fajrAngle, 5.0 / 24.0, counterClockwise = true)
        var sunriseRaw = sunAngleTime(riseSetAngle, 6.0 / 24.0, counterClockwise = true)
        val dhuhrRaw = midDay(12.0 / 24.0)
        val asrRaw = asrTime(madhab.asrShadowFactor, 13.0 / 24.0)
        val sunsetRaw = sunAngleTime(riseSetAngle, 18.0 / 24.0, counterClockwise = false)
        var ishaRaw = method.ishaAngle?.let { sunAngleTime(it, 18.0 / 24.0, counterClockwise = false) } ?: Double.NaN

        val tzAdjust = timeZoneHours - longitude / 15.0
        fajrRaw += tzAdjust
        sunriseRaw += tzAdjust
        val dhuhr = dhuhrRaw + tzAdjust
        val asr = asrRaw + tzAdjust
        val maghrib = sunsetRaw + tzAdjust
        if (!ishaRaw.isNaN()) ishaRaw += tzAdjust

        // Extreme-latitude fallback (sun never reaches the twilight angle): approximate
        // Fajr/Isha as a fixed offset from Sunrise/Maghrib instead of leaving NaN.
        if (fajrRaw.isNaN()) fajrRaw = sunriseRaw - 1.5
        val isha = if (ishaRaw.isNaN()) {
            method.ishaMinutesAfterMaghrib?.let { maghrib + it / 60.0 } ?: (maghrib + 1.5)
        } else {
            ishaRaw
        }

        return PrayerTimesResult(
            date = date,
            fajr = toZonedDateTime(date, fajrRaw, zoneId),
            sunrise = toZonedDateTime(date, sunriseRaw, zoneId),
            dhuhr = toZonedDateTime(date, dhuhr, zoneId),
            asr = toZonedDateTime(date, asr, zoneId),
            maghrib = toZonedDateTime(date, maghrib, zoneId),
            isha = toZonedDateTime(date, isha, zoneId)
        )
    }

    private fun toZonedDateTime(date: LocalDate, hours: Double, zoneId: ZoneId): ZonedDateTime {
        val fixed = PrayerMath.fixHour(hours)
        val totalSeconds = Math.round(fixed * 3600.0)
        return date.atStartOfDay(zoneId).plusSeconds(totalSeconds)
    }
}
