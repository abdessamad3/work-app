package com.prayerwakeup.app.call

import com.prayerwakeup.app.domain.Prayer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class Speaker { AGENT, USER }
data class TranscriptLine(val speaker: Speaker, val text: String)

sealed interface CallUiState {
    data object Idle : CallUiState
    data class Ringing(val prayer: Prayer) : CallUiState
    data class InCall(val prayer: Prayer, val transcript: List<TranscriptLine>, val listening: Boolean) : CallUiState
    data class Ended(val prayer: Prayer) : CallUiState
}

sealed interface CallAction {
    data object Answer : CallAction
    data object Decline : CallAction
}

/**
 * Shared state bridging the always-on-top call UI (Activity) and the foreground service
 * that actually runs the conversation. Both are Hilt-injected with this same singleton.
 */
@Singleton
class CallSessionController @Inject constructor() {
    private val _uiState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val _actions = MutableSharedFlow<CallAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<CallAction> = _actions.asSharedFlow()

    fun startRinging(prayer: Prayer) {
        _uiState.value = CallUiState.Ringing(prayer)
    }

    fun updateInCall(prayer: Prayer, transcript: List<TranscriptLine>, listening: Boolean) {
        _uiState.value = CallUiState.InCall(prayer, transcript, listening)
    }

    fun endCall(prayer: Prayer) {
        _uiState.value = CallUiState.Ended(prayer)
    }

    fun reset() {
        _uiState.value = CallUiState.Idle
    }

    fun requestAnswer() {
        _actions.tryEmit(CallAction.Answer)
    }

    fun requestDecline() {
        _actions.tryEmit(CallAction.Decline)
    }
}
