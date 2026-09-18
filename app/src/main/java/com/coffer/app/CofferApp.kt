package com.coffer.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.coffer.app.work.OVERDUE_NOTIFICATION_CHANNEL_ID
import com.coffer.app.work.OVERDUE_WORK_NAME
import com.coffer.app.work.OverdueCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class CofferApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(CRASH_KEY, stackTrace)
                    .commit()
            } catch (_: Throwable) {
                // Crash reporting must never itself crash harder.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        createOverdueNotificationChannel()
        scheduleOverdueCheck()
    }

    private fun createOverdueNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OVERDUE_NOTIFICATION_CHANNEL_ID,
                "Overdue payments",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Lets you know when an order is past its due date and not fully paid." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Runs once a day in the background; MainActivity also fires an immediate check on every app open. */
    private fun scheduleOverdueCheck() {
        val request = PeriodicWorkRequestBuilder<OverdueCheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(OVERDUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val CRASH_PREFS = "crash_log"
        const val CRASH_KEY = "last_crash"
    }
}
