package com.prayerwakeup.app.data.remote

import com.prayerwakeup.app.domain.Prayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class MoroccoCity(val id: Int, val nameAr: String, val nameFr: String) {
    val displayLabel: String get() = if (nameAr.isNotBlank()) nameAr else nameFr
}

/**
 * Client for a community-run wrapper around Morocco's Ministry of Habous prayer-time
 * schedule (habous.gov.ma). This is an unofficial, unmaintained (archived) third-party
 * service with no uptime guarantee, so every call here is used behind a fallback to the
 * offline astronomical calculator — a failure here must never be allowed to break alarm
 * scheduling, only make it slightly less "officially Moroccan" for that one day.
 */
@Singleton
class MoroccoHabousClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Two known (undocumented/unstable) route shapes exist for this service across its history;
    // try each in turn and only give up once every candidate has failed to both respond AND parse,
    // so a URL that answers with an unexpected shape doesn't stop a working fallback from trying.
    suspend fun fetchCities(): Result<List<MoroccoCity>> = withContext(Dispatchers.IO) {
        fetchFirstWorking(
            urls = listOf("$BASE_URL/api/v1/available-cities", "$BASE_URL/cities"),
            fallbackError = "لا يمكن الوصول إلى خدمة المدن",
            parse = ::parseCities
        )
    }

    suspend fun fetchTodayTimes(cityId: Int, date: LocalDate, zoneId: ZoneId): Result<Map<Prayer, ZonedDateTime>> =
        withContext(Dispatchers.IO) {
            fetchFirstWorking(
                urls = listOf("$BASE_URL/api/v1/prayer-times?cityId=$cityId", "$BASE_URL/$cityId/today"),
                fallbackError = "لا يمكن الوصول إلى خدمة مواقيت الصلاة",
                parse = { body -> parseTimes(body, date, zoneId) }
            )
        }

    private fun <T> fetchFirstWorking(urls: List<String>, fallbackError: String, parse: (String) -> T): Result<T> {
        var lastError: Throwable = IOException(fallbackError)
        for (url in urls) {
            val body = get(url) ?: continue
            val result = runCatching { parse(body) }
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull() ?: lastError
        }
        return Result.failure(lastError)
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    private fun parseCities(body: String): List<MoroccoCity> {
        val json = JSONObject(body)
        val cities = mutableListOf<MoroccoCity>()
        json.keys().forEach { key ->
            val id = key.toIntOrNull()
            val obj = json.optJSONObject(key)
            if (id != null && obj != null) {
                cities.add(
                    MoroccoCity(
                        id = id,
                        nameAr = obj.optString("nameAR", obj.optString("name_ar", "")),
                        nameFr = obj.optString("nameFR", obj.optString("name_fr", ""))
                    )
                )
            }
        }
        if (cities.isEmpty()) throw IOException("قائمة المدن فارغة")
        return cities.sortedBy { it.displayLabel }
    }

    private fun parseTimes(body: String, date: LocalDate, zoneId: ZoneId): Map<Prayer, ZonedDateTime> {
        val json = JSONObject(body)
        fun field(vararg keys: String): String {
            for (k in keys) {
                val v = json.optString(k, "")
                if (v.isNotBlank()) return v
            }
            throw IOException("حقل مفقود في استجابة الخدمة: ${keys.first()}")
        }

        val formatter = DateTimeFormatter.ofPattern("H:mm")
        fun toZoned(raw: String): ZonedDateTime {
            val time = LocalTime.parse(raw.trim(), formatter)
            return date.atTime(time).atZone(zoneId)
        }

        return mapOf(
            Prayer.FAJR to toZoned(field("fajr", "Fajr")),
            Prayer.DHUHR to toZoned(field("dohr", "dhuhr", "Dhuhr")),
            Prayer.ASR to toZoned(field("asr", "Asr")),
            Prayer.MAGHRIB to toZoned(field("maghreb", "maghrib", "Maghrib")),
            Prayer.ISHA to toZoned(field("ichaa", "isha", "Isha"))
        )
    }

    private companion object {
        const val BASE_URL = "https://habous-prayer-times-api.onrender.com"
    }
}
