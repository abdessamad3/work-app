package com.prayerwakeup.app.conversation

import com.prayerwakeup.app.domain.CallerPersona
import com.prayerwakeup.app.domain.Prayer

/** Arabic prompt/phrase bank for the wake-up caller persona. */
object PersonaPrompts {

    const val END_CALL_TAG = "[END_CALL]"

    fun systemPrompt(prayer: Prayer, persona: CallerPersona): String = """
        أنت متصل صوتي يهاتف مستخدماً مسلماً الآن قبيل دخول أو عند دخول وقت صلاة ${prayer.arabicName}، لإيقاظه ودفعه للنهوض والصلاة.
        أسلوبك: ${persona.styleDescription}.
        تحدث بالعربية الفصحى المبسطة فقط، بجمل قصيرة جداً (سطر أو سطرين كحد أقصى) لأن كل رد سيُقرأ بصوت مرتفع فور وصوله.
        لا تستخدم أي رموز أو تنسيق أو أقواس أو علامات ترقيم غريبة، فقط كلام طبيعي منطوق.
        قواعد المحادثة:
        - تأكد أن المستخدم استيقظ فعلاً، بسؤاله صراحة إن كان قد نهض من الفراش.
        - إن ماطل أو اعتذر أو طلب مزيداً من الوقت، شجعه بلطف أو حزم (حسب أسلوبك) وذكّره بفضل الصلاة في وقتها، دون إطالة.
        - إن أكد أنه استيقظ وسيتوضأ ويصلي، اختم فوراً بدعاء قصير له وعبارة وداع مختصرة.
        - عندما تقرر إنهاء المكالمة لأنه أكد استيقاظه (أو بعد عدة محاولات لا جدوى منها)، أضف حرفياً في نهاية ردك الوسم: $END_CALL_TAG
    """.trimIndent()

    fun openingLine(prayer: Prayer, persona: CallerPersona): String = when (persona) {
        CallerPersona.GENTLE -> "السلام عليكم، حبيبي، حان وقت صلاة ${prayer.arabicName}. استيقظ برفق يا غالي، الوقت ينتظرك."
        CallerPersona.FIRM -> "استيقظ الآن. حان وقت صلاة ${prayer.arabicName}، لا تتأخر، قم وتوضأ فوراً."
        CallerPersona.MOTIVATIONAL -> "يلا، انهض! وقت صلاة ${prayer.arabicName} فرصة عظيمة لا تفوّتها، قم بحماس واغتنمها."
    }

    fun fallbackNudge(attempt: Int, prayer: Prayer): String = when {
        attempt <= 1 -> "لم أسمع رداً، هل أنت مستيقظ؟ الرجاء الرد بصوت واضح."
        attempt == 2 -> "لا تزال صلاة ${prayer.arabicName} تنتظرك، أرجوك انهض الآن وقل لي أنك استيقظت."
        else -> "هذه محاولتي الأخيرة، انهض الآن وصلِّ، لا تضيّع وقت ${prayer.arabicName}."
    }

    /** Used when there is no API key, or the network/API call fails mid-call. */
    fun fallbackReply(): String = "استيقظ الآن وتوضأ وصلِّ، لا تؤخر الصلاة عن وقتها."

    fun closingLine(): String = "بارك الله فيك، صلّ الآن، واجعلها بداية يوم مبارك. مع السلامة."
}
