package com.prayerwakeup.app.domain

/**
 * The fixed set of lines the offline call engine needs, in the user's own recorded voice instead
 * of synthesized speech. Deliberately not persona-specific — the user phrases these however they
 * like when recording them, so one recording per category covers every persona.
 */
enum class ScriptLine(val label: String, val sampleText: String) {
    OPENING(
        label = "بداية المكالمة",
        sampleText = "حان وقت الصلاة، استيقظ الآن."
    ),
    NUDGE(
        label = "لم أسمعك / هل أنت مستيقظ؟",
        sampleText = "لم أفهم ما قلته، هل أنت مستيقظ؟ أخبرني من فضلك."
    ),
    MORE_TIME(
        label = "عندما تطلب مزيداً من الوقت",
        sampleText = "لا تزال الصلاة تنتظرك، أرجوك انهض الآن ولا تؤجل."
    ),
    CLOSING(
        label = "عندما تؤكد أنك استيقظت",
        sampleText = "بارك الله فيك، قم وصلِّ الآن. مع السلامة."
    )
}
