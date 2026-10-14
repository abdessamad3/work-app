package com.prayerwakeup.app.conversation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    private fun hasInternet(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

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
            var finishScheduled = false
            var speechDetected = false
            var maxRms = -100f
            var lastPartial: String? = null
            val startedAt = SystemClock.elapsedRealtime()
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

            fun finishNow(result: ListenResult) {
                if (finished) return
                finished = true
                timeoutHandler.removeCallbacksAndMessages(null)
                runCatching { recognizer.stopListening() }
                runCatching { recognizer.destroy() }
                if (continuation.isActive) continuation.resume(result)
            }

            // A structural failure (no network, no offline language pack, a recognizer that
            // rejects the request outright) can come back within milliseconds — seen live as
            // "error 12" the instant the call tries to listen. Without this, the user gets no
            // real window to speak at all: the call just cycles straight to the next nudge.
            // Pad such early, speech-less failures out to a minimum listening window instead.
            fun finish(result: ListenResult) {
                if (finished || finishScheduled) return
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (result.text == null && !speechDetected && elapsed < MIN_LISTEN_MILLIS) {
                    finishScheduled = true
                    timeoutHandler.postDelayed({ finishNow(result) }, MIN_LISTEN_MILLIS - elapsed)
                } else {
                    finishNow(result)
                }
            }

            // Google's on-device "final" pass is sometimes stricter than its streaming partial
            // pass and rejects audio the partial pass already transcribed correctly (seen live:
            // partial "أنا مستيقظ" followed by a final NO_MATCH). Rather than discard a decent
            // partial guess when the final pass comes back empty, use it as the answer.
            fun finishWithPartialFallback(diagnosticBase: String) {
                val partial = lastPartial
                if (!partial.isNullOrBlank()) {
                    finish(ListenResult(partial))
                } else {
                    finish(ListenResult(null, withDiagnostics(diagnosticBase)))
                }
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val top = matches?.firstOrNull()
                    if (top.isNullOrBlank()) {
                        finishWithPartialFallback("تعرّف بلا كلمات")
                    } else {
                        finish(ListenResult(top))
                    }
                }

                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        finishWithPartialFallback(describeError(error))
                    } else {
                        finish(ListenResult(null, withDiagnostics(describeError(error))))
                    }
                }
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

            // Without a network connection, Google's cloud recognizer can reject the request
            // outright (seen live as an immediate "language not supported" error) before the user
            // ever gets a chance to speak. Hint at on-device recognition in that case instead —
            // it actually listens, as long as an Arabic offline pack is installed. "ar-SA" is more
            // accurate online, but offline packs are usually registered under the generic "ar".
            val preferOffline = !hasInternet()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (preferOffline) "ar" else "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
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
        12 -> "التعرف الصوتي دون إنترنت غير متوفر للعربية على هذا الجهاز (نزّل حزمة اللغة العربية الصوتية من إعدادات تطبيق Google)"
        13 -> "تعذر تحميل حزمة اللغة العربية الصوتية دون إنترنت"
        else -> "خطأ غير معروف (رمز $error)"
    }

    private companion object {
        // How long a failed listen attempt is padded out to before reporting failure, so a
        // structural error that fires almost instantly (no network, no offline language pack)
        // still gives the user a real window to speak instead of cycling through nudges in a
        // blink. Well under the 9s overall timeout, so it never delays a genuine full-length listen.
        const val MIN_LISTEN_MILLIS = 3_000L
    }
}
