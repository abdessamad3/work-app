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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Scales each channel toward black, keeping alpha, to build a themed gradient from one color. */
fun Color.darken(factor: Float): Color = copy(red = red * factor, green = green * factor, blue = blue * factor)

/**
 * A background inspired by Zellige Fassi — the interlocking eight-point star tessellation
 * (two squares overlaid 45° apart) that's the foundational motif of Fez's traditional geometric
 * tilework. Each star is filled with a flat tint and outlined, like a real mosaic tile rather than
 * bare line art, tiled over a themed vertical gradient. Pure vector drawing (no bundled photo), so
 * it scales to any screen and re-tints correctly for all 3 app themes.
 */
@Composable
fun ZelligeBackground(modifier: Modifier = Modifier, baseColor: Color, patternColor: Color) {
    val gradient = Brush.verticalGradient(listOf(baseColor, baseColor.darken(0.55f)))
    Canvas(modifier = modifier.fillMaxSize().background(gradient)) {
        val cell = 56.dp.toPx()
        val fillColor = patternColor.copy(alpha = 0.13f)
        val strokeColor = patternColor.copy(alpha = 0.32f)
        val strokeWidth = 1.dp.toPx()
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        for (row in -1..rows) {
            for (col in -1..cols) {
                val center = Offset(col * cell + cell / 2f, row * cell + cell / 2f)
                drawEightPointStar(center, cell * 0.46f, fillColor, strokeColor, strokeWidth)
            }
        }
    }
}

private fun DrawScope.drawEightPointStar(
    center: Offset,
    radius: Float,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float
) {
    val squareA = squarePath(center, radius, 0f)
    val squareB = squarePath(center, radius, 45f)
    drawPath(squareA, color = fillColor, style = Fill)
    drawPath(squareB, color = fillColor, style = Fill)
    drawPath(squareA, color = strokeColor, style = Stroke(width = strokeWidth))
    drawPath(squareB, color = strokeColor, style = Stroke(width = strokeWidth))
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
