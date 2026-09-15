package com.coffer.app.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Product photos are copied into app-private storage, downsized, and referenced by absolute file path — never by a content:// Uri, which can outlive its permission grant. */
object ProductPhotoStore {
    private const val DIR_NAME = "product_photos"
    private const val MAX_DIMENSION = 800

    fun photosDir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Decodes, downsizes and re-encodes the image at [source]; returns the saved file's absolute path, or null on failure. */
    fun savePhoto(context: Context, source: Uri): String? {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null

        val scaled = scaleDown(bitmap)
        val file = File(photosDir(context), "${UUID.randomUUID()}.jpg")
        return runCatching {
            FileOutputStream(file).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            file.absolutePath
        }.getOrNull()
    }

    fun deletePhoto(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / maxSide
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
