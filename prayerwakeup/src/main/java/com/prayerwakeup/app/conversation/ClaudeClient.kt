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

/** Minimal client for the Anthropic Messages API, used to drive the live wake-up conversation. */
@Singleton
class ClaudeClient @Inject constructor(
    private val keyStore: SecureKeyStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(): Boolean = keyStore.hasApiKey()

    suspend fun sendMessage(systemPrompt: String, history: List<ConversationTurn>): Result<String> =
        withContext(Dispatchers.IO) {
            val apiKey = keyStore.apiKey.value
            if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No Anthropic API key configured"))

            val messages = JSONArray().apply {
                history.forEach { turn -> put(JSONObject().put("role", turn.role).put("content", turn.text)) }
            }
            val requestJson = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", 300)
                put("system", systemPrompt)
                put("messages", messages)
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Anthropic API error ${response.code}: $bodyString"))
                    }
                    val json = JSONObject(bodyString)
                    val contentArray = json.optJSONArray("content")
                    val text = buildString {
                        if (contentArray != null) {
                            for (i in 0 until contentArray.length()) {
                                val block = contentArray.getJSONObject(i)
                                if (block.optString("type") == "text") append(block.optString("text"))
                            }
                        }
                    }.trim()
                    if (text.isBlank()) Result.failure(IOException("Empty response")) else Result.success(text)
                }
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

    private companion object {
        // Latest Claude model at time of writing; change here to repoint the whole app.
        const val MODEL = "claude-sonnet-5"
    }
}
