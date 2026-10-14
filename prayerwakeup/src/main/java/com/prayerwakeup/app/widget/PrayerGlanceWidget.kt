package com.prayerwakeup.app.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import androidx.glance.unit.ColorProvider
import com.prayerwakeup.app.MainActivity
import com.prayerwakeup.app.R
import com.prayerwakeup.app.data.PrayerTimesResolver
import com.prayerwakeup.app.data.settings.SettingsRepository
import com.prayerwakeup.app.domain.HijriDate
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.ui.theme.ThemePalette
import com.prayerwakeup.app.ui.theme.paletteFor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
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

// OnCard/OnCardMuted/OnAccent stay fixed since every theme's primary is dark and every theme's
// secondary is a light gold/orange, so a light-on-dark / dark-on-accent pairing always contrasts.
private val OnCard = Color(0xFFF4F4F0)
private val OnCardMuted = Color(0xFFCBD6CF)
private val OnAccent = Color(0xFF241A08)

object PrayerGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, PrayerWidgetEntryPoint::class.java)
        val settingsRepository = entryPoint.settingsRepository()
        val timesResolver = entryPoint.timesResolver()
        val settings = settingsRepository.settingsFlow.first()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
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
        val today = now.toLocalDate()
        val times = timesResolver.resolveForDate(settings, today, zoneId)
        val ordered = listOf(
            Prayer.FAJR to times.fajr,
            Prayer.DHUHR to times.dhuhr,
            Prayer.ASR to times.asr,
            Prayer.MAGHRIB to times.maghrib,
            Prayer.ISHA to times.isha
        )

        // The next prayer is the first of today's remaining ones; once they've all passed, it
        // rolls over to tomorrow's Fajr. The progress bar tracks how far "now" is between the
        // prayer just before it and the next one, so it needs that previous prayer's time too.
        val nextIndex = ordered.indexOfFirst { it.second.isAfter(now) }
        val next: Pair<Prayer, ZonedDateTime>
        val previousTime: ZonedDateTime
        if (nextIndex >= 0) {
            next = ordered[nextIndex]
            previousTime = if (nextIndex > 0) {
                ordered[nextIndex - 1].second
            } else {
                timesResolver.resolveForDate(settings, today.minusDays(1), zoneId).isha
            }
        } else {
            next = Prayer.FAJR to timesResolver.resolveForDate(settings, today.plusDays(1), zoneId).fajr
            previousTime = times.isha
        }

        val totalSeconds = Duration.between(previousTime, next.second).seconds.coerceAtLeast(1)
        val elapsedSeconds = Duration.between(previousTime, now).seconds.coerceIn(0, totalSeconds)
        val progress = elapsedSeconds.toFloat() / totalSeconds.toFloat()

        val hijriLabel = HijriDate.forDate(today, zoneId).displayLabel
        val locationLabel = settings.locationLabel.ifBlank { settings.moroccoCityLabel }.ifBlank { "صلاتي" }
        val remainingMillis = Duration.between(now, next.second).toMillis().coerceAtLeast(0)
        val rows = ordered.map { (prayer, time) ->
            Triple(prayer.arabicName, time.format(formatter), nextIndex >= 0 && prayer == next.first)
        }

        provideContent {
            WidgetContent(
                palette = palette,
                hijriLabel = hijriLabel,
                locationLabel = locationLabel,
                nextName = next.first.arabicName,
                remainingMillis = remainingMillis,
                progress = progress,
                rows = rows
            )
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
    palette: ThemePalette,
    hijriLabel: String,
    locationLabel: String,
    nextName: String,
    remainingMillis: Long,
    progress: Float,
    rows: List<Triple<String, String, Boolean>>
) {
    CardContainer(backgroundColor = palette.primary) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(hijriLabel, style = TextStyle(fontSize = 12.sp, color = ColorProvider(OnCardMuted)))
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier.width(16.dp).height(16.dp).background(palette.secondary).cornerRadius(8.dp)
            ) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                locationLabel,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ColorProvider(OnCard))
            )
        }

        Spacer(GlanceModifier.height(16.dp))

        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            CountdownChronometer(remainingMillis = remainingMillis)
            Spacer(GlanceModifier.defaultWeight())
            Text(nextName, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ColorProvider(OnCard)))
        }

        Spacer(GlanceModifier.height(10.dp))

        LinearProgressIndicator(
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
            progress = progress,
            color = ColorProvider(palette.secondary),
            backgroundColor = ColorProvider(OnCard.copy(alpha = 0.2f))
        )

        Spacer(GlanceModifier.height(14.dp))
        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(OnCard.copy(alpha = 0.15f))) {}
        Spacer(GlanceModifier.height(12.dp))

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            rows.forEach { (name, time, isNext) ->
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(name, style = TextStyle(fontSize = 11.sp, color = ColorProvider(OnCardMuted)))
                    Spacer(GlanceModifier.height(4.dp))
                    if (isNext) {
                        Box(
                            modifier = GlanceModifier
                                .background(palette.secondary)
                                .cornerRadius(8.dp)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(time, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(OnAccent)))
                        }
                    } else {
                        Text(time, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(OnCard)))
                    }
                }
            }
        }
    }
}

// Glance's own Text can't tick on its own — the widget provider only redraws every 30 minutes
// (Android's minimum updatePeriodMillis). A RemoteViews Chronometer, on the other hand, updates
// itself once it's attached to the host's window, independent of our process, so it's the only
// way to get a genuinely live "H:MM:SS" countdown on a home-screen widget.
@Composable
private fun CountdownChronometer(remainingMillis: Long) {
    val context = LocalContext.current
    val remoteViews = RemoteViews(context.packageName, R.layout.widget_chronometer)
    val base = SystemClock.elapsedRealtime() + remainingMillis
    remoteViews.setChronometerCountDown(R.id.prayer_countdown_chronometer, true)
    remoteViews.setChronometer(R.id.prayer_countdown_chronometer, base, "بعد %s", true)
    AndroidRemoteViews(remoteViews = remoteViews)
}

class PrayerGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerGlanceWidget
}
