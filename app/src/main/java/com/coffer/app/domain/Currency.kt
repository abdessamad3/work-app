package com.coffer.app.domain

enum class Currency(val code: String, val displayName: String) {
    MAD("MAD", "Moroccan Dirham"),
    USD("USD", "US Dollar"),
    EUR("EUR", "Euro"),
    GBP("GBP", "British Pound"),
    CAD("CAD", "Canadian Dollar"),
    XOF("XOF", "West African CFA Franc");

    companion object {
        val DEFAULT = MAD
        fun fromCode(code: String?): Currency = entries.find { it.code == code } ?: DEFAULT
    }
}
