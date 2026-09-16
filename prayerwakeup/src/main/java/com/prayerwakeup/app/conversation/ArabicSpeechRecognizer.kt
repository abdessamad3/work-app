package com.prayerwakeup.app.conversation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Result of one listen attempt: either a transcript, or a human-readable reason it failed. */
data class ListenResult(val text: String?, val failureReason: String? = null)

@Singleton
class ArabicSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Some OEM skins (seen on a Realme device) register their own RecognitionService as the
     * system default, and it responds with a static placeholder instead of a real transcript.
     * Prefer Google's speech service explicitly when it's installed, since it's the one that
     * actually transcribes speech; only fall back to whatever the OS considers default if
     * Google's isn't present on the device at all.
     */
    private class RecognizerHandle(val recognizer: SpeechRecognizer, val engineLabel: String)

    private fun createBestRecognizer(context: Context): RecognizerHandle {
        val googleService = runCatching {
            context.packageManager
                .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .firstOrNull { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
                ?.serviceInfo
                ?.let { ComponentName(it.packageName, it.name) }
        }.getOrNull()

        return if (googleService != null) {
            RecognizerHandle(SpeechRecognizer.createSpeechRecognizer(context, googleService), "Google")
        } else {
            RecognizerHandle(SpeechRecognizer.createSpeechRecognizer(context), "افتراضي النظام")
        }
    }

    /** Listens once and returns the top transcript, or a diagnostic reason it heard nothing. */
    suspend fun listenOnce(timeoutMillis: Long = 9_000L): ListenResult = withContext(Dispatchers.Main) {
        if (!isAvailable()) {
            return@withContext ListenResult(null, "لا توجد خدمة تعرف صوتي على هذا الجهاز")
        }

        suspendCancellableCoroutine { continuation ->
            val handle = createBestRecognizer(context)
            val recognizer = handle.recognizer
            var finished = false
            var speechDetected = false
            var maxRms = -100f
            var lastPartial: String? = null
            val timeoutHandler = Handler(Looper.getMainLooper())

            // Turns a bare error/empty outcome into something that says whether the mic
            // picked up any sound at all, so "nothing heard" and "heard but not understood"
            // don't look identical on screen.
            fun withDiagnostics(base: String): String {
                val micState = when {
                    speechDetected -> "تم رصد بداية كلام"
                    maxRms > -30f -> "التُقط صوت خافت لكن لم يُعتبر كلاماً"
                    else -> "لم يُلتقط أي صوت من الميكروفون"
                }
                val partial = lastPartial?.let { " — آخر تخمين جزئي: \"$it\"" }.orEmpty()
                return "$base (المحرك: ${handle.engineLabel}، $micState)$partial"
            }

            fun finish(result: ListenResult) {
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
                    val top = matches?.firstOrNull()
                    if (top.isNullOrBlank()) {
                        finish(ListenResult(null, withDiagnostics("تعرّف بلا كلمات")))
                    } else {
                        finish(ListenResult(top))
                    }
                }

                override fun onError(error: Int) = finish(ListenResult(null, withDiagnostics(describeError(error))))
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() {
                    speechDetected = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (rmsdB > maxRms) maxRms = rmsdB
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { lastPartial = it }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            }

            runCatching { recognizer.startListening(intent) }
                .onFailure { finish(ListenResult(null, "تعذر بدء الاستماع: ${it.message}")) }

            continuation.invokeOnCancellation { runCatching { recognizer.destroy() } }
            timeoutHandler.postDelayed({ finish(ListenResult(null, withDiagnostics("انتهت المهلة"))) }, timeoutMillis)
        }
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "خطأ شبكة (مهلة الاتصال)"
        SpeechRecognizer.ERROR_NETWORK -> "خطأ شبكة"
        SpeechRecognizer.ERROR_AUDIO -> "خطأ في تسجيل الصوت"
        SpeechRecognizer.ERROR_SERVER -> "خطأ من خادم التعرف الصوتي"
        SpeechRecognizer.ERROR_CLIENT -> "خطأ داخلي في التطبيق"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم يُسمع أي كلام"
        SpeechRecognizer.ERROR_NO_MATCH -> "لم يتم التعرف على الكلام"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة التعرف الصوتي مشغولة"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "إذن الميكروفون غير ممنوح لخدمة التعرف الصوتي"
        else -> "خطأ غير معروف (رمز $error)"
    }
}
