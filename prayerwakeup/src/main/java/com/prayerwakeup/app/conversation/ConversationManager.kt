package com.prayerwakeup.app.conversation

import com.prayerwakeup.app.data.voice.RecordedVoiceRepository
import com.prayerwakeup.app.domain.CallerPersona
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.ScriptLine
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CallEvent {
    data class AgentSpeaking(val text: String) : CallEvent
    data class UserHeard(val text: String) : CallEvent
    data class Debug(val text: String) : CallEvent
    data object Listening : CallEvent
    data object Ended : CallEvent
}

/**
 * Drives one wake-up phone call: speak -> listen -> ask Gemini how to respond -> speak -> repeat,
 * until the user confirms they're up (LLM emits [END_CALL]) or the turn limit is hit. Falls back
 * to a fixed escalating script if no API key is configured or a request fails, so a call never
 * goes silent even without a working connection.
 */
@Singleton
class ConversationManager @Inject constructor(
    private val tts: ArabicTextToSpeech,
    private val speechRecognizer: ArabicSpeechRecognizer,
    private val geminiClient: GeminiClient,
    private val elevenLabsClient: ElevenLabsClient,
    private val recordedVoiceRepository: RecordedVoiceRepository,
    private val recordedVoicePlayer: RecordedVoicePlayer
) {
    suspend fun runCall(
        prayer: Prayer,
        persona: CallerPersona,
        maxTurns: Int,
        elevenLabsVoiceId: String?,
        isCancelled: () -> Boolean,
        onEvent: suspend (CallEvent) -> Unit
    ) {
        val history = mutableListOf<ConversationTurn>()
        val systemPrompt = PersonaPrompts.systemPrompt(prayer, persona)
        var nextLine = PersonaPrompts.openingLine(prayer, persona)
        var nextCategory: ScriptLine? = ScriptLine.OPENING
        var turn = 0
        var ended = false

        suspend fun speak(text: String) {
            if (!elevenLabsVoiceId.isNullOrBlank() && elevenLabsClient.hasApiKey()) {
                val result = elevenLabsClient.speak(text, elevenLabsVoiceId)
                if (result.isSuccess) return
                onEvent(CallEvent.Debug("فشل صوت ElevenLabs، تم الرجوع للصوت الافتراضي: ${result.exceptionOrNull()?.message}"))
            }
            tts.speak(text)
        }

        // Prefers the user's own recorded voice for the fixed offline script lines (opening,
        // nudges, "need more time", closing) over synthesized speech, falling back to TTS/
        // ElevenLabs whenever that particular line hasn't been recorded. Never applies to live
        // Gemini-generated replies (category is null for those) since there's no matching clip.
        suspend fun speakLine(category: ScriptLine?, text: String) {
            val recording = category?.let(recordedVoiceRepository::fileFor)?.takeIf { it.exists() }
            if (recording == null) {
                speak(text)
                return
            }
            recordedVoicePlayer.play(recording)
            if (category == ScriptLine.OPENING) {
                recordedVoiceRepository.fileForPrayerName(prayer).takeIf { it.exists() }
                    ?.let { recordedVoicePlayer.play(it) }
            }
        }

        while (!ended && turn < maxTurns && !isCancelled()) {
            onEvent(CallEvent.AgentSpeaking(nextLine))
            speakLine(nextCategory, nextLine)
            history.add(ConversationTurn("assistant", nextLine))
            if (isCancelled()) break

            // TTS's onDone can fire slightly before the audio hardware finishes draining,
            // so start listening a beat later instead of risking the recognizer catching
            // the tail of our own voice.
            delay(500)
            onEvent(CallEvent.Listening)
            val heard = speechRecognizer.listenOnce()
            turn++
            if (isCancelled()) break

            if (heard.text.isNullOrBlank()) {
                heard.failureReason?.let { onEvent(CallEvent.Debug(it)) }
                nextCategory = ScriptLine.NUDGE
                nextLine = PersonaPrompts.fallbackNudge(turn, prayer)
                continue
            }

            onEvent(CallEvent.UserHeard(heard.text))
            history.add(ConversationTurn("user", heard.text))

            val reply = if (geminiClient.hasApiKey()) {
                geminiClient.sendMessage(systemPrompt, history)
                    .onFailure { onEvent(CallEvent.Debug("فشل الاتصال بـ Gemini: ${it.message}")) }
                    .getOrNull()
            } else {
                onEvent(CallEvent.Debug("لا يوجد مفتاح Gemini API محفوظ"))
                null
            }

            if (reply == null) {
                // No cloud reply available — classify what was heard against the offline script
                // categories (and the matching recorded clip, if any) instead of one flat line.
                val category = OfflineDialogueMatcher.classify(heard.text)
                val text = PersonaPrompts.offlineLine(category, prayer)
                if (category == ScriptLine.CLOSING) {
                    onEvent(CallEvent.AgentSpeaking(text))
                    speakLine(category, text)
                    history.add(ConversationTurn("assistant", text))
                    ended = true
                } else {
                    nextCategory = category
                    nextLine = text
                }
                continue
            }

            if (reply.contains(PersonaPrompts.END_CALL_TAG)) {
                val clean = reply.replace(PersonaPrompts.END_CALL_TAG, "").trim()
                val closing = clean.ifBlank { PersonaPrompts.closingLine() }
                onEvent(CallEvent.AgentSpeaking(closing))
                speak(closing)
                history.add(ConversationTurn("assistant", closing))
                ended = true
            } else {
                nextCategory = null
                nextLine = reply
            }
        }

        onEvent(CallEvent.Ended)
    }

    fun stop() {
        tts.stop()
        recordedVoicePlayer.stop()
    }
}
