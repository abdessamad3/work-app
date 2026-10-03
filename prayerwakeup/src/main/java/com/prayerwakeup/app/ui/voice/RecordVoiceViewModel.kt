package com.prayerwakeup.app.ui.voice

import androidx.lifecycle.ViewModel
import com.prayerwakeup.app.data.voice.RecordedVoiceRepository
import com.prayerwakeup.app.data.voice.VoiceRecorder
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.ScriptLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val recordingKey: String? = null,
    val playingKey: String? = null
)

@HiltViewModel
class RecordVoiceViewModel @Inject constructor(
    private val repository: RecordedVoiceRepository,
    private val recorder: VoiceRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordVoiceUiState(items = buildItems()))
    val uiState: StateFlow<RecordVoiceUiState> = _uiState.asStateFlow()

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
        _uiState.value = _uiState.value.copy(items = buildItems())
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

    fun play(item: VoiceItem) {
        _uiState.value = _uiState.value.copy(playingKey = item.key)
        recorder.playPreview(item.file) {
            _uiState.value = _uiState.value.copy(playingKey = null)
        }
    }

    fun delete(item: VoiceItem) {
        item.file.delete()
        refresh()
    }

    override fun onCleared() {
        recorder.cancel()
        recorder.stopPreview()
        super.onCleared()
    }
}
