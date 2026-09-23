package com.prayerwakeup.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.prayerwakeup.app.domain.AppTheme

/**
 * The raw primary/secondary colors behind each named theme. Kept separate from the
 * MaterialTheme colorScheme below because a few screens outside the normal Compose theming
 * flow — the full-screen call UI (IncomingCallActivity) and the home-screen Glance widget —
 * paint their own background directly rather than reading MaterialTheme.colorScheme, so they
 * need these values directly to actually change with the selected theme.
 */
data class ThemePalette(val primary: Color, val secondary: Color)

fun paletteFor(theme: AppTheme): ThemePalette = when (theme) {
    AppTheme.CLASSIC_GREEN -> ThemePalette(primary = Color(0xFF0B3D2E), secondary = Color(0xFFF2C94C))
    AppTheme.MIDNIGHT_BLUE -> ThemePalette(primary = Color(0xFF0D2B4E), secondary = Color(0xFFF2A65A))
    AppTheme.WARM_MAROON -> ThemePalette(primary = Color(0xFF5C1A2E), secondary = Color(0xFFD4AF37))
}
