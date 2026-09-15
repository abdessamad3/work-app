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
 * Stores the user's Anthropic API key encrypted at rest, on-device only.
 * The key is never bundled with the app or sent anywhere except directly
 * to api.anthropic.com when placing a call.
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

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_ANTHROPIC_API_KEY, null).orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_ANTHROPIC_API_KEY, key).apply()
        _apiKey.value = key
    }

    fun hasApiKey(): Boolean = _apiKey.value.isNotBlank()

    private companion object {
        const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
    }
}
