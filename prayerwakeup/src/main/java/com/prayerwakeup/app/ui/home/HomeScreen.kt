package com.prayerwakeup.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.prayerwakeup.app.domain.Prayer
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a") }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestViewModel = rememberUpdatedState(viewModel)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestViewModel.value.refreshNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("صلاتي") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (state.loading) {
                CircularProgressIndicator()
                return@Column
            }

            if (!state.hasLocation) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("لم يتم تحديد موقعك بعد", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("حدد موقعك من الإعدادات لحساب أوقات الصلاة بدقة دون إنترنت.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenSettings) { Text("فتح الإعدادات") }
                    }
                }
                return@Column
            }

            if (!state.canScheduleExactAlarms) {
                WarningCard(
                    message = "التطبيق يحتاج إذن \"التنبيهات الدقيقة\" ليتصل بك في وقت الصلاة بالضبط.",
                    actionLabel = "فتح الإعدادات",
                    onAction = onOpenSettings
                )
                Spacer(Modifier.height(12.dp))
            }

            if (!state.hasApiKey) {
                WarningCard(
                    message = "لم تُضف مفتاح API بعد، لذا ستستخدم المكالمات جملاً ثابتة بدلاً من محادثة ذكية حقيقية.",
                    actionLabel = "إضافة المفتاح",
                    onAction = onOpenSettings
                )
                Spacer(Modifier.height(12.dp))
            }

            state.nextPrayer?.let { prayer ->
                val time = state.nextPrayerTime
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("الصلاة القادمة", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(prayer.arabicName, style = MaterialTheme.typography.headlineMedium)
                        if (time != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(time.format(timeFormatter), style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.testCallNow(prayer) }) {
                            Icon(Icons.Filled.PhoneInTalk, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("اختبار المكالمة الآن")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("مواقيت اليوم", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(state.todayTimes) { (prayer, time) ->
                    val enabled = state.enabledPrayers.contains(prayer)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(prayer.arabicName, style = MaterialTheme.typography.bodyLarge)
                        Row {
                            if (!enabled) {
                                Text("(متوقف) ", color = MaterialTheme.colorScheme.outline)
                            }
                            Text(time.format(timeFormatter), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(message: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(message)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

