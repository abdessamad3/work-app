package com.prayerwakeup.app.domain

import java.time.LocalDate

/** A small bundled, offline list — picks one phrase per calendar day, no network involved. */
object DailyAthkar {
    private val PHRASES = listOf(
        "سبحان الله وبحمده، سبحان الله العظيم",
        "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير",
        "اللهم أعني على ذكرك وشكرك وحسن عبادتك",
        "حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم",
        "رب اشرح لي صدري ويسر لي أمري",
        "اللهم إني أسألك العفو والعافية في الدنيا والآخرة",
        "لا حول ولا قوة إلا بالله",
        "اللهم صلِّ وسلم على نبينا محمد",
        "ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار",
        "أستغفر الله العظيم وأتوب إليه",
        "اللهم إنك عفو تحب العفو فاعف عني",
        "الحمد لله رب العالمين",
        "اللهم اجعلني من التوابين واجعلني من المتطهرين",
        "رضيت بالله رباً وبالإسلام ديناً وبمحمد صلى الله عليه وسلم نبياً",
        "اللهم بارك لنا فيما رزقتنا"
    )

    fun forDate(date: LocalDate): String {
        val index = (date.toEpochDay() % PHRASES.size).toInt().let { if (it < 0) it + PHRASES.size else it }
        return PHRASES[index]
    }
}
