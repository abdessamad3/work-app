package com.prayerwakeup.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.prayerwakeup.app.domain.AppTheme

@Composable
fun PrayerWakeupTheme(appTheme: AppTheme = AppTheme.CLASSIC_GREEN, darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = paletteFor(appTheme)
    val colors = if (darkTheme) {
        darkColorScheme(primary = palette.secondary, secondary = palette.primary)
    } else {
        lightColorScheme(primary = palette.primary, secondary = palette.secondary)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
