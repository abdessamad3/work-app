package com.prayerwakeup.app.domain

enum class WakeChallenge(val displayName: String) {
    NONE("بدون تحدي (رفض مباشر)"),
    STEPS("المشي 20 خطوة"),
    MATH("حل عملية حسابية"),
    QURAN("تلاوة 10 آيات من سورة البقرة")
}
