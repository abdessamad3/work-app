package com.prayerwakeup.app.data.voice

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A user-recorded reply for phrases the fixed offline script doesn't recognize. */
data class CustomResponse(
    val id: String,
    val triggerPhrase: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

private val Context.customOfflineDataStore by preferencesDataStore(name = "custom_offline_responses")

/**
 * Tracks what the offline call engine heard but had no reply for, and the custom recorded
 * responses the user has since added to cover them — the "teach it" loop: a call logs an
 * unmatched phrase, the user records an answer for it from Settings, and future calls that hear
 * something similar play that recording instead of the generic nudge.
 */
@Singleton
class CustomOfflineResponseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val UNMATCHED_JSON = stringPreferencesKey("unmatched_json")
        val CUSTOM_JSON = stringPreferencesKey("custom_responses_json")
    }

    private val audioDirectory: File
        get() = File(context.filesDir, "recorded_voice").apply { mkdirs() }

    fun audioFileFor(id: String): File = File(audioDirectory, "custom_$id.m4a")

    val unmatchedPhrasesFlow: Flow<List<String>> = context.customOfflineDataStore.data.map { prefs ->
        parseStringList(prefs[Keys.UNMATCHED_JSON] ?: "[]")
    }

    val customResponsesFlow: Flow<List<CustomResponse>> = context.customOfflineDataStore.data.map { prefs ->
        parseCustomResponses(prefs[Keys.CUSTOM_JSON] ?: "[]")
    }

    /** Called whenever the offline matcher couldn't classify what it heard against anything. */
    suspend fun recordUnmatched(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        context.customOfflineDataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.UNMATCHED_JSON] ?: "[]").toMutableList()
            current.removeAll { it == normalized }
            current.add(0, normalized)
            prefs[Keys.UNMATCHED_JSON] = serializeStringList(current.take(MAX_UNMATCHED))
        }
    }

    suspend fun dismissUnmatched(text: String) {
        context.customOfflineDataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.UNMATCHED_JSON] ?: "[]")
            prefs[Keys.UNMATCHED_JSON] = serializeStringList(current.filterNot { it == text })
        }
    }

    /** Creates the entry and clears [originalPhrase] from the unmatched queue in one edit. */
    suspend fun createCustomResponse(originalPhrase: String, triggerPhrase: String): CustomResponse {
        val response = CustomResponse(id = UUID.randomUUID().toString(), triggerPhrase = triggerPhrase.trim())
        context.customOfflineDataStore.edit { prefs ->
            val responses = parseCustomResponses(prefs[Keys.CUSTOM_JSON] ?: "[]").toMutableList()
            responses.add(response)
            prefs[Keys.CUSTOM_JSON] = serializeCustomResponses(responses)

            val unmatched = parseStringList(prefs[Keys.UNMATCHED_JSON] ?: "[]")
            prefs[Keys.UNMATCHED_JSON] = serializeStringList(unmatched.filterNot { it == originalPhrase })
        }
        return response
    }

    suspend fun deleteCustomResponse(id: String) {
        audioFileFor(id).delete()
        context.customOfflineDataStore.edit { prefs ->
            val responses = parseCustomResponses(prefs[Keys.CUSTOM_JSON] ?: "[]")
            prefs[Keys.CUSTOM_JSON] = serializeCustomResponses(responses.filterNot { it.id == id })
        }
    }

    private fun parseStringList(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())

    private fun serializeStringList(values: List<String>): String {
        val array = JSONArray()
        values.forEach { array.put(it) }
        return array.toString()
    }

    private fun parseCustomResponses(json: String): List<CustomResponse> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CustomResponse(
                id = id,
                triggerPhrase = obj.optString("triggerPhrase"),
                createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
            )
        }
    }.getOrDefault(emptyList())

    private fun serializeCustomResponses(responses: List<CustomResponse>): String {
        val array = JSONArray()
        responses.forEach { response ->
            val obj = JSONObject()
            obj.put("id", response.id)
            obj.put("triggerPhrase", response.triggerPhrase)
            obj.put("createdAtMillis", response.createdAtMillis)
            array.put(obj)
        }
        return array.toString()
    }

    private companion object {
        const val MAX_UNMATCHED = 20
    }
}
