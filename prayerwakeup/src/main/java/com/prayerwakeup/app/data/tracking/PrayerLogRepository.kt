package com.prayerwakeup.app.data.tracking

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prayerwakeup.app.domain.Prayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One entry per (date, prayer). [prayed] is the user's own self-report ("I actually prayed",
 * tapped on Home) — the app has no way to verify this, so it's honest about being a self-report
 * rather than inferring it from the call outcome. [callAnswered] is a separate, automatic record
 * of whether the wake-up call itself was answered or declined/missed, for diagnosing the alarm
 * mechanic rather than for measuring devotion.
 */
data class PrayerLogEntry(
    val date: LocalDate,
    val prayer: Prayer,
    val prayed: Boolean = false,
    val callAnswered: Boolean? = null
)

private val Context.prayerLogDataStore by preferencesDataStore(name = "prayer_log")

@Singleton
class PrayerLogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENTRIES_JSON = stringPreferencesKey("entries_json")
    }

    val entriesFlow: Flow<List<PrayerLogEntry>> = context.prayerLogDataStore.data.map { prefs ->
        parseEntries(prefs[Keys.ENTRIES_JSON] ?: "[]")
    }

    suspend fun setPrayed(date: LocalDate, prayer: Prayer, prayed: Boolean) {
        update(date, prayer) { it.copy(prayed = prayed) }
    }

    suspend fun logCallOutcome(date: LocalDate, prayer: Prayer, answered: Boolean) {
        update(date, prayer) { it.copy(callAnswered = answered) }
    }

    private suspend fun update(date: LocalDate, prayer: Prayer, transform: (PrayerLogEntry) -> PrayerLogEntry) {
        context.prayerLogDataStore.edit { prefs ->
            val entries = parseEntries(prefs[Keys.ENTRIES_JSON] ?: "[]").toMutableList()
            val index = entries.indexOfFirst { it.date == date && it.prayer == prayer }
            if (index >= 0) {
                entries[index] = transform(entries[index])
            } else {
                entries.add(transform(PrayerLogEntry(date = date, prayer = prayer)))
            }
            // Unbounded growth isn't useful here — a rolling window is plenty for a streak and a
            // week-in-review, and keeps the DataStore value small forever.
            val cutoff = LocalDate.now().minusDays(RETENTION_DAYS)
            val trimmed = entries.filter { it.date >= cutoff }
            prefs[Keys.ENTRIES_JSON] = serializeEntries(trimmed)
        }
    }

    private fun parseEntries(json: String): List<PrayerLogEntry> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val prayer = runCatching { Prayer.valueOf(obj.optString("prayer")) }.getOrNull() ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(obj.optString("date")) }.getOrNull() ?: return@mapNotNull null
            PrayerLogEntry(
                date = date,
                prayer = prayer,
                prayed = obj.optBoolean("prayed", false),
                callAnswered = if (obj.has("callAnswered") && !obj.isNull("callAnswered")) obj.optBoolean("callAnswered") else null
            )
        }
    }.getOrDefault(emptyList())

    private fun serializeEntries(entries: List<PrayerLogEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("date", entry.date.toString())
            obj.put("prayer", entry.prayer.name)
            obj.put("prayed", entry.prayed)
            if (entry.callAnswered != null) obj.put("callAnswered", entry.callAnswered)
            array.put(obj)
        }
        return array.toString()
    }

    private companion object {
        const val RETENTION_DAYS = 60L
    }
}
