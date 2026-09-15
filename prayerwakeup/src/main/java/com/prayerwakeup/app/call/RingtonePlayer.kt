package com.prayerwakeup.app.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingtonePlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    fun start() {
        stop()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }
        vibrate()
    }

    private fun vibrate() {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 1000, 1000)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    fun stop() {
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        context.getSystemService(Vibrator::class.java)?.cancel()
    }
}
