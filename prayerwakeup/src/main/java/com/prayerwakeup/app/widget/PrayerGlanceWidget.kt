package com.prayerwakeup.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.prayerwakeup.app.MainActivity
import com.prayerwakeup.app.data.PrayerTimesResolver
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.ui.theme.paletteFor
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

// OnCard/OnCardMuted stay fixed since every theme's primary color is dark enough for light text.
private val OnCard = Color(0xFFF4F4F0)
private val OnCardMuted = Color(0xFFCBD6CF)

object PrayerGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, PrayerWidgetEntryPoint::class.java)
        val settingsRepository = entryPoint.settingsRepository()
        val timesResolver = entryPoint.timesResolver()
        val settings = settingsRepository.settingsFlow.first()
        val formatter = DateTimeFormatter.ofPattern("hh:mm a")
        val palette = paletteFor(settings.appTheme)

        if (!settings.hasLocation) {
            provideContent {
                CardContainer(backgroundColor = palette.primary) {
                    Text("صلاتي", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorProvider(OnCard)))
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        "لم يُحدد الموقع بعد، افتح التطبيق لإعداده",
                        style = TextStyle(fontSize = 12.sp, color = ColorProvider(OnCardMuted))
                    )
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
            WidgetContent(nextName = nextName, nextTime = nextTime, rows = rows, palette = palette)
        }
    }
}

@Composable
private fun CardContainer(backgroundColor: Color, content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        content()
    }
}

@Composable
private fun WidgetContent(
    nextName: String,
    nextTime: String,
    rows: List<Pair<String, String>>,
    palette: com.prayerwakeup.app.ui.theme.ThemePalette
) {
    CardContainer(backgroundColor = palette.primary) {
        Text("الصلاة القادمة", style = TextStyle(fontSize = 11.sp, color = ColorProvider(OnCardMuted)))
        Spacer(GlanceModifier.height(2.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                nextName,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ColorProvider(OnCard)),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                nextTime,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ColorProvider(palette.secondary))
            )
        }
        Spacer(GlanceModifier.height(12.dp))
        rows.forEach { (name, time) ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    name,
                    style = TextStyle(fontSize = 13.sp, color = ColorProvider(OnCardMuted)),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(time, style = TextStyle(fontSize = 13.sp, color = ColorProvider(OnCard)))
            }
        }
    }
}

class PrayerGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerGlanceWidget
}
