package com.prayerwakeup.app.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import com.prayerwakeup.app.PrayerWakeupApp
import com.prayerwakeup.app.conversation.CallEvent
import com.prayerwakeup.app.conversation.ConversationManager
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.data.tracking.PrayerLogRepository
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.WakeChallenge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class CallForegroundService : Service() {

    @Inject lateinit var sessionController: CallSessionController
    @Inject lateinit var ringtonePlayer: RingtonePlayer
    @Inject lateinit var conversationManager: ConversationManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var prayerLogRepository: PrayerLogRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var actionsJob: Job? = null
    private var answerJob: Job? = null
    private val cancelled = AtomicBoolean(false)
    private val answered = AtomicBoolean(false)
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> {
                val prayerName = intent.getStringExtra(EXTRA_PRAYER)
                val prayer = prayerName?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
                if (prayer != null) {
                    serviceScope.launch { beginCall(prayer) }
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

    private suspend fun beginCall(prayer: Prayer) {
        cancelled.set(false)
        answered.set(false)
        requestAudioFocus()
        val wakeChallenge = settingsRepository.settingsFlow.first().wakeChallenge
        startForeground(NOTIFICATION_ID, buildForegroundNotification(prayer))
        postFullScreenCallNotification(prayer, wakeChallenge)
        ringtonePlayer.start()
        sessionController.startRinging(prayer)

        actionsJob?.cancel()
        actionsJob = serviceScope.launch {
            sessionController.actions.collect { action ->
                // Answer runs the (long, suspending) conversation in its own child
                // coroutine so this collector loop stays free to react to an
                // in-call Decline/hang-up immediately instead of queuing behind it.
                when (action) {
                    // Guarded so a double-tap (or a stale queued Answer firing after the UI
                    // already moved on) can't start a second conversation session alongside the
                    // first — both would otherwise share the same singleton TTS/player and step
                    // on each other's playback.
                    CallAction.Answer -> if (answered.compareAndSet(false, true)) {
                        answerJob = launch { handleAnswer(prayer) }
                    }
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

        // A concurrent Decline already logged the outcome and will cancel this job — skip
        // logging and finishing again here so the two paths don't double-write the same call.
        if (!cancelled.get()) {
            prayerLogRepository.logCallOutcome(LocalDate.now(), prayer, answered = true)
            delay(1500)
            finishCall()
        }
    }

    private fun handleDecline() {
        cancelled.set(true)
        ringtonePlayer.stop()
        conversationManager.stop()
        // Log the outcome here, before cancelling — cancelling answerJob below can abort
        // handleAnswer via CancellationException before it ever reaches its own post-call
        // logging, so handleDecline owns the write for every decline path (not just Ringing),
        // guarded by `cancelled` in handleAnswer so the two never both log the same call.
        when (val state = sessionController.uiState.value) {
            is CallUiState.Ringing -> {
                sessionController.endCall(state.prayer)
                serviceScope.launch {
                    prayerLogRepository.logCallOutcome(LocalDate.now(), state.prayer, answered = false)
                }
            }
            is CallUiState.InCall -> {
                // They did pick up, so this still counts as answered even though they hung up
                // partway through.
                sessionController.endCall(state.prayer)
                serviceScope.launch {
                    prayerLogRepository.logCallOutcome(LocalDate.now(), state.prayer, answered = true)
                }
            }
            else -> Unit
        }
        // The isCancelled() check inside ConversationManager's loop only runs between suspend
        // points — while it's actually suspended awaiting a Gemini/ElevenLabs network call, it
        // can't notice cancelled=true until that call returns. Cancelling the job directly
        // propagates into the (now cancellation-aware) network call and aborts it immediately
        // instead of leaving it running for a call the user already ended.
        answerJob?.cancel()
        finishCall()
    }

    private fun finishCall() {
        abandonAudioFocus()
        sessionController.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Exclusive transient focus tells other apps (music, podcasts, video) to stop playback
    // entirely rather than just duck — held for the whole call (ringing + conversation), not just
    // the ringtone, so nothing competes with the wake-up call from start to finish.
    private fun requestAudioFocus() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .build()
        audioManager.requestAudioFocus(request)
        audioFocusRequest = request
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    private fun buildForegroundNotification(prayer: Prayer): Notification =
        NotificationCompat.Builder(this, PrayerWakeupApp.STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("مكالمة صلاة ${prayer.arabicName}")
            .setContentText("جاري الاتصال بك لإيقاظك للصلاة")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

    private fun postFullScreenCallNotification(prayer: Prayer, wakeChallenge: WakeChallenge) {
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // A notification action can only fire a PendingIntent, not run the challenge UI itself —
        // so once a wake-up challenge is configured, "رفض" here just opens the same full-screen
        // call screen (where the challenge is enforced) instead of declining directly, closing
        // the one bypass that let a plain tap-and-dismiss skip the challenge entirely.
        val declinePendingIntent = if (wakeChallenge == WakeChallenge.NONE) {
            val declineIntent = Intent(this, CallForegroundService::class.java).apply {
                action = ACTION_DECLINE_CALL
            }
            PendingIntent.getService(
                this, 1, declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            fullScreenPendingIntent
        }
        val notification = NotificationCompat.Builder(this, PrayerWakeupApp.CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان وقت صلاة ${prayer.arabicName}")
            .setContentText("اضغط للرد")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                if (wakeChallenge == WakeChallenge.NONE) "رفض" else "فتح",
                declinePendingIntent
            )
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
        abandonAudioFocus()
        getSystemService(android.app.NotificationManager::class.java)?.cancel(FULL_SCREEN_NOTIFICATION_ID)
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
