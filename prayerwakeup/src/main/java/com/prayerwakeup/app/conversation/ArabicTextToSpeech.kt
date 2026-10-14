package com.prayerwakeup.app.conversation

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ArabicTextToSpeech @Inject constructor(
    @ApplicationContext context: Context
) {
    private var engine: TextToSpeech? = null
    private var arabicAvailable = false

    init {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale("ar"))
                arabicAvailable = result == TextToSpeech.LANG_AVAILABLE ||
                    result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            }
        }
    }

    fun isArabicAvailable(): Boolean = arabicAvailable

    suspend fun speak(text: String) {
        val tts = engine ?: return
        suspendCancellableCoroutine<Unit> { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
                }
            })
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            if (result == TextToSpeech.ERROR && continuation.isActive) {
                continuation.resume(Unit)
            }
            continuation.invokeOnCancellation { tts.stop() }
        }
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.shutdown()
        engine = null
    }
}
