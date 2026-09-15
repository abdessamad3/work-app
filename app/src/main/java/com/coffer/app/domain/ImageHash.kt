package com.coffer.app.domain

import android.graphics.Bitmap

/**
 * A 64-bit difference hash (dHash). Robust to resizing, mild lighting/angle changes and JPEG
 * recompression — good enough to rank "which saved product photo looks most like this one,"
 * not to prove two photos show the same physical item. Always pair with a visible ranked list,
 * never a hard auto-pick.
 */
fun computeImageHash(bitmap: Bitmap): Long {
    val resized = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
    var hash = 0L
    var bit = 0
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            val left = grayscale(resized.getPixel(x, y))
            val right = grayscale(resized.getPixel(x + 1, y))
            if (left < right) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

private fun grayscale(pixel: Int): Int {
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    return (r + g + b) / 3
}
