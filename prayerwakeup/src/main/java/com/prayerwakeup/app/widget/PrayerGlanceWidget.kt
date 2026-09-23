package com.prayerwakeup.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.prayerwakeup.app.MainActivity
import com.prayerwakeup.app.data.PrayerTimesResolver
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.Prayer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Glance widget instances aren't created through Hilt's normal Android entry points, so
// dependencies are pulled from the app's Hilt graph manually via EntryPointAccessors instead of
// constructor injection.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerWidgetEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun timesResolver(): PrayerTimesResolver
}

object PrayerGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, PrayerWidgetEntryPoint::class.java)
        val settingsRepository = entryPoint.settingsRepository()
        val timesResolver = entryPoint.timesResolver()
        val settings = settingsRepository.settingsFlow.first()
        val formatter = DateTimeFormatter.ofPattern("hh:mm a")

        if (!settings.hasLocation) {
            provideContent {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(12.dp).clickable(actionStartActivity<MainActivity>())
                ) {
                    Text("صلاتي", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    Spacer(GlanceModifier.height(4.dp))
                    Text("لم يُحدد الموقع بعد، افتح التطبيق لإعداده")
                }
            }
            return
        }

        val zoneId = runCatching { ZoneId.of(settings.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zoneId)
        val times = timesResolver.resolveForDate(settings, now.toLocalDate(), zoneId)
        val ordered = listOf(
            Prayer.FAJR to times.fajr,
            Prayer.DHUHR to times.dhuhr,
            Prayer.ASR to times.asr,
            Prayer.MAGHRIB to times.maghrib,
            Prayer.ISHA to times.isha
        )
        val next = ordered.firstOrNull { it.second.isAfter(now) }
            ?: (Prayer.FAJR to timesResolver.resolveForDate(settings, now.toLocalDate().plusDays(1), zoneId).fajr)

        val nextName = next.first.arabicName
        val nextTime = next.second.format(formatter)
        val rows = ordered.map { it.first.arabicName to it.second.format(formatter) }

        provideContent {
            WidgetContent(nextName = nextName, nextTime = nextTime, rows = rows)
        }
    }
}

@Composable
private fun WidgetContent(nextName: String, nextTime: String, rows: List<Pair<String, String>>) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp).clickable(actionStartActivity<MainActivity>())
    ) {
        Text("الصلاة القادمة", style = TextStyle(fontSize = 11.sp))
        Row {
            Text(nextName, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp))
            Spacer(GlanceModifier.width(8.dp))
            Text(nextTime, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        rows.forEach { (name, time) ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(name, style = TextStyle(fontSize = 12.sp))
                Spacer(GlanceModifier.width(16.dp))
                Text(time, style = TextStyle(fontSize = 12.sp))
            }
        }
    }
}

class PrayerGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerGlanceWidget
}
