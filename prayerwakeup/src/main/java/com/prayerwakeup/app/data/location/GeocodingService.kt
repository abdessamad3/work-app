package com.prayerwakeup.app.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class GeoPlace(val latitude: Double, val longitude: Double, val label: String)

/**
 * Turns a typed place name into coordinates, and coordinates into a human-readable place name,
 * using Android's own on-device Geocoder — no extra API key or HTTP client, since the OS already
 * ships this (backed by Google's geocoder on devices with Play Services, or an on-device
 * database on some others). Some devices/ROMs have no working geocoder backend at all, so every
 * call here is wrapped and surfaces a clear Arabic error rather than crashing.
 */
@Singleton
class GeocodingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val geocoder: Geocoder? by lazy {
        if (Geocoder.isPresent()) Geocoder(context, Locale("ar")) else null
    }

    suspend fun searchPlaces(query: String, maxResults: Int = 8): Result<List<GeoPlace>> = withContext(Dispatchers.IO) {
        runCatching {
            val g = geocoder ?: throw IOException("البحث عن الأماكن غير مدعوم على هذا الجهاز")
            val trimmed = query.trim()
            if (trimmed.isBlank()) throw IllegalStateException("أدخل اسم المدينة أو المكان للبحث")

            @Suppress("DEPRECATION")
            val results = g.getFromLocationName(trimmed, maxResults) ?: emptyList()
            if (results.isEmpty()) throw IOException("لم يتم العثور على أي نتائج، جرّب اسماً آخر")
            results.map { it.toGeoPlace() }
        }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeoPlace? = withContext(Dispatchers.IO) {
        runCatching {
            val g = geocoder ?: return@runCatching null
            @Suppress("DEPRECATION")
            val results = g.getFromLocation(latitude, longitude, 1)
            results?.firstOrNull()?.toGeoPlace()
        }.getOrNull()
    }

    private fun Address.toGeoPlace(): GeoPlace {
        val parts = listOfNotNull(
            locality?.takeIf { it.isNotBlank() } ?: subAdminArea?.takeIf { it.isNotBlank() },
            countryName?.takeIf { it.isNotBlank() }
        ).distinct()
        val label = parts.ifEmpty { listOfNotNull(featureName) }.joinToString("، ").ifBlank { "%.4f, %.4f".format(latitude, longitude) }
        return GeoPlace(latitude, longitude, label)
    }
}
