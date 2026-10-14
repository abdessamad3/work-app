package com.prayerwakeup.app.domain

import android.icu.util.Calendar
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Uses Android's built-in ICU Islamic-calendar support (no extra dependency) rather than a
 * hand-rolled Hijri conversion — ICU's "islamic-umalqura" variant matches the same Umm al-Qura
 * authority already used elsewhere in this app (the offline calculation method, AlAdhan's method
 * 4), so the displayed date stays consistent with that convention.
 */
data class HijriDate(val day: Int, val month: Int, val year: Int) {
    val isRamadan: Boolean get() = month == 9
    val monthName: String get() = MONTH_NAMES.getOrElse(month - 1) { "" }
    val displayLabel: String get() = "$day $monthName $year هـ"

    companion object {
        private val MONTH_NAMES = listOf(
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        )

        fun forDate(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): HijriDate {
            val instant = date.atStartOfDay(zoneId).toInstant()
            val calendar = Calendar.getInstance(ULocale("en_US@calendar=islamic-umalqura"))
            calendar.time = Date.from(instant)
            return HijriDate(
                day = calendar.get(Calendar.DAY_OF_MONTH),
                month = calendar.get(Calendar.MONTH) + 1,
                year = calendar.get(Calendar.YEAR)
            )
        }
    }
}
