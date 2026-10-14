package com.prayerwakeup.app.domain

/**
 * Fajr/Isha twilight angles (degrees below the horizon) used by well-known prayer
 * calculation authorities. Where an authority defines Isha as a fixed number of
 * minutes after Maghrib instead of a twilight angle, [ishaAngle] is null and
 * [ishaMinutesAfterMaghrib] is set instead.
 */
enum class CalculationMethod(
    val displayName: String,
    val fajrAngle: Double,
    val ishaAngle: Double?,
    val ishaMinutesAfterMaghrib: Int?
) {
    MUSLIM_WORLD_LEAGUE("رابطة العالم الإسلامي", 18.0, 17.0, null),
    ISNA("الجمعية الإسلامية لأمريكا الشمالية", 15.0, 15.0, null),
    EGYPTIAN("الهيئة المصرية العامة للمساحة", 19.5, 17.5, null),
    UMM_AL_QURA("أم القرى (مكة المكرمة)", 18.5, null, 90),
    KARACHI("جامعة العلوم الإسلامية - كراتشي", 18.0, 18.0, null),
    MOROCCO("المغرب (وزارة الأوقاف والشؤون الإسلامية)", 19.0, 17.0, null);
}

enum class Madhab(val displayName: String, val asrShadowFactor: Double) {
    SHAFI("شافعي / مالكي / حنبلي", 1.0),
    HANAFI("حنفي", 2.0)
}
