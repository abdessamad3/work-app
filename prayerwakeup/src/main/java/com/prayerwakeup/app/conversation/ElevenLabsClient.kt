package com.prayerwakeup.app.conversation

import android.content.Context
import android.media.MediaPlayer
import com.prayerwakeup.app.data.settings.SecureKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ElevenLabsVoice(val id: String, val name: String)

/**
 * Optional replacement for Android's built-in TTS: synthesizes each line through ElevenLabs
 * (a natural-sounding stock voice on the free tier — real voice *cloning* needs their paid
 * plan) and plays the resulting audio. Every call here is used behind a fallback to the plain
 * Android TTS engine already in the app, so a quota cutoff or network failure never leaves a
 * call silent.
 */
@Singleton
class ElevenLabsClient @Inject constructor(
    private val keyStore: SecureKeyStore,
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(): Boolean = keyStore.hasElevenLabsApiKey()

    suspend fun fetchVoices(): Result<List<ElevenLabsVoice>> = runCatching {
        val apiKey = keyStore.elevenLabsApiKey.value
        if (apiKey.isBlank()) throw IllegalStateException("لا يوجد مفتاح ElevenLabs API")

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/voices")
            .addHeader("xi-api-key", apiKey)
            .get()
            .build()

        executeCancellable(request) { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("ElevenLabs API error ${response.code}: $bodyString")
            val voicesArray: JSONArray = JSONObject(bodyString).optJSONArray("voices") ?: JSONArray()
            val voices = (0 until voicesArray.length()).map { i ->
                val v = voicesArray.getJSONObject(i)
                ElevenLabsVoice(id = v.getString("voice_id"), name = v.optString("name", "صوت"))
            }
            if (voices.isEmpty()) throw IOException("لا توجد أصوات متاحة في حسابك")
            voices
        }
    }

    /** Synthesizes [text] with ElevenLabs and plays it through the device speaker, suspending until done. */
    suspend fun speak(text: String, voiceId: String): Result<Unit> =
        synthesizeToFile(text, voiceId).mapCatching { file ->
            try {
                playAndAwait(file)
            } finally {
                file.delete()
            }
        }

    private suspend fun synthesizeToFile(text: String, voiceId: String): Result<File> = runCatching {
        val apiKey = keyStore.elevenLabsApiKey.value
        if (apiKey.isBlank()) throw IllegalStateException("لا يوجد مفتاح ElevenLabs API")

        val requestJson = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
        }
        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            .addHeader("xi-api-key", apiKey)
            .addHeader("content-type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeCancellable(request) { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IOException("ElevenLabs API error ${response.code}: $errorBody")
            }
            val bytes = response.body?.bytes() ?: throw IOException("استجابة صوتية فارغة")
            val file = File.createTempFile("prayer_tts_", ".mp3", context.cacheDir)
            file.writeBytes(bytes)
            file
        }
    }

    // A plain blocking call.execute() ignores coroutine cancellation entirely — declining mid-call
    // used to leave a synthesis request running for up to the full timeout even after the user had
    // already hung up. enqueue() + suspendCancellableCoroutine lets invokeOnCancellation actually
    // abort the in-flight request the moment the caller's job is cancelled. [onResponse] runs (and
    // the response body gets read) on OkHttp's own callback thread, not the caller's, so this never
    // blocks whatever dispatcher called into this client.
    private suspend fun <T> executeCancellable(request: Request, onResponse: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use { runCatching { onResponse(it) } }
                    if (continuation.isActive) {
                        result.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) }
                        )
                    }
                }
            })
        }

    private suspend fun playAndAwait(file: File): Unit = suspendCancellableCoroutine { continuation ->
        val player = MediaPlayer()
        fun finish() {
            runCatching { player.release() }
            if (continuation.isActive) continuation.resume(Unit)
        }
        runCatching {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { finish() }
            player.setOnErrorListener { _, _, _ -> finish(); true }
            player.prepare()
            player.start()
        }.onFailure { finish() }
        continuation.invokeOnCancellation { runCatching { player.release() } }
    }
}
