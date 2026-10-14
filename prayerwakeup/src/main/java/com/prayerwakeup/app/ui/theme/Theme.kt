package com.prayerwakeup.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.prayerwakeup.app.domain.AppTheme

// Default Material3 onBackground is near-black, meant for a light colorScheme.background.
// Screens now paint the Zellige pattern directly behind a transparent Scaffold instead of a flat
// colorScheme.background, so background/onBackground are pointed at that same dark base color
// and a light, near-white ink — otherwise any Text() not sitting on its own opaque card (titles,
// section labels) inherits the old dark-on-light default and goes unreadable on the new backdrop.
private val OnDarkBackground = Color(0xFFF4F1E6)

@Composable
fun PrayerWakeupTheme(appTheme: AppTheme = AppTheme.CLASSIC_GREEN, darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = paletteFor(appTheme)
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = palette.secondary,
            secondary = palette.primary,
            background = palette.primary,
            onBackground = OnDarkBackground
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            background = palette.primary,
            onBackground = OnDarkBackground
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
