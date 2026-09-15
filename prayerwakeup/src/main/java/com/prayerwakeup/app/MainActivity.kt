package com.prayerwakeup.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.prayerwakeup.app.alarm.AlarmScheduler
import com.prayerwakeup.app.ui.navigation.PrayerWakeupNavGraph
import com.prayerwakeup.app.ui.theme.PrayerWakeupTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { rescheduleAlarms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStartupPermissions()

        setContent {
            PrayerWakeupTheme {
                PrayerWakeupNavGraph()
            }
        }
    }

    private fun requestStartupPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            requestPermissions.launch(needed.toTypedArray())
        } else {
            rescheduleAlarms()
        }
    }

    private fun rescheduleAlarms() {
        lifecycleScope.launch { alarmScheduler.rescheduleAll() }
    }
}
