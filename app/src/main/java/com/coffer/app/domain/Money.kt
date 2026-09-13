package com.coffer.app.domain

import java.util.Locale
import kotlin.math.abs

fun formatCents(cents: Long): String = String.format(Locale.US, "%,.2f MAD", abs(cents) / 100.0)
