package com.prayerwakeup.app.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prayerwakeup.app.data.voice.CustomOfflineResponseRepository
import com.prayerwakeup.app.data.voice.CustomResponse
import com.prayerwakeup.app.data.voice.RecordedVoiceRepository
import com.prayerwakeup.app.data.voice.VoiceRecorder
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.ScriptLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class VoiceItem(
    val key: String,
    val label: String,
    val sampleText: String,
    val file: File,
    val hasRecording: Boolean
)

data class RecordVoiceUiState(
    val items: List<VoiceItem> = emptyList(),
    val unmatchedPhrases: List<String> = emptyList(),
    val customResponses: List<CustomResponse> = emptyList(),
    val recordingKey: String? = null,
    val playingKey: String? = null,
    // Bumped after any record/delete so the screen re-checks file existence for custom
    // responses, whose "has a recording" state isn't itself part of the stored metadata.
    val recordingVersion: Int = 0
)

@HiltViewModel
class RecordVoiceViewModel @Inject constructor(
    private val repository: RecordedVoiceRepository,
    private val customRepository: CustomOfflineResponseRepository,
    private val recorder: VoiceRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordVoiceUiState(items = buildItems()))
    val uiState: StateFlow<RecordVoiceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            customRepository.unmatchedPhrasesFlow.collect { phrases ->
                _uiState.value = _uiState.value.copy(unmatchedPhrases = phrases)
            }
        }
        viewModelScope.launch {
            customRepository.customResponsesFlow.collect { responses ->
                _uiState.value = _uiState.value.copy(customResponses = responses)
            }
        }
    }

    private fun buildItems(): List<VoiceItem> {
        val scriptItems = ScriptLine.entries.map { line ->
            VoiceItem(
                key = line.name,
                label = line.label,
                sampleText = line.sampleText,
                file = repository.fileFor(line),
                hasRecording = repository.hasRecording(line)
            )
        }
        val prayerItems = Prayer.entries.map { prayer ->
            VoiceItem(
                key = "PRAYER_${prayer.name}",
                label = "اسم صلاة ${prayer.arabicName}",
                sampleText = prayer.arabicName,
                file = repository.fileForPrayerName(prayer),
                hasRecording = repository.hasPrayerName(prayer)
            )
        }
        return scriptItems + prayerItems
    }

    private fun refresh() {
        _uiState.value = _uiState.value.copy(items = buildItems(), recordingVersion = _uiState.value.recordingVersion + 1)
    }

    fun startRecording(item: VoiceItem) {
        recorder.stopPreview()
        if (recorder.start()) {
            _uiState.value = _uiState.value.copy(recordingKey = item.key, playingKey = null)
        }
    }

    fun stopRecording(item: VoiceItem) {
        recorder.stopAndSave(item.file)
        _uiState.value = _uiState.value.copy(recordingKey = null)
        refresh()
    }

    fun cancelRecording() {
        recorder.cancel()
        _uiState.value = _uiState.value.copy(recordingKey = null)
    }

    fun play(file: File, key: String) {
        _uiState.value = _uiState.value.copy(playingKey = key)
        recorder.playPreview(file) {
            _uiState.value = _uiState.value.copy(playingKey = null)
        }
    }

    fun delete(item: VoiceItem) {
        item.file.delete()
        refresh()
    }

    // --- Unmatched phrases: a previous call heard this and had no reply for it at all. ---

    fun startRecordingForUnmatched(phrase: String) {
        recorder.stopPreview()
        if (recorder.start()) {
            _uiState.value = _uiState.value.copy(recordingKey = unmatchedKey(phrase), playingKey = null)
        }
    }

    fun stopRecordingForUnmatched(originalPhrase: String, triggerText: String) {
        viewModelScope.launch {
            val trigger = triggerText.trim().ifBlank { originalPhrase }
            val response = customRepository.createCustomResponse(originalPhrase, trigger)
            recorder.stopAndSave(customRepository.audioFileFor(response.id))
            _uiState.value = _uiState.value.copy(
                recordingKey = null,
                recordingVersion = _uiState.value.recordingVersion + 1
            )
        }
    }

    fun dismissUnmatched(phrase: String) {
        viewModelScope.launch { customRepository.dismissUnmatched(phrase) }
    }

    // --- Custom responses: already-recorded answers the user taught for an unmatched phrase. ---

    fun startRecordingForCustom(response: CustomResponse) {
        recorder.stopPreview()
        if (recorder.start()) {
            _uiState.value = _uiState.value.copy(recordingKey = customKey(response), playingKey = null)
        }
    }

    fun stopRecordingForCustom(response: CustomResponse) {
        recorder.stopAndSave(customRepository.audioFileFor(response.id))
        _uiState.value = _uiState.value.copy(
            recordingKey = null,
            recordingVersion = _uiState.value.recordingVersion + 1
        )
    }

    fun deleteCustomResponse(response: CustomResponse) {
        viewModelScope.launch {
            customRepository.deleteCustomResponse(response.id)
            _uiState.value = _uiState.value.copy(recordingVersion = _uiState.value.recordingVersion + 1)
        }
    }

    fun unmatchedKey(phrase: String): String = "UNMATCHED_$phrase"
    fun customKey(response: CustomResponse): String = "CUSTOM_${response.id}"
    fun audioFileFor(response: CustomResponse): File = customRepository.audioFileFor(response.id)
    fun customResponseHasRecording(response: CustomResponse): Boolean = audioFileFor(response).exists()

    override fun onCleared() {
        recorder.cancel()
        recorder.stopPreview()
        super.onCleared()
    }
}
