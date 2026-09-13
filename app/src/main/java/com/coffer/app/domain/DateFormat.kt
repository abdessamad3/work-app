package com.coffer.app.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)

fun formatDate(epochMillis: Long): String = dateFormatter.format(Date(epochMillis))
