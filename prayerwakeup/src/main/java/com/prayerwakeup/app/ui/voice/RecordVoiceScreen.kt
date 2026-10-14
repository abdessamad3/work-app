package com.prayerwakeup.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prayerwakeup.app.ui.theme.ZelligeBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordVoiceScreen(onBack: () -> Unit, viewModel: RecordVoiceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
    }

    fun withMicPermission(action: () -> Unit) {
        val hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            action()
        } else {
            pendingAction = action
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(Modifier.fillMaxSize()) {
    ZelligeBackground(
        modifier = Modifier.fillMaxSize(),
        baseColor = MaterialTheme.colorScheme.primary,
        patternColor = MaterialTheme.colorScheme.secondary
    )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("صوتك المسجَّل") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "سجّل صوتك بنفسك لهذه الجمل، وسيستخدمها التطبيق أثناء المكالمة بدل الصوت الاصطناعي " +
                        "كلما لم يكن هناك اتصال بالإنترنت أو مفتاح API. أي جملة لم تُسجَّل ستُقرأ بالصوت الافتراضي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(12.dp))
            }

            items(state.items, key = { it.key }) { item ->
                VoiceItemCard(
                    label = item.label,
                    sampleText = item.sampleText,
                    hasRecording = item.hasRecording,
                    isRecording = state.recordingKey == item.key,
                    isPlaying = state.playingKey == item.key,
                    recordingInProgress = state.recordingKey != null,
                    onStartRecording = { withMicPermission { viewModel.startRecording(item) } },
                    onStopRecording = { viewModel.stopRecording(item) },
                    onPlay = { viewModel.play(item.file, item.key) },
                    onDelete = { viewModel.delete(item) }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (state.unmatchedPhrases.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text("عبارات لم نفهمها", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "هذا ما قاله المستخدم في مكالمات سابقة ولم يكن للتطبيق رد عليه. سجّل رداً " +
                            "مناسباً، وستُستخدم عبارة التفعيل (قابلة للتعديل) لمطابقة ما يُسمع في المكالمات القادمة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(10.dp))
                }
                items(state.unmatchedPhrases, key = { "unmatched_$it" }) { phrase ->
                    UnmatchedPhraseCard(
                        phrase = phrase,
                        key = viewModel.unmatchedKey(phrase),
                        isRecording = state.recordingKey == viewModel.unmatchedKey(phrase),
                        recordingInProgress = state.recordingKey != null,
                        onStartRecording = { withMicPermission { viewModel.startRecordingForUnmatched(phrase) } },
                        onStopRecording = { trigger -> viewModel.stopRecordingForUnmatched(phrase, trigger) },
                        onDismiss = { viewModel.dismissUnmatched(phrase) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (state.customResponses.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text("ردودك المخصصة", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "الردود التي علّمتها للتطبيق حتى الآن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(10.dp))
                }
                items(state.customResponses, key = { "custom_${it.id}" }) { response ->
                    val key = viewModel.customKey(response)
                    val hasRecording = remember(response.id, state.recordingVersion) {
                        viewModel.customResponseHasRecording(response)
                    }
                    VoiceItemCard(
                        label = response.triggerPhrase,
                        sampleText = "يُستخدم عندما يُسمع شيء قريب من: \"${response.triggerPhrase}\"",
                        hasRecording = hasRecording,
                        isRecording = state.recordingKey == key,
                        isPlaying = state.playingKey == key,
                        recordingInProgress = state.recordingKey != null,
                        onStartRecording = { withMicPermission { viewModel.startRecordingForCustom(response) } },
                        onStopRecording = { viewModel.stopRecordingForCustom(response) },
                        onPlay = { viewModel.play(viewModel.audioFileFor(response), key) },
                        onDelete = { viewModel.deleteCustomResponse(response) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    }
}

@Composable
private fun VoiceItemCard(
    label: String,
    sampleText: String,
    hasRecording: Boolean,
    isRecording: Boolean,
    isPlaying: Boolean,
    recordingInProgress: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                sampleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (hasRecording) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (hasRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isRecording) "جارٍ التسجيل..." else if (hasRecording) "تم التسجيل" else "لم يُسجَّل بعد",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRecording) {
                    OutlinedButton(onClick = onStopRecording) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إيقاف وحفظ")
                    }
                } else {
                    OutlinedButton(onClick = onStartRecording, enabled = !recordingInProgress) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasRecording) "إعادة التسجيل" else "تسجيل")
                    }
                    if (hasRecording) {
                        TextButton(onClick = onPlay, enabled = !isPlaying) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPlaying) "تشغيل..." else "تشغيل")
                        }
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حذف")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnmatchedPhraseCard(
    phrase: String,
    key: String,
    isRecording: Boolean,
    recordingInProgress: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: (triggerText: String) -> Unit,
    onDismiss: () -> Unit
) {
    var triggerText by remember(key) { mutableStateOf(phrase) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("\"$phrase\"", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = triggerText,
                onValueChange = { triggerText = it },
                label = { Text("عبارة التفعيل (يمكنك اختصارها)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRecording) {
                    OutlinedButton(onClick = { onStopRecording(triggerText) }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إيقاف وحفظ الرد")
                    }
                } else {
                    OutlinedButton(onClick = onStartRecording, enabled = !recordingInProgress) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تسجيل رد")
                    }
                    TextButton(onClick = onDismiss, enabled = !recordingInProgress) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تجاهل")
                    }
                }
            }
        }
    }
}
