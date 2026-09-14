package com.coffer.app.domain

/** Strips whitespace and folds case so a barcode typed by hand and the same code read by the camera always compare equal. */
fun normalizeBarcode(value: String?): String? {
    if (value == null) return null
    val cleaned = value.trim().replace(Regex("\\s+"), "").uppercase()
    return cleaned.ifBlank { null }
}
