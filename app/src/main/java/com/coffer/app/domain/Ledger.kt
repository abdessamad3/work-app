package com.coffer.app.domain

import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity

data class OrderComputed(
    val order: OrderEntity,
    val paidCents: Long,
    val remainingCents: Long,
    val status: OrderStatus
)

/** Paid, remaining and status are computed here on demand — never persisted alongside the order. */
fun computeOrder(order: OrderEntity, payments: List<PaymentEntity>): OrderComputed {
    val paid = payments.filter { it.orderId == order.id }.sumOf { it.amountCents }
    return OrderComputed(order, paid, remainingCents(order.totalAmountCents, paid), statusFor(order.totalAmountCents, paid))
}

fun totalRemainingForContact(contactId: Int, orders: List<OrderEntity>, payments: List<PaymentEntity>): Long =
    orders.filter { it.contactId == contactId }.sumOf { computeOrder(it, payments).remainingCents }

/** Cash only moves when a payment happens: in from a client, out to a supplier. */
fun cashBalanceCents(openingBalanceCents: Long, orders: List<OrderEntity>, payments: List<PaymentEntity>, contacts: List<ContactEntity>): Long {
    val orderById = orders.associateBy { it.id }
    val contactById = contacts.associateBy { it.id }
    val delta = payments.sumOf { payment ->
        val contact = orderById[payment.orderId]?.let { contactById[it.contactId] } ?: return@sumOf 0L
        if (contact.type == ContactType.CLIENT.name) payment.amountCents else -payment.amountCents
    }
    return openingBalanceCents + delta
}

fun totalOwedByType(type: String, orders: List<OrderEntity>, payments: List<PaymentEntity>, contacts: List<ContactEntity>): Long {
    val contactById = contacts.associateBy { it.id }
    return orders
        .filter { contactById[it.contactId]?.type == type }
        .sumOf { computeOrder(it, payments).remainingCents }
}
