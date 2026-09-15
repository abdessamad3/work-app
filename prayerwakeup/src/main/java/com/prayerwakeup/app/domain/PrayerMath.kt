package com.prayerwakeup.app.domain

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Trigonometric helpers operating in degrees, matching the notation used by the
 * classic praytimes.org solar-position algorithm this calculator is ported from.
 */
internal object PrayerMath {
    private const val DR = PI / 180.0

    fun dsin(deg: Double) = sin(deg * DR)
    fun dcos(deg: Double) = cos(deg * DR)
    fun dtan(deg: Double) = tan(deg * DR)
    fun darcsin(x: Double) = asin(x.coerceIn(-1.0, 1.0)) / DR
    fun darccos(x: Double) = acos(x.coerceIn(-1.0, 1.0)) / DR
    fun darctan2(y: Double, x: Double) = atan2(y, x) / DR
    fun darccot(x: Double) = atan(1.0 / x) / DR

    /** Wraps a value into [0, 360). */
    fun fixAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) a += 360.0
        return a
    }

    /** Wraps a value into [0, 24). */
    fun fixHour(hour: Double): Double {
        var h = hour % 24.0
        if (h < 0) h += 24.0
        return h
    }

    /** Standard Julian Day Number for a Gregorian calendar date at 0h UTC. */
    fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = Math.floor(y / 100.0)
        val b = 2 - a + Math.floor(a / 4.0)
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5
    }
}
