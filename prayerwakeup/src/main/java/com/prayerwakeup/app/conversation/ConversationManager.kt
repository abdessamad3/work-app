package com.prayerwakeup.app.conversation

import com.prayerwakeup.app.domain.CallerPersona
import com.prayerwakeup.app.domain.Prayer
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
 * Drives one wake-up phone call: speak -> listen -> ask Claude how to respond -> speak -> repeat,
 * until the user confirms they're up (LLM emits [END_CALL]) or the turn limit is hit. Falls back
 * to a fixed escalating script if no API key is configured or a request fails, so a call never
 * goes silent even without a working connection.
 */
@Singleton
class ConversationManager @Inject constructor(
    private val tts: ArabicTextToSpeech,
    private val speechRecognizer: ArabicSpeechRecognizer,
    private val claudeClient: ClaudeClient
) {
    suspend fun runCall(
        prayer: Prayer,
        persona: CallerPersona,
        maxTurns: Int,
        isCancelled: () -> Boolean,
        onEvent: suspend (CallEvent) -> Unit
    ) {
        val history = mutableListOf<ConversationTurn>()
        val systemPrompt = PersonaPrompts.systemPrompt(prayer, persona)
        var nextLine = PersonaPrompts.openingLine(prayer, persona)
        var turn = 0
        var ended = false

        while (!ended && turn < maxTurns && !isCancelled()) {
            onEvent(CallEvent.AgentSpeaking(nextLine))
            tts.speak(nextLine)
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
                nextLine = PersonaPrompts.fallbackNudge(turn, prayer)
                continue
            }

            onEvent(CallEvent.UserHeard(heard.text))
            history.add(ConversationTurn("user", heard.text))

            val reply = if (claudeClient.hasApiKey()) {
                claudeClient.sendMessage(systemPrompt, history)
                    .onFailure { onEvent(CallEvent.Debug("فشل الاتصال بـ Claude: ${it.message}")) }
                    .getOrNull()
            } else {
                onEvent(CallEvent.Debug("لا يوجد مفتاح Anthropic API محفوظ"))
                null
            }

            if (reply == null) {
                nextLine = PersonaPrompts.fallbackReply()
                continue
            }

            if (reply.contains(PersonaPrompts.END_CALL_TAG)) {
                val clean = reply.replace(PersonaPrompts.END_CALL_TAG, "").trim()
                val closing = clean.ifBlank { PersonaPrompts.closingLine() }
                onEvent(CallEvent.AgentSpeaking(closing))
                tts.speak(closing)
                history.add(ConversationTurn("assistant", closing))
                ended = true
            } else {
                nextLine = reply
            }
        }

        onEvent(CallEvent.Ended)
    }

    fun stop() {
        tts.stop()
    }
}
