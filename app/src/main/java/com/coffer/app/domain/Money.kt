package com.coffer.app.domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale
import kotlin.math.abs

val LocalCurrency = compositionLocalOf { Currency.DEFAULT }

@Composable
fun formatCents(cents: Long): String {
    val currency = LocalCurrency.current
    return String.format(Locale.US, "%,.2f %s", abs(cents) / 100.0, currency.code)
}
