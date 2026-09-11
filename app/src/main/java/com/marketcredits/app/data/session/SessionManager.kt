package com.marketcredits.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Tracks which profile is currently "logged in", so screens can act as that user. */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val currentUserIdKey = intPreferencesKey("current_user_id")

    val currentUserId = context.sessionDataStore.data.map { prefs ->
        prefs[currentUserIdKey]?.takeIf { it != NO_USER }
    }

    suspend fun setCurrentUser(userId: Int) {
        context.sessionDataStore.edit { prefs -> prefs[currentUserIdKey] = userId }
    }

    suspend fun clearCurrentUser() {
        context.sessionDataStore.edit { prefs -> prefs[currentUserIdKey] = NO_USER }
    }

    private companion object {
        const val NO_USER = -1
    }
}
