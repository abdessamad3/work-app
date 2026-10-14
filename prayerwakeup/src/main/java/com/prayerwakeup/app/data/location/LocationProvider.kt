package com.prayerwakeup.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Resolves a device fix using Android's plain LocationManager (GPS + network
 * providers) rather than Google Play Services' FusedLocationProvider, so a GPS
 * fix works with no data connection once the almanac/ephemeris is cached.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMillis: Long = 20_000L): Location? {
        val lm = manager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        // Prefer a recent last-known fix so the settings screen doesn't stall.
        val lastKnown = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_FIX_MAX_AGE_MS) {
            return lastKnown
        }

        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (resumed) return
                    resumed = true
                    providers.forEach { runCatching { lm.removeUpdates(this) } }
                    continuation.resume(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }

            providers.forEach { provider ->
                runCatching { lm.requestLocationUpdates(provider, 1000L, 0f, listener) }
            }

            continuation.invokeOnCancellation {
                runCatching { lm.removeUpdates(listener) }
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!resumed) {
                    resumed = true
                    runCatching { lm.removeUpdates(listener) }
                    if (continuation.isActive) continuation.resume(lastKnown)
                }
            }, timeoutMillis)
        }
    }

    fun deviceTimeZoneId(): String = ZoneId.systemDefault().id

    private companion object {
        const val FRESH_FIX_MAX_AGE_MS = 10 * 60 * 1000L
    }
}
