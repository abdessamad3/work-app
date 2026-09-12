package com.coffer.app.data.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val seenKey = booleanPreferencesKey("has_seen_onboarding")

    val hasSeenOnboarding = context.onboardingDataStore.data.map { it[seenKey] ?: false }

    suspend fun markSeen() {
        context.onboardingDataStore.edit { it[seenKey] = true }
    }
}
