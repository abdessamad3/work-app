package com.coffer.app.domain

import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerTest {

    private fun contact(id: Int, type: ContactType) = ContactEntity(id = id, name = "Contact $id", type = type.name)
    private fun order(id: Int, contactId: Int, totalCents: Long) =
        OrderEntity(id = id, contactId = contactId, totalAmountCents = totalCents, itemized = false, description = "Order $id")
    private fun payment(id: Int, orderId: Int, amountCents: Long) =
        PaymentEntity(id = id, orderId = orderId, amountCents = amountCents, note = null)

    @Test
    fun `computeOrder only sums payments for that order`() {
        val target = order(id = 1, contactId = 1, totalCents = 1000)
        val payments = listOf(
            payment(id = 1, orderId = 1, amountCents = 300),
            payment(id = 2, orderId = 1, amountCents = 100),
            payment(id = 3, orderId = 2, amountCents = 9999) // different order — must be ignored
        )

        val result = computeOrder(target, payments)

        assertEquals(400L, result.paidCents)
        assertEquals(600L, result.remainingCents)
        assertEquals(OrderStatus.PARTIAL, result.status)
    }

    @Test
    fun `totalRemainingForContact sums remaining across only that contact's orders`() {
        val orders = listOf(
            order(id = 1, contactId = 1, totalCents = 1000),
            order(id = 2, contactId = 1, totalCents = 500),
            order(id = 3, contactId = 2, totalCents = 2000) // different contact — must be ignored
        )
        val payments = listOf(
            payment(id = 1, orderId = 1, amountCents = 400),
            payment(id = 2, orderId = 2, amountCents = 500) // order 2 fully paid
        )

        assertEquals(600L, totalRemainingForContact(contactId = 1, orders = orders, payments = payments))
    }

    @Test
    fun `cashBalanceCents adds client payments and subtracts supplier payments`() {
        val contacts = listOf(contact(1, ContactType.CLIENT), contact(2, ContactType.SUPPLIER))
        val orders = listOf(order(id = 1, contactId = 1, totalCents = 1000), order(id = 2, contactId = 2, totalCents = 1000))
        val payments = listOf(
            payment(id = 1, orderId = 1, amountCents = 300), // from a client: cash in
            payment(id = 2, orderId = 2, amountCents = 200), // to a supplier: cash out
            payment(id = 3, orderId = 999, amountCents = 5000) // no matching order — must be ignored
        )

        val balance = cashBalanceCents(openingBalanceCents = 1000, orders = orders, payments = payments, contacts = contacts)

        assertEquals(1100L, balance)
    }

    @Test
    fun `totalOwedByType only counts remaining balance for that contact type`() {
        val contacts = listOf(contact(1, ContactType.SUPPLIER), contact(2, ContactType.CLIENT))
        val orders = listOf(order(id = 1, contactId = 1, totalCents = 1000), order(id = 2, contactId = 2, totalCents = 500))
        val payments = listOf(payment(id = 1, orderId = 1, amountCents = 700))

        assertEquals(300L, totalOwedByType(ContactType.SUPPLIER.name, orders, payments, contacts))
    }

    @Test
    fun `totalOrderValueByType ignores payments entirely`() {
        val contacts = listOf(contact(1, ContactType.SUPPLIER))
        val orders = listOf(order(id = 1, contactId = 1, totalCents = 700))

        // Fully paid or not, the order's face value is unchanged.
        assertEquals(700L, totalOrderValueByType(ContactType.SUPPLIER.name, orders, contacts))
    }

    @Test
    fun `profitCents is client revenue minus supplier spend`() {
        val contacts = listOf(contact(1, ContactType.CLIENT), contact(2, ContactType.SUPPLIER))
        val orders = listOf(order(id = 1, contactId = 1, totalCents = 1000), order(id = 2, contactId = 2, totalCents = 400))

        assertEquals(1000L, revenueCents(orders, contacts))
        assertEquals(600L, profitCents(orders, contacts))
    }
}
