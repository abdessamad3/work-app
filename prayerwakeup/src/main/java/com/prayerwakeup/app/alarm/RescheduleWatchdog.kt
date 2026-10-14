package com.prayerwakeup.app.alarm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * A periodic safety net on top of boot-time and settings-change rescheduling: some OEM battery
 * managers silently clear an app's pending alarms outside of a reboot, with no callback the app
 * can observe. Re-running rescheduleAll() every few hours costs nothing when alarms are already
 * correct (it's idempotent) and quietly repairs the rare case where they were cleared.
 */
object RescheduleWatchdog {
    private const val WORK_NAME = "reschedule_watchdog"

    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<RescheduleWatchdogWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

@HiltWorker
class RescheduleWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alarmScheduler: AlarmScheduler
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        alarmScheduler.rescheduleAll()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
