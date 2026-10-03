package com.prayerwakeup.app.conversation

import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Plays back one of the user's recorded script clips during a call, suspending until it finishes. */
@Singleton
class RecordedVoicePlayer @Inject constructor() {
    private var player: MediaPlayer? = null

    suspend fun play(file: File) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val mediaPlayer = runCatching {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        release()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    setOnErrorListener { _, _, _ ->
                        release()
                        if (continuation.isActive) continuation.resume(Unit)
                        true
                    }
                    prepare()
                    start()
                }
            }.getOrNull()

            if (mediaPlayer == null) {
                if (continuation.isActive) continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }

            player = mediaPlayer
            continuation.invokeOnCancellation {
                runCatching { mediaPlayer.stop(); mediaPlayer.release() }
            }
        }
    }

    fun stop() {
        runCatching { player?.stop(); player?.release() }
        player = null
    }
}
