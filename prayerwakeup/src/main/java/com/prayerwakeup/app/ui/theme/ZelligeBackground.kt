package com.prayerwakeup.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Scales each channel toward black, keeping alpha, to build a themed gradient from one color. */
fun Color.darken(factor: Float): Color = copy(red = red * factor, green = green * factor, blue = blue * factor)

/**
 * A background inspired by Zellige Fassi — the interlocking eight-point star tessellation
 * (two squares overlaid 45° apart) that's the foundational motif of Fez's traditional geometric
 * tilework. Drawn as a tiled line-art grid at low opacity over a themed vertical gradient, rather
 * than a bundled photo/image, so it scales to any screen and re-tints correctly for all 3 app
 * themes without needing per-theme image assets.
 */
@Composable
fun ZelligeBackground(modifier: Modifier = Modifier, baseColor: Color, patternColor: Color) {
    val gradient = Brush.verticalGradient(listOf(baseColor, baseColor.darken(0.55f)))
    Canvas(modifier = modifier.fillMaxSize().background(gradient)) {
        val cell = 56.dp.toPx()
        val starColor = patternColor.copy(alpha = 0.10f)
        val strokeWidth = 1.2.dp.toPx()
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        for (row in -1..rows) {
            for (col in -1..cols) {
                val center = Offset(col * cell + cell / 2f, row * cell + cell / 2f)
                drawEightPointStar(center, cell * 0.46f, starColor, strokeWidth)
            }
        }
    }
}

private fun DrawScope.drawEightPointStar(center: Offset, radius: Float, color: Color, strokeWidth: Float) {
    drawPath(squarePath(center, radius, 0f), color = color, style = Stroke(width = strokeWidth))
    drawPath(squarePath(center, radius, 45f), color = color, style = Stroke(width = strokeWidth))
}

private fun squarePath(center: Offset, radius: Float, rotationDeg: Float): Path {
    val path = Path()
    for (i in 0..3) {
        val angleRad = Math.toRadians(rotationDeg + 45.0 + 90.0 * i)
        val x = center.x + radius * cos(angleRad).toFloat()
        val y = center.y + radius * sin(angleRad).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
