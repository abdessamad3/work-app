package com.prayerwakeup.app.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.prayerwakeup.app.PrayerWakeupApp
import com.prayerwakeup.app.conversation.CallEvent
import com.prayerwakeup.app.conversation.ConversationManager
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.Prayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class CallForegroundService : Service() {

    @Inject lateinit var sessionController: CallSessionController
    @Inject lateinit var ringtonePlayer: RingtonePlayer
    @Inject lateinit var conversationManager: ConversationManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var actionsJob: Job? = null
    private val cancelled = AtomicBoolean(false)
    private var screenOffReceiverRegistered = false

    // Android doesn't let apps see the physical power-button press itself, only its effect
    // (the screen turning off), which the power button is the main real-world cause of during
    // an active call. Requested explicitly: treat screen-off as "dismiss the call" the same as
    // tapping decline, accepting that any screen-off (not just a deliberate power-button tap)
    // will silence it.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) handleDecline()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenOffReceiverRegistered = true
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> {
                val prayerName = intent.getStringExtra(EXTRA_PRAYER)
                val prayer = prayerName?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
                if (prayer != null) {
                    beginCall(prayer)
                } else {
                    stopSelf()
                }
            }
            // Handled directly here (not routed through sessionController.actions) so tapping
            // "Decline" on the notification works reliably even in the rare case the service
            // had to be freshly restarted to deliver this intent, when no collector would be
            // listening on that flow yet.
            ACTION_DECLINE_CALL -> handleDecline()
        }
        return START_NOT_STICKY
    }

    private fun beginCall(prayer: Prayer) {
        cancelled.set(false)
        startForeground(NOTIFICATION_ID, buildForegroundNotification(prayer))
        postFullScreenCallNotification(prayer)
        ringtonePlayer.start()
        sessionController.startRinging(prayer)

        actionsJob?.cancel()
        actionsJob = serviceScope.launch {
            sessionController.actions.collect { action ->
                // Answer runs the (long, suspending) conversation in its own child
                // coroutine so this collector loop stays free to react to an
                // in-call Decline/hang-up immediately instead of queuing behind it.
                when (action) {
                    CallAction.Answer -> launch { handleAnswer(prayer) }
                    CallAction.Decline -> handleDecline()
                }
            }
        }
    }

    private suspend fun handleAnswer(prayer: Prayer) {
        ringtonePlayer.stop()
        val settings = settingsRepository.settingsFlow.first()
        val transcript = mutableListOf<TranscriptLine>()
        val maxTurns = (settings.maxCallMinutes * 2).coerceAtLeast(3)

        conversationManager.runCall(
            prayer = prayer,
            persona = settings.persona,
            maxTurns = maxTurns,
            elevenLabsVoiceId = settings.elevenLabsVoiceId.ifBlank { null },
            isCancelled = { cancelled.get() }
        ) { event ->
            when (event) {
                is CallEvent.AgentSpeaking -> {
                    transcript.add(TranscriptLine(Speaker.AGENT, event.text))
                    sessionController.updateInCall(prayer, transcript.toList(), listening = false)
                }
                is CallEvent.UserHeard -> {
                    transcript.add(TranscriptLine(Speaker.USER, event.text))
                    sessionController.updateInCall(prayer, transcript.toList(), listening = false)
                }
                is CallEvent.Debug -> {
                    transcript.add(TranscriptLine(Speaker.SYSTEM, event.text))
                    sessionController.updateInCall(prayer, transcript.toList(), listening = false)
                }
                CallEvent.Listening -> sessionController.updateInCall(prayer, transcript.toList(), listening = true)
                CallEvent.Ended -> sessionController.endCall(prayer)
            }
        }

        delay(1500)
        finishCall()
    }

    private fun handleDecline() {
        cancelled.set(true)
        ringtonePlayer.stop()
        conversationManager.stop()
        when (val state = sessionController.uiState.value) {
            is CallUiState.Ringing -> sessionController.endCall(state.prayer)
            is CallUiState.InCall -> sessionController.endCall(state.prayer)
            else -> Unit
        }
        finishCall()
    }

    private fun finishCall() {
        sessionController.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildForegroundNotification(prayer: Prayer): Notification =
        NotificationCompat.Builder(this, PrayerWakeupApp.STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("مكالمة صلاة ${prayer.arabicName}")
            .setContentText("جاري الاتصال بك لإيقاظك للصلاة")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

    private fun postFullScreenCallNotification(prayer: Prayer) {
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declineIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_DECLINE_CALL
        }
        val declinePendingIntent = PendingIntent.getService(
            this, 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, PrayerWakeupApp.CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان وقت صلاة ${prayer.arabicName}")
            .setContentText("اضغط للرد")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "رفض", declinePendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(FULL_SCREEN_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        cancelled.set(true)
        actionsJob?.cancel()
        serviceScope.cancel()
        ringtonePlayer.stop()
        conversationManager.stop()
        getSystemService(android.app.NotificationManager::class.java)?.cancel(FULL_SCREEN_NOTIFICATION_ID)
        if (screenOffReceiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenOffReceiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_CALL = "com.prayerwakeup.app.action.START_CALL"
        const val ACTION_DECLINE_CALL = "com.prayerwakeup.app.action.DECLINE_CALL"
        const val EXTRA_PRAYER = "extra_prayer"
        private const val NOTIFICATION_ID = 42
        private const val FULL_SCREEN_NOTIFICATION_ID = 43
    }
}
