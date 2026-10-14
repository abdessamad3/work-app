package com.prayerwakeup.app.conversation

import com.prayerwakeup.app.data.voice.CustomResponse
import com.prayerwakeup.app.domain.ScriptLine

/** What the offline matcher decided to do with one thing the user said. */
sealed interface OfflineReply {
    data class Custom(val response: CustomResponse) : OfflineReply
    data class Fixed(val category: ScriptLine) : OfflineReply
    /** Nothing — not a custom trigger, not an awake/more-time keyword — matched at all. */
    data class Unmatched(val heardText: String) : OfflineReply
}

/**
 * Picks which fixed script category (or user-recorded custom response) best matches what the
 * speech recognizer heard, for calls that have no working Gemini reply (no key, or the request
 * failed). Simple keyword/substring matching rather than real understanding — good enough to
 * separate "I'm up" from "give me a minute" from everything else, without needing a network
 * connection. Phrases that match nothing fall through as [OfflineReply.Unmatched] so the caller
 * can offer the user a chance to teach it a response for next time.
 */
object OfflineDialogueMatcher {
    private val awakeKeywords = listOf(
        "استيقظت", "صحيت", "قمت", "صاحي", "مستيقظ", "نعم", "حاضر", "تمام", "واقف", "قايم", "مزيان"
    )
    private val moreTimeKeywords = listOf(
        "دقيقة", "دقيقتين", "شوي", "شوية", "لحظة", "بعد", "نعسان", "تعبان", "لسا", "خمس"
    )

    fun classify(heardText: String, customResponses: List<CustomResponse> = emptyList()): OfflineReply {
        val normalized = heardText.trim()

        val customMatch = customResponses.firstOrNull { response ->
            val trigger = response.triggerPhrase.trim()
            trigger.isNotEmpty() && (normalized.contains(trigger) || trigger.contains(normalized))
        }
        if (customMatch != null) return OfflineReply.Custom(customMatch)

        return when {
            awakeKeywords.any { normalized.contains(it) } -> OfflineReply.Fixed(ScriptLine.CLOSING)
            moreTimeKeywords.any { normalized.contains(it) } -> OfflineReply.Fixed(ScriptLine.MORE_TIME)
            else -> OfflineReply.Unmatched(normalized)
        }
    }
}
