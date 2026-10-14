package com.prayerwakeup.app.data.remote

import com.prayerwakeup.app.domain.CalculationMethod
import com.prayerwakeup.app.domain.Madhab
import com.prayerwakeup.app.domain.Prayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the AlAdhan API (api.aladhan.com) — a free, no-key, widely used, actively
 * maintained prayer-times service. Unlike the Morocco Habous and Mawaqit sources, it needs no
 * extra per-user configuration: it's just asked for whichever calculation method and madhab the
 * user already picked for the offline calculator (mapped to AlAdhan's own method/school IDs), so
 * selecting it is a straight "prefer this to the offline calculation when reachable" toggle. As
 * with every other online source here, any failure just leaves the offline result in place.
 */
@Singleton
class AlAdhanClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTodayTimes(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zoneId: ZoneId,
        method: CalculationMethod,
        madhab: Madhab
    ): Result<Map<Prayer, ZonedDateTime>> = withContext(Dispatchers.IO) {
        runCatching {
            val dateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val school = if (madhab == Madhab.HANAFI) 1 else 0
            val tz = URLEncoder.encode(zoneId.id, "UTF-8")
            val url = "https://api.aladhan.com/v1/timings/$dateStr" +
                "?latitude=$latitude&longitude=$longitude&method=${methodId(method)}&school=$school&timezonestring=$tz"

            val request = Request.Builder().url(url).get().build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body?.string()?.takeIf { it.isNotBlank() } ?: throw IOException("استجابة فارغة من الخدمة")
            }

            parseTimings(body, date, zoneId)
        }
    }

    // AlAdhan's own numbering for these authorities; stable and well-documented across every
    // client library built on this API.
    private fun methodId(method: CalculationMethod): Int = when (method) {
        CalculationMethod.KARACHI -> 1
        CalculationMethod.ISNA -> 2
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> 3
        CalculationMethod.UMM_AL_QURA -> 4
        CalculationMethod.EGYPTIAN -> 5
        CalculationMethod.MOROCCO -> 21
    }

    private fun parseTimings(body: String, date: LocalDate, zoneId: ZoneId): Map<Prayer, ZonedDateTime> {
        val json = JSONObject(body)
        if (json.optInt("code") != 200) throw IOException("رمز خطأ من الخدمة: ${json.optInt("code")}")
        val timings = json.optJSONObject("data")?.optJSONObject("timings")
            ?: throw IOException("تعذر العثور على مواقيت اليوم في استجابة الخدمة")

        val formatter = DateTimeFormatter.ofPattern("H:mm")
        fun field(key: String): String =
            timings.optString(key).takeIf { it.isNotBlank() } ?: throw IOException("حقل مفقود في استجابة الخدمة: $key")

        // AlAdhan times can carry a trailing UTC-offset annotation like "05:12 (+01)"; only the
        // leading HH:mm is the actual time we need.
        fun toZoned(raw: String): ZonedDateTime {
            val time = LocalTime.parse(raw.trim().substringBefore(' '), formatter)
            return date.atTime(time).atZone(zoneId)
        }

        return mapOf(
            Prayer.FAJR to toZoned(field("Fajr")),
            Prayer.DHUHR to toZoned(field("Dhuhr")),
            Prayer.ASR to toZoned(field("Asr")),
            Prayer.MAGHRIB to toZoned(field("Maghrib")),
            Prayer.ISHA to toZoned(field("Isha"))
        )
    }
}
