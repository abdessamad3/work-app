package com.prayerwakeup.app.data.settings

import com.prayerwakeup.app.domain.CalculationMethod
import com.prayerwakeup.app.domain.CallerPersona
import com.prayerwakeup.app.domain.Madhab
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.PrayerTimeSource

data class PrayerSettings(
    val hasLocation: Boolean = false,
    val latitude: Double = 21.4225, // defaults: Masjid al-Haram, replaced once the user sets a real location
    val longitude: Double = 39.8262,
    val timeZoneId: String = "Asia/Riyadh",
    val locationLabel: String = "",
    val calculationMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val madhab: Madhab = Madhab.SHAFI,
    val enabledPrayers: Set<Prayer> = Prayer.entries.toSet(),
    val snoozeMinutes: Int = 5,
    val maxCallMinutes: Int = 3,
    val persona: CallerPersona = CallerPersona.FIRM,
    val onboardingComplete: Boolean = false,
    val prayerTimeSource: PrayerTimeSource = PrayerTimeSource.OFFLINE_CALCULATION,
    val moroccoCityId: Int? = null,
    val moroccoCityLabel: String = ""
)
