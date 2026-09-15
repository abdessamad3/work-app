package com.prayerwakeup.app.domain

enum class CallerPersona(val displayName: String, val styleDescription: String) {
    GENTLE(
        "لطيف",
        "دافئ وحنون، كأنه أحد الوالدين يوقظك برفق"
    ),
    FIRM(
        "حازم",
        "مباشر وحازم، لا يقبل الأعذار ويصر عليك حتى تقوم"
    ),
    MOTIVATIONAL(
        "محفّز",
        "متحمس ومُلهم، يذكّرك بأجر الصلاة وقت الفجر"
    )
}
