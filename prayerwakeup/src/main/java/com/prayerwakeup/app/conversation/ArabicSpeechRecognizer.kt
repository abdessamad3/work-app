package com.prayerwakeup.app.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ArabicSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Listens once and returns the top transcript, or null on silence/timeout/error. */
    suspend fun listenOnce(timeoutMillis: Long = 8_000L): String? = withContext(Dispatchers.Main) {
        if (!isAvailable()) return@withContext null

        suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            var finished = false
            val timeoutHandler = Handler(Looper.getMainLooper())

            fun finish(result: String?) {
                if (finished) return
                finished = true
                timeoutHandler.removeCallbacksAndMessages(null)
                runCatching { recognizer.stopListening() }
                runCatching { recognizer.destroy() }
                if (continuation.isActive) continuation.resume(result)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    finish(matches?.firstOrNull())
                }

                override fun onError(error: Int) = finish(null)
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            }

            runCatching { recognizer.startListening(intent) }.onFailure { finish(null) }

            continuation.invokeOnCancellation { runCatching { recognizer.destroy() } }
            timeoutHandler.postDelayed({ finish(null) }, timeoutMillis)
        }
    }
}
