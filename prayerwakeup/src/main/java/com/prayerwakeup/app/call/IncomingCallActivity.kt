package com.prayerwakeup.app.call

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerwakeup.app.data.settings.PrayerSettings
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.WakeChallenge
import com.prayerwakeup.app.ui.theme.paletteFor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    @Inject lateinit var sessionController: CallSessionController
    @Inject lateinit var settingsRepository: SettingsRepository

    private val requestMicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val requestActivityRecognitionPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowOverLockScreenFlags()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestActivityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        setContent {
            MaterialTheme {
                val state by sessionController.uiState.collectAsState()
                val settings by settingsRepository.settingsFlow.collectAsState(initial = PrayerSettings())
                val backgroundColor = paletteFor(settings.appTheme).primary
                var showChallenge by remember { mutableStateOf(false) }

                // The Answer path never explicitly closes this screen — the service just runs the
                // conversation, marks it Ended, and resets to Idle a moment later. Without this,
                // that reset left the activity showing a blank Idle screen forever instead of
                // returning the user to whatever was in the foreground before the call.
                var hasStartedSession by remember { mutableStateOf(false) }
                LaunchedEffect(state) {
                    if (state !is CallUiState.Idle) {
                        hasStartedSession = true
                    } else if (hasStartedSession) {
                        finish()
                    }
                }

                if (showChallenge) {
                    WakeChallengeScreen(
                        challenge = settings.wakeChallenge,
                        backgroundColor = backgroundColor,
                        onCompleted = {
                            sessionController.requestDecline()
                            finish()
                        },
                        onCancel = { showChallenge = false }
                    )
                } else {
                    CallScreen(
                        state = state,
                        backgroundColor = backgroundColor,
                        onAnswer = { sessionController.requestAnswer() },
                        onDecline = {
                            // A configured challenge means a bare decline is never accepted on
                            // its own — the call only actually ends once the challenge below is
                            // completed, so it can't be dismissed by someone still half-asleep.
                            if (settings.wakeChallenge == WakeChallenge.NONE) {
                                sessionController.requestDecline()
                                finish()
                            } else {
                                showChallenge = true
                            }
                        }
                    )
                }
            }
        }
    }

    private fun setShowOverLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

@Composable
private fun CallScreen(state: CallUiState, backgroundColor: Color, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {
        when (state) {
            is CallUiState.Idle -> Box(Modifier.fillMaxSize())
            is CallUiState.Ringing -> RingingContent(state.prayer, onAnswer, onDecline)
            is CallUiState.InCall -> InCallContent(state, onDecline)
            is CallUiState.Ended -> EndedContent(state.prayer, backgroundColor)
        }
    }
}

@Composable
private fun RingingContent(prayer: Prayer, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("مكالمة واردة", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "حان وقت صلاة ${prayer.arabicName}",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallActionButton(
                icon = Icons.Filled.CallEnd,
                label = "رفض",
                containerColor = Color(0xFFD32F2F),
                onClick = onDecline
            )
            CallActionButton(
                icon = Icons.Filled.Call,
                label = "رد",
                containerColor = Color(0xFF2E7D32),
                onClick = onAnswer
            )
        }
    }
}

@Composable
private fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = containerColor),
            modifier = Modifier.height(72.dp).padding(0.dp)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White)
    }
}

@Composable
private fun InCallContent(state: CallUiState.InCall, onDecline: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            "مكالمة صلاة ${state.prayer.arabicName}",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.listening) "أستمع إليك..." else "يتحدث...",
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.transcript) { line ->
                if (line.speaker == Speaker.SYSTEM) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                        Text(
                            "⚠ ${line.text}",
                            color = Color(0xFFFFC107),
                            fontSize = 12.sp
                        )
                    }
                    return@items
                }
                val isAgent = line.speaker == Speaker.AGENT
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End
                ) {
                    Surface(
                        color = if (isAgent) Color.White.copy(alpha = 0.15f) else Color(0xFF2E7D32),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            line.text,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CallActionButton(
                icon = Icons.Filled.CallEnd,
                label = "إنهاء",
                containerColor = Color(0xFFD32F2F),
                onClick = onDecline
            )
        }
    }
}

@Composable
private fun EndedContent(prayer: Prayer, backgroundColor: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).background(backgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("انتهت المكالمة", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text("صلاة ${prayer.arabicName} مقبولة بإذن الله", color = Color.White.copy(alpha = 0.7f))
    }
}

// --- Wake-up challenges: gate an actual decline behind a small task, so it's harder to
// dismiss the alarm while still mostly asleep than a single reflexive tap. ---

private const val TARGET_STEPS = 20
private const val QURAN_MIN_SECONDS = 90

@Composable
private fun WakeChallengeScreen(
    challenge: WakeChallenge,
    backgroundColor: Color,
    onCompleted: () -> Unit,
    onCancel: () -> Unit
) {
    when (challenge) {
        WakeChallenge.NONE -> LaunchedEffect(Unit) { onCompleted() }
        WakeChallenge.STEPS -> StepsChallengeContent(backgroundColor, onCompleted, onCancel)
        WakeChallenge.MATH -> MathChallengeContent(backgroundColor, onCompleted, onCancel)
        WakeChallenge.QURAN -> QuranChallengeContent(backgroundColor, onCompleted, onCancel)
    }
}

@Composable
private fun ChallengeScaffold(
    title: String,
    backgroundColor: Color,
    onCancel: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            content()
            Spacer(Modifier.height(40.dp))
            TextButton(onClick = onCancel) { Text("رجوع", color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun StepsChallengeContent(backgroundColor: Color, onCompleted: () -> Unit, onCancel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var steps by remember { mutableStateOf(0) }
    var sensorAvailable by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val detector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensorManager == null || detector == null) {
            sensorAvailable = false
            return@DisposableEffect onDispose {}
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                steps += 1
                if (steps >= TARGET_STEPS) onCompleted()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, detector, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    ChallengeScaffold(title = "امشِ 20 خطوة لإيقاف المنبه", backgroundColor = backgroundColor, onCancel = onCancel) {
        if (!sensorAvailable) {
            Text("مستشعر الخطوات غير متوفر على هذا الجهاز.", color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCompleted) { Text("متابعة على أي حال") }
        } else {
            Text("$steps / $TARGET_STEPS", fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { (steps.toFloat() / TARGET_STEPS).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class MathProblem(val a: Int, val b: Int, val isAddition: Boolean, val answer: Int) {
    val displayText: String get() = "$a ${if (isAddition) "+" else "-"} $b = ?"
}

private fun generateMathProblem(): MathProblem {
    val isAddition = Random.nextBoolean()
    val a = Random.nextInt(15, 60)
    val b = Random.nextInt(10, 40)
    val answer = if (isAddition) a + b else a - b
    return MathProblem(a, b, isAddition, answer)
}

@Composable
private fun MathChallengeContent(backgroundColor: Color, onCompleted: () -> Unit, onCancel: () -> Unit) {
    var problem by remember { mutableStateOf(generateMathProblem()) }
    var answer by remember(problem) { mutableStateOf("") }
    var wrongAttempt by remember(problem) { mutableStateOf(false) }

    ChallengeScaffold(title = "حل العملية الحسابية لإيقاف المنبه", backgroundColor = backgroundColor, onCancel = onCancel) {
        Text(problem.displayText, fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it; wrongAttempt = false },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (wrongAttempt) {
            Spacer(Modifier.height(8.dp))
            Text("إجابة خاطئة، حاول مع عملية جديدة", color = Color(0xFFFFCDD2), fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (answer.trim().toIntOrNull() == problem.answer) {
                onCompleted()
            } else {
                wrongAttempt = true
                problem = generateMathProblem()
            }
        }) { Text("تحقق") }
    }
}

@Composable
private fun QuranChallengeContent(backgroundColor: Color, onCompleted: () -> Unit, onCancel: () -> Unit) {
    var remaining by remember { mutableStateOf(QURAN_MIN_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }

    ChallengeScaffold(title = "اتلُ 10 آيات من سورة البقرة لإيقاف المنبه", backgroundColor = backgroundColor, onCancel = onCancel) {
        Text(
            "اتلُ عشر آيات من أول سورة البقرة (الآيات 1 إلى 10) من حفظك أو من مصحفك، ثم اضغط الزر أدناه بعد الانتهاء.",
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(28.dp))
        if (remaining > 0) {
            Text("$remaining ث", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "لا يمكن إيقاف المنبه قبل انتهاء هذه المهلة",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        } else {
            Button(onClick = onCompleted) { Text("انتهيت من التلاوة") }
        }
    }
}
