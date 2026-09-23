package com.prayerwakeup.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.schedulingStatusDataStore by preferencesDataStore(name = "scheduling_status")

data class SchedulingStatus(val lastScheduledAtEpochMillis: Long = 0L, val scheduledCount: Int = 0)

/**
 * Records proof that AlarmScheduler.rescheduleAll() actually ran and how many alarms it armed,
 * separate from PrayerSettings (a preference the user set) since this is operational status the
 * app observes about itself — surfaced on Home so "is this actually going to wake me up" has a
 * real answer instead of just trusting that some earlier reschedule call worked.
 */
@Singleton
class SchedulingStatusStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAST_SCHEDULED_AT = longPreferencesKey("last_scheduled_at")
        val SCHEDULED_COUNT = intPreferencesKey("scheduled_count")
    }

    val statusFlow: Flow<SchedulingStatus> = context.schedulingStatusDataStore.data.map { prefs ->
        SchedulingStatus(
            lastScheduledAtEpochMillis = prefs[Keys.LAST_SCHEDULED_AT] ?: 0L,
            scheduledCount = prefs[Keys.SCHEDULED_COUNT] ?: 0
        )
    }

    suspend fun recordSuccess(scheduledCount: Int) {
        context.schedulingStatusDataStore.edit { prefs ->
            prefs[Keys.LAST_SCHEDULED_AT] = System.currentTimeMillis()
            prefs[Keys.SCHEDULED_COUNT] = scheduledCount
        }
    }
}
