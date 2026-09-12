package com.coffer.app.domain

enum class OrderStatus {
    UNPAID,
    PARTIAL,
    PAID
}

/** Paid/remaining/status are never stored — always recomputed from an order's payments. */
fun statusFor(totalAmountCents: Long, paidCents: Long): OrderStatus = when {
    paidCents <= 0 -> OrderStatus.UNPAID
    paidCents < totalAmountCents -> OrderStatus.PARTIAL
    else -> OrderStatus.PAID
}

fun remainingCents(totalAmountCents: Long, paidCents: Long): Long =
    (totalAmountCents - paidCents).coerceAtLeast(0)
