package com.prayerwakeup.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.prayerwakeup.app.call.CallForegroundService
import com.prayerwakeup.app.domain.Prayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PRAYER_ALARM -> {
                val prayerName = intent.getStringExtra(EXTRA_PRAYER) ?: return
                val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrNull() ?: return
                val serviceIntent = Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_START_CALL
                    putExtra(CallForegroundService.EXTRA_PRAYER, prayer.name)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }

            ACTION_REFRESH -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        alarmScheduler.rescheduleAll()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_PRAYER_ALARM = "com.prayerwakeup.app.action.PRAYER_ALARM"
        const val ACTION_REFRESH = "com.prayerwakeup.app.action.REFRESH_SCHEDULE"
        const val EXTRA_PRAYER = "extra_prayer"
    }
}
