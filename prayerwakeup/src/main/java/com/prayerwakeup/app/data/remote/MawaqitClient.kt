package com.prayerwakeup.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class MawaqitTimes(
    val mosqueName: String,
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

/**
 * Reads a specific mosque's prayer times straight from its public page on mawaqit.net — no
 * account, no API key, since Mawaqit's real API is private but mosque pages are meant to be
 * displayed publicly (e.g. on a screen in the mosque) and embed the day's times as a JS object
 * literal ("confData") in the page source. This is an unofficial, reverse-engineered read of
 * that markup, so it's used behind the same offline-calculator fallback as every other online
 * source in this app — if mawaqit.net changes its page structure, this just stops working
 * silently rather than breaking anything.
 */
@Singleton
class MawaqitClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTodayTimes(mosqueIdentifier: String): Result<MawaqitTimes> = withContext(Dispatchers.IO) {
        runCatching {
            val slug = normalizeSlug(mosqueIdentifier)
            if (slug.isBlank()) throw IllegalStateException("لم يُحدد رابط أو اسم المسجد")

            val request = Request.Builder()
                .url("https://mawaqit.net/en/$slug")
                .header("User-Agent", "Mozilla/5.0 (Android) PrayerWakeupApp")
                .get()
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} — تأكد من نسخ الرابط كاملاً من صفحة المسجد الحقيقية على mawaqit.net")
                }
                response.body?.string()?.takeIf { it.isNotBlank() } ?: throw IOException("صفحة المسجد فارغة")
            }

            val confData = extractConfData(html)
                ?: throw IOException("تعذر العثور على بيانات المسجد في الصفحة (قد يكون الرابط أو الاسم خاطئاً)")

            parseConfData(confData)
        }
    }

    /** mawaqit.net identifies mosques in its public URLs by a text slug (e.g.
     * "mawaqit.net/en/some-mosque-name"), not by the numeric "mosque code" some mosques show
     * on their in-building display — that code isn't a public identifier. So this accepts
     * either a bare slug or a full URL the user copy-pasted from their browser's address bar
     * after finding their mosque on mawaqit.net, and always extracts just the slug: the last
     * non-empty path segment, which is also correct for a bare slug typed directly. */
    private fun normalizeSlug(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val afterDomain = trimmed.substringAfter("mawaqit.net/", trimmed)
        return afterDomain.substringAfterLast('/').trim()
    }

    /** confData is a JS object literal embedded in a <script> tag, not a standalone JSON
     * response, so it has to be located by marker text and extracted with brace counting
     * (respecting string contents) rather than parsed as the whole HTTP body. */
    private fun extractConfData(html: String): JSONObject? {
        val markerIndex = html.indexOf("confData")
        if (markerIndex == -1) return null
        val braceStart = html.indexOf('{', markerIndex)
        if (braceStart == -1) return null

        var depth = 0
        var inString = false
        var escapeNext = false
        var i = braceStart
        while (i < html.length) {
            val c = html[i]
            if (inString) {
                when {
                    escapeNext -> escapeNext = false
                    c == '\\' -> escapeNext = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val jsonStr = html.substring(braceStart, i + 1)
                            return runCatching { JSONObject(jsonStr) }.getOrNull()
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun parseConfData(json: JSONObject): MawaqitTimes {
        val name = json.optString("name").ifBlank { json.optString("label", "المسجد") }
        val times = json.optJSONArray("times")
            ?: throw IOException("تعذر العثور على مواقيت اليوم في بيانات المسجد")
        if (times.length() < 5) throw IOException("بيانات المواقيت غير مكتملة")
        return MawaqitTimes(
            mosqueName = name,
            fajr = times.getString(0),
            dhuhr = times.getString(1),
            asr = times.getString(2),
            maghrib = times.getString(3),
            isha = times.getString(4)
        )
    }
}
