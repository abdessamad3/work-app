package com.prayerwakeup.app.domain

enum class PrayerTimeSource(val displayName: String) {
    OFFLINE_CALCULATION("حساب فلكي (يعمل دون إنترنت)"),
    MOROCCO_HABOUS("مواقيت الحبوس الرسمية (المغرب، تتطلب إنترنت)"),
    MAWAQIT_MOSQUE("مواقيت مسجد محدد (Mawaqit، تتطلب إنترنت)"),
    ALADHAN("خدمة AlAdhan العالمية (تتطلب إنترنت)")
}
