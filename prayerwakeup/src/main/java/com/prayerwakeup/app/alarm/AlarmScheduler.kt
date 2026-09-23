package com.prayerwakeup.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.updateAll
import com.prayerwakeup.app.data.PrayerTimesResolver
import com.prayerwakeup.app.data.settings.SchedulingStatusStore
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.PrayerTimeCalculator
import com.prayerwakeup.app.widget.PrayerGlanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val calculator: PrayerTimeCalculator,
    private val timesResolver: PrayerTimesResolver,
    private val schedulingStatusStore: SchedulingStatusStore
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /** Recomputes today/tomorrow's prayer times and (re)schedules every enabled alarm. */
    suspend fun rescheduleAll() {
        val settings = settingsRepository.settingsFlow.first()
        Prayer.entries.forEach { cancelPrayer(it) }
        cancelRefresh()

        if (!settings.hasLocation) {
            runCatching { PrayerGlanceWidget.updateAll(context) }
            return
        }
        val zoneId = runCatching { ZoneId.of(settings.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zoneId)
        val today = now.toLocalDate()

        // Tomorrow always comes from the offline calculator: the online Moroccan source only
        // exposes "today", and we never want an unreachable third-party service to be able to
        // leave a prayer unscheduled.
        val todayTimes = timesResolver.resolveForDate(settings, today, zoneId)
        val tomorrowTimes = calculator.calculate(
            today.plusDays(1), settings.latitude, settings.longitude, zoneId, settings.calculationMethod, settings.madhab
        )

        for (prayer in settings.enabledPrayers) {
            val todayTime = todayTimes.forPrayer(prayer)
            val target = if (todayTime.isAfter(now)) todayTime else tomorrowTimes.forPrayer(prayer)
            scheduleExact(requestCodeFor(prayer), target, buildPrayerIntent(prayer))
        }

        // Recompute again shortly after local midnight so times stay correct day to day.
        val refreshTarget = today.plusDays(1).atStartOfDay(zoneId).plusMinutes(2)
        scheduleExact(REFRESH_REQUEST_CODE, refreshTarget, buildRefreshIntent())

        schedulingStatusStore.recordSuccess(settings.enabledPrayers.size)
        runCatching { PrayerGlanceWidget.updateAll(context) }
    }

    fun cancelPrayer(prayer: Prayer) {
        cancel(requestCodeFor(prayer), buildPrayerIntent(prayer))
    }

    private fun cancelRefresh() {
        cancel(REFRESH_REQUEST_CODE, buildRefreshIntent())
    }

    private fun scheduleExact(requestCode: Int, time: ZonedDateTime, intent: Intent) {
        val manager = alarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = time.toInstant().toEpochMilli()
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canUseExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancel(requestCode: Int, intent: Intent) {
        val manager = alarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
    }

    private fun buildPrayerIntent(prayer: Prayer) = Intent(context, PrayerAlarmReceiver::class.java).apply {
        action = PrayerAlarmReceiver.ACTION_PRAYER_ALARM
        putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, prayer.name)
    }

    private fun buildRefreshIntent() = Intent(context, PrayerAlarmReceiver::class.java).apply {
        action = PrayerAlarmReceiver.ACTION_REFRESH
    }

    private fun requestCodeFor(prayer: Prayer) = 1000 + prayer.ordinal

    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    private companion object {
        const val REFRESH_REQUEST_CODE = 999
    }
}
