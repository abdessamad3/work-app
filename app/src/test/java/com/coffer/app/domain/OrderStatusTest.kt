package com.coffer.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderStatusTest {

    @Test
    fun `statusFor is UNPAID when nothing paid`() {
        assertEquals(OrderStatus.UNPAID, statusFor(totalAmountCents = 1000, paidCents = 0))
    }

    @Test
    fun `statusFor is UNPAID for a negative paid amount`() {
        assertEquals(OrderStatus.UNPAID, statusFor(totalAmountCents = 1000, paidCents = -100))
    }

    @Test
    fun `statusFor is PARTIAL when some but not all is paid`() {
        assertEquals(OrderStatus.PARTIAL, statusFor(totalAmountCents = 1000, paidCents = 400))
    }

    @Test
    fun `statusFor is PAID when fully paid`() {
        assertEquals(OrderStatus.PAID, statusFor(totalAmountCents = 1000, paidCents = 1000))
    }

    @Test
    fun `statusFor is PAID on overpayment`() {
        assertEquals(OrderStatus.PAID, statusFor(totalAmountCents = 1000, paidCents = 1500))
    }

    @Test
    fun `remainingCents is total minus paid`() {
        assertEquals(700L, remainingCents(totalAmountCents = 1000, paidCents = 300))
    }

    @Test
    fun `remainingCents never goes negative on overpayment`() {
        assertEquals(0L, remainingCents(totalAmountCents = 1000, paidCents = 1500))
    }
}
