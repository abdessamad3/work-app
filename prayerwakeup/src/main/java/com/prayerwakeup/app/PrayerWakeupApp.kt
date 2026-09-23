package com.prayerwakeup.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.prayerwakeup.app.alarm.RescheduleWatchdog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PrayerWakeupApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        RescheduleWatchdog.enqueue(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)

        val callChannel = NotificationChannel(
            CALL_CHANNEL_ID,
            "مكالمات وقت الصلاة",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيه صوتي كامل الشاشة قبل كل صلاة"
            setSound(null, null) // service plays the ringtone itself so it can be stopped/looped
            enableVibration(false)
        }

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "حالة التطبيق",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "إشعار مستمر أثناء المكالمة"
        }

        manager.createNotificationChannel(callChannel)
        manager.createNotificationChannel(statusChannel)
    }

    companion object {
        const val CALL_CHANNEL_ID = "prayer_call_channel"
        const val STATUS_CHANNEL_ID = "prayer_status_channel"
    }
}
