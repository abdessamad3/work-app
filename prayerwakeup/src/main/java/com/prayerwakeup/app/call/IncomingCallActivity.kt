package com.prayerwakeup.app.call

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerwakeup.app.domain.Prayer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    @Inject lateinit var sessionController: CallSessionController

    private val requestMicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowOverLockScreenFlags()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            MaterialTheme {
                val state by sessionController.uiState.collectAsState()
                CallScreen(
                    state = state,
                    onAnswer = { sessionController.requestAnswer() },
                    onDecline = {
                        sessionController.requestDecline()
                        finish()
                    }
                )
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
private fun CallScreen(state: CallUiState, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B3D2E)) {
        when (state) {
            is CallUiState.Idle -> Box(Modifier.fillMaxSize())
            is CallUiState.Ringing -> RingingContent(state.prayer, onAnswer, onDecline)
            is CallUiState.InCall -> InCallContent(state, onDecline)
            is CallUiState.Ended -> EndedContent(state.prayer)
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
private fun EndedContent(prayer: Prayer) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).background(Color(0xFF0B3D2E)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("انتهت المكالمة", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text("صلاة ${prayer.arabicName} مقبولة بإذن الله", color = Color.White.copy(alpha = 0.7f))
    }
}
