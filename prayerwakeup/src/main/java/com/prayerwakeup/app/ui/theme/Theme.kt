package com.prayerwakeup.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF0B3D2E)
private val Secondary = Color(0xFFF2C94C)

private val LightColors = lightColorScheme(primary = Primary, secondary = Secondary)
private val DarkColors = darkColorScheme(primary = Secondary, secondary = Primary)

@Composable
fun PrayerWakeupTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
