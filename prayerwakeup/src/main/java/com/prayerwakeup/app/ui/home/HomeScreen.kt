package com.prayerwakeup.app.ui.home

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
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.prayerwakeup.app.domain.Prayer
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenQibla: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
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

    // Ticks the countdown on the next-prayer card and keeps "next prayer" itself correct as
    // time passes, without needing a full settings/network refresh every minute.
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(30_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("صلاتي") },
                actions = {
                    IconButton(onClick = onOpenQibla) {
                        Icon(Icons.Filled.Explore, contentDescription = "اتجاه القبلة")
                    }
                    IconButton(onClick = onOpenStatistics) {
                        Icon(Icons.Filled.BarChart, contentDescription = "الإحصائيات")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
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

            LocationBadge(locationLabel = state.locationLabel, sourceLabel = state.sourceLabel, onClick = onOpenSettings)
            Spacer(Modifier.height(8.dp))

            if (state.hijriLabel.isNotBlank()) {
                Text(
                    state.hijriLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(4.dp))
            }
            if (state.isRamadan) {
                Text(
                    "رمضان مبارك",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }

            StatusCard(state = state, now = now, onClick = onOpenSettings)
            Spacer(Modifier.height(12.dp))

            if (state.dailyAthkar.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.dailyAthkar,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
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
                        Text(
                            if (state.nextPrayerIsTomorrow) "الصلاة القادمة (غداً)" else "الصلاة القادمة",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            prayer.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(prayer.arabicName, style = MaterialTheme.typography.headlineMedium)
                        if (time != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(time.format(timeFormatter), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                remainingLabel(now, time),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.testCallNow(prayer) }) {
                            Icon(Icons.Filled.PhoneInTalk, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("اختبار المكالمة الآن")
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            Text("مواقيت اليوم", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(state.todayTimes) { (prayer, time) ->
                    val enabled = state.enabledPrayers.contains(prayer)
                    val isNext = !state.nextPrayerIsTomorrow && prayer == state.nextPrayer
                    val isPast = time.isBefore(now)
                    val prayed = state.prayedToday.contains(prayer)
                    PrayerRow(
                        prayer = prayer,
                        time = time.format(timeFormatter),
                        enabled = enabled,
                        isNext = isNext,
                        isPast = isPast,
                        prayed = prayed,
                        onTogglePrayed = { viewModel.togglePrayed(prayer, prayed) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationBadge(locationLabel: String, sourceLabel: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    locationLabel.ifBlank { "الموقع" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sourceLabel.isNotBlank()) {
                    Text(
                        sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: HomeUiState, now: ZonedDateTime, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (state.allDiagnosticsOk) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row {
                StatusChip(ok = state.canScheduleExactAlarms, label = "التنبيهات الدقيقة")
                Spacer(Modifier.width(14.dp))
                StatusChip(ok = state.batteryOptimizationExempt, label = "البطارية")
                Spacer(Modifier.width(14.dp))
                StatusChip(ok = state.notificationsEnabled, label = "الإشعارات")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                schedulingSummary(state.schedulingStatus, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun StatusChip(ok: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun schedulingSummary(status: com.prayerwakeup.app.data.settings.SchedulingStatus, now: ZonedDateTime): String {
    if (status.lastScheduledAtEpochMillis <= 0L) return "لم تتم جدولة أي تنبيه بعد"
    val scheduledAt = java.time.Instant.ofEpochMilli(status.lastScheduledAtEpochMillis).atZone(now.zone)
    val minutes = Duration.between(scheduledAt, now).toMinutes().coerceAtLeast(0)
    val relative = when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "قبل $minutes د"
        minutes < 60 * 24 -> "قبل ${minutes / 60} س"
        else -> "قبل ${minutes / (60 * 24)} يوم"
    }
    return "آخر جدولة ناجحة: $relative · ${status.scheduledCount} صلاة مجدولة"
}

@Composable
private fun PrayerRow(
    prayer: Prayer,
    time: String,
    enabled: Boolean,
    isNext: Boolean,
    isPast: Boolean,
    prayed: Boolean,
    onTogglePrayed: () -> Unit
) {
    val contentColor = when {
        isNext -> MaterialTheme.colorScheme.primary
        isPast -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isNext) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(prayer.icon(), contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    prayer.arabicName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!enabled) {
                    Text("(متوقف) ", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    time,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onTogglePrayed, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (prayed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (prayed) "تمت الصلاة، اضغط للإلغاء" else "لم تُسجَّل بعد، اضغط لتسجيل الصلاة",
                        tint = if (prayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
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

private fun Prayer.icon(): ImageVector = when (this) {
    Prayer.FAJR -> Icons.Filled.WbTwilight
    Prayer.DHUHR -> Icons.Filled.WbSunny
    Prayer.ASR -> Icons.Filled.Brightness5
    Prayer.MAGHRIB -> Icons.Filled.Brightness4
    Prayer.ISHA -> Icons.Filled.NightsStay
}

private fun remainingLabel(now: ZonedDateTime, target: ZonedDateTime): String {
    val remaining = Duration.between(now, target)
    if (remaining.isNegative) return "الآن"
    val hours = remaining.toHours()
    val minutes = remaining.toMinutes() % 60
    return when {
        hours > 0 -> "متبقٍ: ${hours} س ${minutes} د"
        else -> "متبقٍ: ${minutes} د"
    }
}
