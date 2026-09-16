package com.prayerwakeup.app.conversation

import com.prayerwakeup.app.data.settings.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ConversationTurn(val role: String, val text: String)

/** Minimal client for the Google Gemini API, used to drive the live wake-up conversation. */
@Singleton
class GeminiClient @Inject constructor(
    private val keyStore: SecureKeyStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(): Boolean = keyStore.hasGeminiApiKey()

    suspend fun sendMessage(systemPrompt: String, history: List<ConversationTurn>): Result<String> =
        withContext(Dispatchers.IO) {
            val apiKey = keyStore.geminiApiKey.value
            if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No Gemini API key configured"))

            val contents = JSONArray().apply {
                history.forEach { turn ->
                    val geminiRole = if (turn.role == "assistant") "model" else "user"
                    put(
                        JSONObject()
                            .put("role", geminiRole)
                            .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
                    )
                }
            }
            val requestJson = JSONObject().apply {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                put("contents", contents)
                put("generationConfig", JSONObject().put("maxOutputTokens", 300))
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
                .addHeader("content-type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Gemini API error ${response.code}: $bodyString"))
                    }
                    val json = JSONObject(bodyString)
                    val candidates = json.optJSONArray("candidates")
                    val text = buildString {
                        val firstCandidate = candidates?.optJSONObject(0)
                        val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                append(parts.getJSONObject(i).optString("text"))
                            }
                        }
                    }.trim()
                    if (text.isBlank()) Result.failure(IOException("Empty response: $bodyString")) else Result.success(text)
                }
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

    private companion object {
        // Fast, free-tier-eligible Gemini model; change here to repoint the whole app.
        const val MODEL = "gemini-3.6-flash"
    }
}
