package com.prayerwakeup.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prayerwakeup.app.domain.CalculationMethod
import com.prayerwakeup.app.domain.CallerPersona
import com.prayerwakeup.app.domain.Madhab
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.PrayerTimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "prayer_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val HAS_LOCATION = booleanPreferencesKey("has_location")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val TIME_ZONE_ID = stringPreferencesKey("time_zone_id")
        val LOCATION_LABEL = stringPreferencesKey("location_label")
        val CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        val MADHAB = stringPreferencesKey("madhab")
        val ENABLED_PRAYERS = stringSetPreferencesKey("enabled_prayers")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val MAX_CALL_MINUTES = intPreferencesKey("max_call_minutes")
        val PERSONA = stringPreferencesKey("persona")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val PRAYER_TIME_SOURCE = stringPreferencesKey("prayer_time_source")
        val MOROCCO_CITY_ID = intPreferencesKey("morocco_city_id")
        val MOROCCO_CITY_LABEL = stringPreferencesKey("morocco_city_label")
    }

    val settingsFlow: Flow<PrayerSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = PrayerSettings()
        PrayerSettings(
            hasLocation = prefs[Keys.HAS_LOCATION] ?: defaults.hasLocation,
            latitude = prefs[Keys.LATITUDE] ?: defaults.latitude,
            longitude = prefs[Keys.LONGITUDE] ?: defaults.longitude,
            timeZoneId = prefs[Keys.TIME_ZONE_ID] ?: defaults.timeZoneId,
            locationLabel = prefs[Keys.LOCATION_LABEL] ?: defaults.locationLabel,
            calculationMethod = prefs[Keys.CALCULATION_METHOD]?.let { runCatching { CalculationMethod.valueOf(it) }.getOrNull() }
                ?: defaults.calculationMethod,
            madhab = prefs[Keys.MADHAB]?.let { runCatching { Madhab.valueOf(it) }.getOrNull() } ?: defaults.madhab,
            enabledPrayers = prefs[Keys.ENABLED_PRAYERS]
                ?.mapNotNull { runCatching { Prayer.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?.ifEmpty { defaults.enabledPrayers }
                ?: defaults.enabledPrayers,
            snoozeMinutes = prefs[Keys.SNOOZE_MINUTES] ?: defaults.snoozeMinutes,
            maxCallMinutes = prefs[Keys.MAX_CALL_MINUTES] ?: defaults.maxCallMinutes,
            persona = prefs[Keys.PERSONA]?.let { runCatching { CallerPersona.valueOf(it) }.getOrNull() } ?: defaults.persona,
            onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
            prayerTimeSource = prefs[Keys.PRAYER_TIME_SOURCE]?.let { runCatching { PrayerTimeSource.valueOf(it) }.getOrNull() }
                ?: defaults.prayerTimeSource,
            moroccoCityId = prefs[Keys.MOROCCO_CITY_ID],
            moroccoCityLabel = prefs[Keys.MOROCCO_CITY_LABEL] ?: defaults.moroccoCityLabel
        )
    }

    suspend fun updateLocation(latitude: Double, longitude: Double, timeZoneId: String, label: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.HAS_LOCATION] = true
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.TIME_ZONE_ID] = timeZoneId
            prefs[Keys.LOCATION_LABEL] = label
        }
    }

    suspend fun updateCalculationMethod(method: CalculationMethod) {
        context.settingsDataStore.edit { it[Keys.CALCULATION_METHOD] = method.name }
    }

    suspend fun updateMadhab(madhab: Madhab) {
        context.settingsDataStore.edit { it[Keys.MADHAB] = madhab.name }
    }

    suspend fun updateEnabledPrayers(prayers: Set<Prayer>) {
        context.settingsDataStore.edit { it[Keys.ENABLED_PRAYERS] = prayers.map { p -> p.name }.toSet() }
    }

    suspend fun updateSnoozeMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.SNOOZE_MINUTES] = minutes }
    }

    suspend fun updateMaxCallMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.MAX_CALL_MINUTES] = minutes }
    }

    suspend fun updatePersona(persona: CallerPersona) {
        context.settingsDataStore.edit { it[Keys.PERSONA] = persona.name }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun updatePrayerTimeSource(source: PrayerTimeSource) {
        context.settingsDataStore.edit { it[Keys.PRAYER_TIME_SOURCE] = source.name }
    }

    suspend fun updateMoroccoCity(cityId: Int, label: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.MOROCCO_CITY_ID] = cityId
            prefs[Keys.MOROCCO_CITY_LABEL] = label
        }
    }
}
