package com.prayerwakeup.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's API keys encrypted at rest, on-device only. Each key is never bundled
 * with the app or sent anywhere except directly to its own provider's API when placing a call
 * (Gemini's key to generativelanguage.googleapis.com, ElevenLabs' key to api.elevenlabs.io).
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "prayer_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _geminiApiKey = MutableStateFlow(prefs.getString(KEY_GEMINI_API_KEY, null).orEmpty())
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
        _geminiApiKey.value = key
    }

    fun hasGeminiApiKey(): Boolean = _geminiApiKey.value.isNotBlank()

    private val _elevenLabsApiKey = MutableStateFlow(prefs.getString(KEY_ELEVENLABS_API_KEY, null).orEmpty())
    val elevenLabsApiKey: StateFlow<String> = _elevenLabsApiKey.asStateFlow()

    fun setElevenLabsApiKey(key: String) {
        prefs.edit().putString(KEY_ELEVENLABS_API_KEY, key).apply()
        _elevenLabsApiKey.value = key
    }

    fun hasElevenLabsApiKey(): Boolean = _elevenLabsApiKey.value.isNotBlank()

    private companion object {
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
    }
}
