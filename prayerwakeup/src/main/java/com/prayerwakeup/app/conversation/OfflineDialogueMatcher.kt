package com.prayerwakeup.app.conversation

import com.prayerwakeup.app.domain.ScriptLine

/**
 * Picks which fixed script category best matches what the speech recognizer heard, for calls
 * that have no working Gemini reply (no key, or the request failed). Simple keyword matching
 * rather than real understanding — good enough to separate "I'm up" from "give me a minute" from
 * everything else, without needing a network connection.
 */
object OfflineDialogueMatcher {
    private val awakeKeywords = listOf(
        "استيقظت", "صحيت", "قمت", "صاحي", "مستيقظ", "نعم", "حاضر", "تمام", "واقف", "قايم", "مزيان"
    )
    private val moreTimeKeywords = listOf(
        "دقيقة", "دقيقتين", "شوي", "شوية", "لحظة", "بعد", "نعسان", "تعبان", "لسا", "خمس"
    )

    fun classify(heardText: String): ScriptLine {
        val normalized = heardText.trim()
        return when {
            awakeKeywords.any { normalized.contains(it) } -> ScriptLine.CLOSING
            moreTimeKeywords.any { normalized.contains(it) } -> ScriptLine.MORE_TIME
            else -> ScriptLine.NUDGE
        }
    }
}
